package com.nishiyu.lunex.program.server;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.program.core.BaseLuaVM;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.server.ServerProgramData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class ServerLuaVM extends BaseLuaVM {

    protected static final Set<String> BASE_SYSTEM_GLOBALS = new HashSet<>(Set.of(
            "_G", "_VERSION", "assert", "error", "getmetatable", "next", "pcall", "print", "rawequal", "rawget", "rawlen", "rawset",
            "select", "setmetatable", "tonumber", "tostring", "type", "xpcall", "coroutine", "math", "string", "table", "io", "os",
            "package", "collectgarbage", "dofile", "load", "loadfile", "require",
            "system", "machine", "screen", "turtle", "mcLAN", "craft", "tool", "http", "fs", "device", "rs", "commands", "mcNet",
            "net", "lan", "router", "machine_control", "mob", "goal",
            "storage", "pubsub", "inventory", "printer", "speaker",
            "on_init", "on_tick", "on_click", "on_redstone", "on_item_in",
            "on_right_click", "on_left_click_block", "on_attack_entity",
            "on_block_break", "on_projectile_hit_entity", "on_projectile_hit_block", "on_projectile_shoot"
    ));

    private static final ExecutorService SCRIPT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    public final Map<Class<?>, Object> apiCache = new ConcurrentHashMap<>();

    protected final Set<String> dynamicSystemGlobals = ConcurrentHashMap.newKeySet();

    public CompoundTag memoryBuffer;
    public Player currentPlayer;
    public ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
    public boolean isRelocating = false;
    public ServerLuaVM rootVm = null;
    public volatile boolean isWipingMemory = false;
    public volatile Thread mainServerThread = null;
    protected LinkedBlockingQueue<LuaEvent> pendingEvents = new LinkedBlockingQueue<>();
    protected Future<?> taskFuture;
    public String workspaceId = "default"; // 実行環境ごとに異なるIDを想定

    public ServerLuaVM() {
        super();
    }

    protected Set<String> getSystemGlobals() {
        return BASE_SYSTEM_GLOBALS;
    }

    public boolean isSystemGlobal(String name) {
        return getSystemGlobals().contains(name) || dynamicSystemGlobals.contains(name);
    }

    public void takeOverFrom(ServerLuaVM oldVm) {
        this.rootVm = (oldVm.rootVm != null) ? oldVm.rootVm : oldVm;
        this.mainThreadTasks = oldVm.mainThreadTasks;
        this.pendingEvents = oldVm.pendingEvents;
        this.isRunning = oldVm.isRunning;
        this.taskFuture = oldVm.taskFuture;
        this.currentExecutingThread = oldVm.currentExecutingThread;
        this.globals = oldVm.globals;
        this.apiCache.putAll(oldVm.apiCache);
        this.dynamicSystemGlobals.addAll(oldVm.dynamicSystemGlobals);
        this.workspaceId = oldVm.workspaceId;
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrCreateAPI(Class<T> clazz, java.util.function.Function<ServerLuaVM, T> factory) {
        T instance = (T) apiCache.get(clazz);
        if (instance == null) {
            synchronized (apiCache) {
                instance = (T) apiCache.get(clazz);
                if (instance == null) {
                    instance = factory.apply(this);
                    apiCache.put(clazz, instance);
                }
            }
        }
        return instance;
    }

    protected void registerAPI(String namespace, Object apiInstance) {
        String rootName = namespace.split("\\.")[0];
        dynamicSystemGlobals.add(rootName);

        LuaValue coercedInstance = CoerceJavaToLua.coerce(apiInstance);
        LuaTable apiTable = new LuaTable();

        for (java.lang.reflect.Method m : apiInstance.getClass().getDeclaredMethods()) {
            if (m.isAnnotationPresent(LuaFunction.class)) {
                String methodName = m.getName();
                LuaValue methodVal = coercedInstance.get(methodName);

                apiTable.set(methodName, new org.luaj.vm2.lib.VarArgFunction() {
                    @Override
                    public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                        int n = args.narg();
                        LuaValue[] combined = new LuaValue[n + 1];
                        combined[0] = coercedInstance;
                        for (int i = 0; i < n; i++) {
                            combined[i + 1] = args.arg(i + 1);
                        }
                        return methodVal.invoke(org.luaj.vm2.LuaValue.varargsOf(combined));
                    }
                });
            }
        }

        String[] parts = namespace.split("\\.");
        LuaValue current = globals;

        for (int i = 0; i < parts.length - 1; i++) {
            LuaValue next = current.get(parts[i]);
            if (next.isnil()) {
                next = new LuaTable();
                current.set(parts[i], next);
            }
            current = next;
        }

        LuaValue existing = current.get(parts[parts.length - 1]);
        if (existing.istable()) {
            LuaTable exTable = (LuaTable) existing;
            for (LuaValue key : apiTable.keys()) {
                exTable.set(key, apiTable.get(key));
            }
        } else {
            current.set(parts[parts.length - 1], apiTable);
        }
    }

    protected void initSandboxAndAPIs() {
        initBaseSandbox();

        final int currentExecId = this.executionId.get();

        globals.set("print", new org.luaj.vm2.lib.VarArgFunction() {
            @Override
            public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= args.narg(); i++) {
                    sb.append(args.arg(i).tojstring());
                    if (i < args.narg()) sb.append("  ");
                }
                String messageStr = sb.toString();
                Lunex.LOGGER.info("[Lua Print] " + messageStr);

                if (messageStr.toLowerCase().contains("error:")) {
                    String cleanError = parseLuaErrorString(messageStr);

                    if (currentPlayer instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                serverPlayer,
                                new com.nishiyu.lunex.network.packet.s2c.ErrorToastS2CPacket(cleanError)
                        );
                    }
                    return LuaValue.NIL;
                }

                return LuaValue.NIL;
            }
        });

        globals.set("sys_pollEvent", new org.luaj.vm2.lib.OneArgFunction() {
            @Override
            public LuaValue call(LuaValue timeoutVal) {
                if (!isValidRun(currentExecId)) throw new org.luaj.vm2.LuaError("VM Stopped");
                try {
                    LuaEvent ev;
                    long timeout = timeoutVal.isnil() ? 0 : timeoutVal.tolong();

                    if (timeout < 0) {
                        ev = pendingEvents.take();
                    } else if (timeout == 0) {
                        ev = pendingEvents.poll();
                    } else {
                        ev = pendingEvents.poll(timeout, TimeUnit.MILLISECONDS);
                    }

                    if (ev == null) {
                        if (!isValidRun(currentExecId)) throw new org.luaj.vm2.LuaError("VM Stopped");
                        return LuaValue.NIL;
                    }

                    LuaTable t = new LuaTable();
                    t.set("name", LuaValue.valueOf(ev.name()));
                    LuaTable args = new LuaTable();
                    if (ev.args() != null) {
                        for (int i = 0; i < ev.args().length; i++) {
                            args.set(i + 1, CoerceJavaToLua.coerce(ev.args()[i]));
                        }
                    }
                    t.set("args", args);
                    return t;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new org.luaj.vm2.LuaError("VM Stopped");
                }
            }
        });

        globals.set("sys_isRunning", new org.luaj.vm2.lib.ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(isValidRun(currentExecId));
            }
        });
    }

    @Override
    public void startCode(String rawCode, String processName) {
        stopProgram();

        isRunning = true;
        mainThreadTasks.clear();
        pendingEvents.clear();

        final int currentExecId = this.executionId.incrementAndGet();

        taskFuture = SCRIPT_EXECUTOR.submit(() -> {
            Thread thisThread = Thread.currentThread();
            this.currentExecutingThread = thisThread;
            thisThread.setName("Lunex-Lua-VM-" + workspaceId + "-" + processName);

            try {
                initSandboxAndAPIs();

                try (java.io.InputStream is = Lunex.class.getResourceAsStream("/assets/lunex/lua/scheduler.lua")) {
                    if (is != null) {
                        String schedulerScript = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        globals.load(schedulerScript, "scheduler.lua").call();
                    } else {
                        Lunex.LOGGER.error("[Lunex] scheduler.lua が見つかりません。");
                    }
                } catch (Exception e) {
                    Lunex.LOGGER.error("[Lunex] scheduler.lua の読み込みに失敗しました: ", e);
                }

                if (isWipingMemory) {
                    memoryBuffer = null;
                    isWipingMemory = false;
                }

                if (memoryBuffer != null) {
                    loadMemoryFromTag(memoryBuffer);
                }

                String loadName = (processName != null && !processName.isEmpty()) ? processName : "user_program.lua";
                LuaValue chunk = globals.load(rawCode, loadName);

                LuaValue runScheduler = globals.get("sys_runScheduler");
                if (!runScheduler.isnil()) {
                    runScheduler.call(chunk);
                } else {
                    Lunex.LOGGER.error("[Lunex] sys_runScheduler 関数が見つかりません。");
                }

            } catch (Exception e) {
                boolean isInterrupted = !isValidRun(currentExecId);
                Throwable cause = e;
                while (cause != null) {
                    if (cause instanceof InterruptedException || (cause.getMessage() != null && cause.getMessage().contains("VM Stopped"))) {
                        isInterrupted = true;
                        break;
                    }
                    cause = cause.getCause();
                }

                if (!isInterrupted) {
                    String cleanError = formatLuaError(e);
                    Lunex.LOGGER.error("[Lunex] Lua実行エラー: \n" + cleanError);

                    if (currentPlayer instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                serverPlayer,
                                new com.nishiyu.lunex.network.packet.s2c.ErrorToastS2CPacket(cleanError)
                        );
                    }

                    String vmId = "vm_" + workspaceId;
                    com.nishiyu.lunex.network.LocalWebSocketServer.sendVmError(vmId, cleanError);
                }
            } finally {
                if (this.executionId.get() == currentExecId) {
                    saveMemoryToNBT();
                    isRunning = false;
                    this.currentExecutingThread = null;
                    cleanupAPIs();
                }
            }
        });
    }

    public void startProgram(String programName) {
        try {
            ServerProgramData.load(workspaceId);
            Map<String, String> progs = ServerProgramData.getPrograms(workspaceId);

            // ▼ 修正: 拡張子が省かれて渡された場合でも正しく読み込めるようにする
            String searchName = programName;
            if (!searchName.endsWith(".lua") && !progs.containsKey(searchName)) {
                searchName = searchName + ".lua";
            }

            String rawCode = progs.get(searchName);

            // デバッグログ (不要であれば削除してください)
            Lunex.LOGGER.info("[Debug] 実行ファイル名: " + searchName + " / コード長: " + (rawCode != null ? rawCode.length() : "null"));

            if (rawCode == null) rawCode = "";
            startCode(rawCode, programName);
        } catch (Exception e) {
            Lunex.LOGGER.error("[Lunex] プログラムの読み込みに失敗: ", e);
        }
    }

    @Override
    public void stopProgram() {
        if (isRelocating) return;

        this.executionId.incrementAndGet();

        isRunning = false;
        if (rootVm != null) rootVm.isRunning = false;

        cleanupAPIs();

        if (taskFuture != null && !taskFuture.isDone()) taskFuture.cancel(true);

        Thread execThread = this.currentExecutingThread;
        if (execThread != null && execThread.isAlive()) {
            execThread.interrupt();
        }

        mainThreadTasks.clear();
        if (rootVm != null) rootVm.mainThreadTasks.clear();

        pendingEvents.clear();
    }

    public void restartProgram(String programName) {
        isWipingMemory = true;
        stopProgram();

        memoryBuffer = null;

        cleanupAPIs();
        apiCache.clear();

        startProgram(programName);
    }

    protected void cleanupAPIs() {
        for (Object apiObj : apiCache.values()) {
            if (apiObj instanceof AutoCloseable closeableApi) {
                try {
                    closeableApi.close();
                } catch (Exception e) {
                    Lunex.LOGGER.error("[Lunex] APIクリーンアップ中にエラー: ", e);
                }
            }
        }
    }

    public void triggerEvent(String eventName, Object... args) {
        if (isRunning) pendingEvents.add(new LuaEvent(eventName, args));
    }

    public void forceTriggerEvent(String eventName, Object... args) {
        pendingEvents.add(new LuaEvent(eventName, args));
    }

    public void tick() {
        if (mainServerThread == null) mainServerThread = Thread.currentThread();
        int processed = 0;
        Runnable task;
        while ((task = mainThreadTasks.poll()) != null && processed < 50) {
            try {
                task.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
            processed++;
        }
    }

    public void applyDelay(int baseDelayMs, boolean isRender) {
        if (!isValidRun(this.executionId.get())) throw new org.luaj.vm2.LuaError("VM Stopped");

        long finalDelay = (long) baseDelayMs; // BlockEntity関連の速度計算は削除
        if (finalDelay > 0) {
            try {
                Thread.sleep(finalDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new org.luaj.vm2.LuaError("VM Stopped");
            }
        }
    }

    public <T> T executeInMainThreadSync(Callable<T> task) {
        return executeInMainThreadSync(task, 50, false);
    }

    public <T> T executeInMainThreadSync(Callable<T> task, int baseDelayMs, boolean isRender) {
        if (!isValidRun(this.executionId.get())) throw new org.luaj.vm2.LuaError("VM Stopped");
        if (Thread.currentThread() == mainServerThread) {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException("メインスレッドタスクの実行に失敗", e);
            }
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        mainThreadTasks.add(() -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            T result = future.get();
            applyDelay(baseDelayMs, isRender);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new org.luaj.vm2.LuaError("VM Stopped");
        } catch (Exception e) {
            throw new RuntimeException("メインスレッドタスクの実行に失敗", e);
        }
    }

    public void loadMemoryFromTag(CompoundTag memTag) {
        for (String key : memTag.getAllKeys()) {
            if (isSystemGlobal(key)) continue;

            LuaValue loadedVal = loadFromNBT(memTag.get(key));
            if (loadedVal != LuaValue.NIL) globals.set(key, loadedVal);
        }
    }

    public CompoundTag extractMemoryToTag() {
        CompoundTag memTag = new CompoundTag();
        if (globals != null) {
            LuaValue key = LuaValue.NIL;
            int count = 0;
            final int MAX_GLOBALS_SIZE = 2000;

            while (true) {
                Varargs nextNode;
                try {
                    nextNode = globals.next(key);
                } catch (Exception e) {
                    Lunex.LOGGER.warn("[Lunex] メモリセーブ中に同時書き込みを検知したため保存を中断しました。");
                    break;
                }

                key = nextNode.arg1();
                if (key.isnil()) {
                    break;
                }

                count++;
                if (count > MAX_GLOBALS_SIZE) {
                    memTag.putString("_warning", "[Globals Size Limit Reached / Infinite Loop Prevented]");
                    break;
                }

                if (key.isstring()) {
                    String k = key.checkjstring();
                    if (isSystemGlobal(k)) continue;

                    LuaValue val = nextNode.arg(2);
                    Tag tag = saveToNBT(val, 0);
                    if (tag != null) {
                        memTag.put(k, tag);
                    }
                }
            }
        }
        return memTag;
    }

    public void saveMemoryToNBT() {
        if (isWipingMemory) return;
        this.memoryBuffer = extractMemoryToTag();
    }

    protected record LuaEvent(String name, Object[] args) {
    }
}