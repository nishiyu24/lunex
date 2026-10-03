package com.nishiyu.lunex.program.server.machine;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.mcnet.DeviceAPIRegistry;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.api.mainframe.IMainframeAPI;
import com.nishiyu.lunex.api.mainframe.LuaAPIRegistry;
import net.minecraft.server.MinecraftServer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.Varargs;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

public class CoreMachineServerLuaVM extends ServerLuaVM {

    private static final Set<String> MACHINE_SYSTEM_GLOBALS;

    static {
        MACHINE_SYSTEM_GLOBALS = new HashSet<>(ServerLuaVM.BASE_SYSTEM_GLOBALS);
        MACHINE_SYSTEM_GLOBALS.add("Direction");
        MACHINE_SYSTEM_GLOBALS.add("DEVICE");
    }

    public final SimpleMachineBlockEntity simpleMachine;
    public final List<String> terminalLog = new CopyOnWriteArrayList<>(List.of("Core OS v1.0", "Type a command and press Enter."));
    public final List<String> suggestions = new ArrayList<>();
    public final List<String> successfulPrograms = new CopyOnWriteArrayList<>();

    private final BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();
    private Thread interpreterThread;

    public boolean isMainframeMode = false;
    private final Set<String> activeApis = new HashSet<>();

    public CoreMachineServerLuaVM(SimpleMachineBlockEntity simpleMachine) {
        super();
        this.simpleMachine = simpleMachine;
        this.isRunning = true;
        this.ensureInitialized();
        this.startInteractiveThread();
    }

    @Override
    protected Set<String> getSystemGlobals() {
        return MACHINE_SYSTEM_GLOBALS;
    }

