package com.nishiyu.lunex.program.server.machine;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.core.LuaFunction;
import net.minecraft.server.MinecraftServer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.Varargs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class SimpleMachineServerLuaVM extends MachineServerLuaVM {

    public final SimpleMachineBlockEntity simpleMachine;
    public final List<String> terminalLog = new CopyOnWriteArrayList<>(List.of("Simple OS v1.0", "Type a command and press Enter."));
    public final List<String> suggestions = new ArrayList<>();

    // ★追加: エラーなく実行できたプログラムだけを蓄積するリスト (Export用)
    public final List<String> successfulPrograms = new CopyOnWriteArrayList<>();

    private final BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();
    private Thread interpreterThread;

    public SimpleMachineServerLuaVM(SimpleMachineBlockEntity simpleMachine) {
        super(simpleMachine);
        this.simpleMachine = simpleMachine;
        this.isRunning = true;
        this.ensureInitialized();
        this.startInterpreterThread();
    }

    private void ensureInitialized() {
        if (this.globals == null) {
            try {
                this.globals = org.luaj.vm2.lib.jse.JsePlatform.standardGlobals();
                super.initSandboxAndAPIs();

                if (this.globals != null) {
                    VarArgFunction customPrint = new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 1; i <= args.narg(); i++) {
                                if (i > 1) sb.append("\t");
                                sb.append(args.arg(i).tojstring());
                            }
                            addLog(sb.toString());
                            return LuaValue.NIL;
                        }
                    };

                    this.globals.set("print", customPrint);
                    LuaValue systemApi = this.globals.get("system");
                    if (systemApi.istable()) {
                        systemApi.set("print", customPrint);
                        systemApi.set("chat", customPrint);
                    }

                    LuaTable disabledFs = new LuaTable();
                    LuaTable mt = new LuaTable();
                    mt.set(LuaValue.INDEX, new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            throw new org.luaj.vm2.LuaError("このマシンでは fs API は許可されていません。");
                        }
                    });
                    disabledFs.setmetatable(mt);
                    this.globals.set("fs", disabledFs);

                    buildSuggestions();
                }
            } catch (Exception e) {
                Lunex.LOGGER.error("SimpleMachine VM Init Error: ", e);
            }
        }
    }

    private void buildSuggestions() {
        this.suggestions.clear();
        this.suggestions.addAll(List.of(
                "print(\"\")", "math.abs()", "math.ceil()", "math.floor()", "math.max()", "math.min()",
                "math.random()", "string.char()", "string.find()", "string.format()", "string.len()",
                "string.lower()", "string.upper()", "string.match()", "string.rep()", "string.sub()",
                "table.concat()", "table.insert()", "table.remove()", "table.sort()", "os.time()", "os.date()"
        ));

        for (Map.Entry<Class<?>, Object> entry : this.apiCache.entrySet()) {
            String namespace = getNamespaceFromClass(entry.getKey());
            if (namespace == null) continue;
            if (namespace.equals("fs")) {
                this.suggestions.add("fs (※許可されていません)");
                continue;
            }
            for (Method m : entry.getKey().getDeclaredMethods()) {
                if (m.isAnnotationPresent(LuaFunction.class)) {
                    LuaFunction lf = m.getAnnotation(LuaFunction.class);
                    StringBuilder sig = new StringBuilder(namespace + "." + m.getName() + "(");

                    String[] args = lf.args();
                    for (int i = 0; i < args.length; i++) {
                        String type = args[i].split(":")[0];
                        if (type.equals("str")) sig.append("\"\"");
                        else if (type.equals("num")) sig.append("0");
                        else if (type.equals("bool")) sig.append("false");
                        else sig.append("nil");
                        if (i < args.length - 1) sig.append(", ");
                    }
                    sig.append(")");
                    this.suggestions.add(sig.toString());
                }
            }
        }
    }

    private String getNamespaceFromClass(Class<?> clazz) {
        String name = clazz.getSimpleName().replace("API", "").toLowerCase();
        if (name.equals("redpower")) return "rs";
        if (name.equals("file")) return "fs";
        return name;
    }

    private void startInterpreterThread() {
        if (this.interpreterThread != null && this.interpreterThread.isAlive()) return;

        this.interpreterThread = Thread.ofVirtual().name("SimpleMachine-Interpreter-Thread").start(() -> {
            this.executionId.incrementAndGet();
            this.currentExecutingThread = Thread.currentThread();

            while (this.isRunning) {
                try {
                    String code = this.commandQueue.take();
                    String processedCode = code.trim();
                    if (processedCode.startsWith("local ")) {
                        processedCode = processedCode.substring(6).trim();
                    }
                    try {
                        this.globals.load(processedCode).call();
                        // ★追加: エラーなく正常に完了した場合のみリストに追加
                        this.successfulPrograms.add(code);
                    } catch (Throwable e) {
                        String msg = e.getMessage();
                        if (msg == null) msg = e.getClass().getSimpleName();
                        addLog("§cError: " + msg + "§r");
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
            this.currentExecutingThread = null;
        });
    }

    public void executeString(String code) {
        addLog("> " + code);
        this.ensureInitialized();
        this.commandQueue.add(code);
    }

    private void addLog(String message) {
        this.terminalLog.add(message);
        if (this.terminalLog.size() > 200) {
            while (this.terminalLog.size() > 200) {
                this.terminalLog.remove(0);
            }
        }
        syncClient();
    }

    @Override
    public <T> T executeInMainThreadSync(Callable<T> task, int baseDelayMs, boolean isRender) {
        if (this.simpleMachine != null && this.simpleMachine.getLevel() != null && !this.simpleMachine.getLevel().isClientSide) {
            MinecraftServer server = this.simpleMachine.getLevel().getServer();
            if (server != null) {
                CompletableFuture<T> future = new CompletableFuture<>();
                server.execute(() -> {
                    try {
                        future.complete(task.call());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
                try {
                    return future.join();
                } catch (Exception e) {
                    throw new org.luaj.vm2.LuaError("Main thread execution failed: " + e.getMessage());
                }
            }
        }
        return null;
    }

    @Override
    public void stopProgram() {
        this.isRunning = false;
        if (this.interpreterThread != null) {
            this.interpreterThread.interrupt();
        }
        super.stopProgram();
    }

    public void syncClient() {
        if (this.simpleMachine != null && this.simpleMachine.getLevel() != null && !this.simpleMachine.getLevel().isClientSide) {
            MinecraftServer server = this.simpleMachine.getLevel().getServer();
            if (server != null) {
                server.execute(() -> {
                    this.simpleMachine.setChanged();
                    this.simpleMachine.getLevel().sendBlockUpdated(
                            this.simpleMachine.getBlockPos(),
                            this.simpleMachine.getBlockState(),
                            this.simpleMachine.getBlockState(),
                            3
                    );
                });
            }
        }
    }
}