    @Override
    protected void initSandboxAndAPIs() {
        super.initSandboxAndAPIs();

        String enumDefinition = "Direction = { UP='up', DOWN='down', LEFT='left', RIGHT='right', FRONT='front', BACK='back', NORTH='north', SOUTH='south', EAST='east', WEST='west' }";
        globals.load(enumDefinition).call();

        LuaTable deviceTable = new LuaTable();
        for (String type : DeviceAPIRegistry.getRegisteredTypes()) {
            deviceTable.set(type.toUpperCase(java.util.Locale.ROOT), LuaValue.valueOf(type));
        }
        globals.set("DEVICE", deviceTable);

        // ★ SystemAPI を明示的に初期化して 'system' テーブルとして登録
        com.nishiyu.lunex.program.server.SystemAPI systemApiObj = this.getOrCreateAPI(
                com.nishiyu.lunex.program.server.SystemAPI.class,
                com.nishiyu.lunex.program.server.SystemAPI::new
        );
        this.registerAPI("system", systemApiObj);

        // 動的API (アドオン等) の登録処理
        for (IMainframeAPI apiDef : LuaAPIRegistry.getAll().values()) {
            String namespace = apiDef.getNamespace();
            String reqFeature = apiDef.getRequiredFeature();

            boolean isAvailable = reqFeature.isEmpty() ||
                    (!isMainframeMode && reqFeature.isEmpty()) ||
                    (isMainframeMode && activeApis.contains(reqFeature));

            if (isAvailable) {
                Object apiInstance = apiDef.createInstance(this);
                this.registerAPI(namespace, apiInstance);
            } else {
                // 利用不可なAPIが呼ばれた場合、エラーを投げずに「not found」とだけ出力する
                LuaTable disabled = new LuaTable();
                LuaTable mt = new LuaTable();
                mt.set(LuaValue.INDEX, new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        return new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs innerArgs) {
                                addLog("not found");
                                return LuaValue.NIL;
                            }
                        };
                    }
                });
                disabled.setmetatable(mt);
                this.globals.set(namespace, disabled);
            }
        }
    }

    private void ensureInitialized() {
        if (this.globals == null) {
            try {
                this.globals = org.luaj.vm2.lib.jse.JsePlatform.standardGlobals();

                // ★修正: super ではなく、このクラスでオーバーライドした this のメソッドを呼ぶ
                this.initSandboxAndAPIs();

                if (this.globals != null) {
                    setupCustomPrint();
                    buildSuggestions();
                }
            } catch (Exception e) {
                Lunex.LOGGER.error("CoreMachine VM Init Error: ", e);
            }
        }
    }

    private void setupCustomPrint() {
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
        }
    }

    public void upgradeToMainframe(Set<String> providedApis) {
        this.isMainframeMode = true;
        this.activeApis.clear();
        this.activeApis.addAll(providedApis);
        addLog("[System] Upgrading to Mainframe VM...");

        if (this.interpreterThread != null) this.interpreterThread.interrupt();
        this.globals = null;
        this.apiCache.clear();
        this.ensureInitialized();

        this.startMainframeThread();
    }

    public void downgradeToInteractive() {
        this.isMainframeMode = false;
        this.activeApis.clear();
        addLog("[System] Downgrading to Interactive VM...");

        if (this.interpreterThread != null) this.interpreterThread.interrupt();
        this.globals = null;
        this.apiCache.clear();
        this.ensureInitialized();

        this.startInteractiveThread();
    }

    private void startInteractiveThread() {
        if (this.interpreterThread != null && this.interpreterThread.isAlive()) return;
        this.interpreterThread = Thread.ofVirtual().name("CoreMachine-Interactive-Thread").start(() -> {
            this.executionId.incrementAndGet();
            this.currentExecutingThread = Thread.currentThread();
            while (this.isRunning && !this.isMainframeMode) {
                try {
                    String code = this.commandQueue.take();
                    String processedCode = code.trim();
                    if (processedCode.startsWith("local ")) processedCode = processedCode.substring(6).trim();
                    try {
                        this.globals.load(processedCode).call();
                        this.successfulPrograms.add(code);
                    } catch (Throwable e) {
                        String msg = e.getMessage();
                        // 存在しない関数(nil value)が呼ばれた場合は「not found」と出力する
                        if (msg != null && msg.contains("nil value")) {
                            addLog("not found");
                        } else {
                            addLog("§cError: " + (msg == null ? e.getClass().getSimpleName() : msg) + "§r");
                        }
                    }
                } catch (InterruptedException e) { break; }
            }
            this.currentExecutingThread = null;
        });
    }

    private void startMainframeThread() {
        if (this.interpreterThread != null && this.interpreterThread.isAlive()) return;
        this.interpreterThread = Thread.ofVirtual().name("CoreMachine-Mainframe-Thread").start(() -> {
            this.executionId.incrementAndGet();
            this.currentExecutingThread = Thread.currentThread();
            try {
                String startupCode = "print('Mainframe OS Initialized. Active APIs: " + String.join(", ", this.activeApis) + "')";
                this.globals.load(startupCode).call();
            } catch (Throwable e) {
                addLog("§cMainframe Error: " + e.getMessage() + "§r");
            }
            this.currentExecutingThread = null;
        });
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
            String namespace = null;
            if (entry.getValue() instanceof IMainframeAPI) {
                namespace = ((IMainframeAPI)entry.getValue()).getNamespace();
            } else {
                namespace = getNamespaceFromClass(entry.getKey());
            }

            if (namespace == null) continue;

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

    public void executeString(String code) {
        addLog("> " + code);
        this.ensureInitialized();
        if (!this.isMainframeMode) {
            this.commandQueue.add(code);
        } else {
            addLog("§cError: インタラクティブコマンドは現在無効です。§r");
        }
    }

    private void addLog(String message) {
        this.terminalLog.add(message);
        if (this.terminalLog.size() > 200) {
            while (this.terminalLog.size() > 200) this.terminalLog.remove(0);
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
                    try { future.complete(task.call()); }
                    catch (Exception e) { future.completeExceptionally(e); }
                });
                try { return future.join(); }
                catch (Exception e) { throw new org.luaj.vm2.LuaError("Main thread execution failed: " + e.getMessage()); }
            }
        }
        return null;
    }

    @Override
    public void stopProgram() {
        this.isRunning = false;
        if (this.interpreterThread != null) this.interpreterThread.interrupt();
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
                            this.simpleMachine.getBlockState(), 3);
                });
            }
        }
    }
}