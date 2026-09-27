package com.nishiyu.lunex.program.server.entity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.entity.CustomBioMobEntity;
import com.nishiyu.lunex.program.core.BaseLuaVM;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.entity.api.CustomGoalAPI;
import com.nishiyu.lunex.program.server.entity.api.EntitySystemAPI;
import com.nishiyu.lunex.program.server.entity.api.MobEntityAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class EntityServerLuaVM extends BaseLuaVM {

    private static final ExecutorService ENTITY_VM_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static final Set<String> SYSTEM_GLOBALS = Set.of(
            "_G", "_VERSION", "assert", "error", "getmetatable", "next", "pcall", "print", "rawequal", "rawget", "rawlen", "rawset",
            "select", "setmetatable", "tonumber", "tostring", "type", "xpcall", "coroutine", "math", "string", "table", "io", "os",
            "package", "collectgarbage", "dofile", "load", "loadfile", "require",
            "mob", "goal",
            "on_init", "on_tick", "on_hurt", "on_reach_target", "on_click",
            "GoalAction", "GoalCondition"
    );

    private final CustomBioMobEntity mob;
    private final Map<Class<?>, Object> apiCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();
    private final LinkedBlockingQueue<LuaEvent> pendingEvents = new LinkedBlockingQueue<>();
    public CompoundTag memoryBuffer;
    private Future<?> taskFuture;

    public Player currentPlayer;

    public EntityServerLuaVM(CustomBioMobEntity mob) {
        super();
        this.mob = mob;
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrCreateAPI(Class<T> clazz, java.util.function.Function<EntityServerLuaVM, T> factory) {
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

        registerAPI("system", getOrCreateAPI(EntitySystemAPI.class, vm -> new EntitySystemAPI(vm, this.mob)));
        registerAPI("mob", new MobEntityAPI(this.mob, this));
        registerAPI("goal", new CustomGoalAPI());

        globals.set("GoalCondition", CustomGoalAPI.buildComponentFactories(com.nishiyu.lunex.entity.goals.GoalComponentRegistry.getConditionKeys(), com.nishiyu.lunex.entity.goals.GoalComponentRegistry::getConditionArgs));
        globals.set("GoalAction", CustomGoalAPI.buildComponentFactories(com.nishiyu.lunex.entity.goals.GoalComponentRegistry.getActionKeys(), com.nishiyu.lunex.entity.goals.GoalComponentRegistry::getActionArgs));

        globals.set("print", new org.luaj.vm2.lib.VarArgFunction() {
            @Override
            public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= args.narg(); i++) {
                    sb.append(args.arg(i).tojstring());
                    if (i < args.narg()) sb.append("  ");
                }
                String messageStr = sb.toString();
                Lunex.LOGGER.info("[EntityVM Print] " + messageStr);

                if (messageStr.toLowerCase().contains("error:")) {
                    String cleanError = parseLuaErrorString(messageStr);

                    if (mob != null) {
                        mob.getPersistentData().putString("LastError", cleanError);
                    }

                    if (currentPlayer instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                serverPlayer,
                                new com.nishiyu.lunex.network.packet.s2c.ErrorToastS2CPacket(cleanError)
                        );
                    }

                    if (mob != null && mob.level().getServer() != null && mob.getPersistentData().getBoolean("DebugLog")) {
                        mob.level().getServer().execute(() -> {
                            for (Player player : mob.level().players()) {
                                if (player.distanceToSqr(mob) < 100) {
                                    player.sendSystemMessage(Component.literal("§c=== Lua Error ==="));
                                    for (String line : cleanError.split("\n")) {
                                        player.sendSystemMessage(Component.literal("§c" + line));
                                    }
                                    player.sendSystemMessage(Component.literal("§c================="));
                                }
                            }
                        });
                    }
                    return LuaValue.NIL;
                }

                if (mob != null && mob.getPersistentData().getBoolean("DebugLog")) {
                    Component msgComp = Component.literal("§b[EntityLua] " + messageStr);
                    if (mob.level().getServer() != null) {
                        mob.level().getServer().execute(() -> {
                            for (Player player : mob.level().players()) {
                                if (player.distanceToSqr(mob) < 100) {
                                    player.sendSystemMessage(msgComp);
                                }
                            }
                        });
                    }
                }

                return LuaValue.NIL;
            }
        });

        globals.set("sys_pollEvent", new OneArgFunction() {
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

                    if (ev.name().equals("dispatchEvent")) {
                        LuaValue mobApi = globals.get("mob");
                        if (mobApi.istable()) {
                            LuaValue dispatch = mobApi.get("dispatchEvent");
                            if (dispatch.isfunction()) {
                                dispatch.call(t.get("name"), t.get("args"));
                            }
                        }
                    }

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

        if (this.mob != null) {
            this.mob.resetForProgram();
            this.mob.getPersistentData().remove("LastError");
        }

        final int currentExecId = this.executionId.get();

        taskFuture = ENTITY_VM_EXECUTOR.submit(() -> {
            Thread thisThread = Thread.currentThread();
            this.currentExecutingThread = thisThread;
            thisThread.setName("Lunex-Entity-VM-" + mob.getUUID().toString().substring(0, 8) + "-" + processName);

            try {
                initSandboxAndAPIs();

                try (java.io.InputStream is = Lunex.class.getResourceAsStream("/assets/lunex/lua/scheduler.lua")) {
                    if (is != null) {
                        String schedulerScript = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        globals.load(schedulerScript, "scheduler.lua").call();
                    }
                } catch (Exception e) {
                    Lunex.LOGGER.error("[EntityVM] scheduler.lua の読み込みに失敗: ", e);
                }

                if (memoryBuffer != null) {
                    loadMemoryFromTag(memoryBuffer);
                }

                String loadName = (processName != null && !processName.isEmpty()) ? processName : "entity_program.lua";
                LuaValue chunk = globals.load(rawCode, loadName);

                LuaValue runScheduler = globals.get("sys_runScheduler");
                if (!runScheduler.isnil()) {
                    runScheduler.call(chunk);
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
                    Lunex.LOGGER.error("[EntityVM] Lua実行エラー: \n" + cleanError);

                    if (mob != null) {
                        mob.getPersistentData().putString("LastError", cleanError);
                    }

                    if (currentPlayer instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                serverPlayer,
                                new com.nishiyu.lunex.network.packet.s2c.ErrorToastS2CPacket(cleanError)
                        );
                    }

                    if (mob != null && mob.level().getServer() != null && mob.getPersistentData().getBoolean("DebugLog")) {
                        mob.level().getServer().execute(() -> {
                            for (Player player : mob.level().players()) {
                                if (player.distanceToSqr(mob) < 100) {
                                    player.sendSystemMessage(Component.literal("§c=== Lua Error ==="));
                                    for (String line : cleanError.split("\n")) {
                                        player.sendSystemMessage(Component.literal("§c" + line));
                                    }
                                    player.sendSystemMessage(Component.literal("§c================="));
                                }
                            }
                        });
                    }

                    String vmId = "biomob_" + mob.getId();
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

    @Override
    public void stopProgram() {
        this.executionId.incrementAndGet();
        isRunning = false;

        if (this.mob != null) {
            this.mob.resetForProgram();
        }

        cleanupAPIs();

        if (taskFuture != null && !taskFuture.isDone()) taskFuture.cancel(true);

        // ★修正: ローカル変数に一時退避してNullPointerException（スレッド競合）を回避
        Thread execThread = this.currentExecutingThread;
        if (execThread != null && execThread.isAlive()) {
            execThread.interrupt();
        }

        mainThreadTasks.clear();
        pendingEvents.clear();
    }

    protected void cleanupAPIs() {
        for (Object apiObj : apiCache.values()) {
            if (apiObj instanceof AutoCloseable closeableApi) {
                try {
                    closeableApi.close();
                } catch (Exception ignored) {
                }
            }
        }
        apiCache.clear();
    }

    public void triggerEvent(String eventName, Object... args) {
        if (isRunning) pendingEvents.add(new LuaEvent(eventName, args));
    }

    public void forceTriggerEvent(String eventName, Object... args) {
        pendingEvents.add(new LuaEvent(eventName, args));
    }

    public void tick() {
        int processed = 0;
        Runnable task;
        while ((task = mainThreadTasks.poll()) != null && processed < 10) {
            try {
                task.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
            processed++;
        }
    }

    public <T> T executeInMainThreadSync(Callable<T> task) {
        if (!isRunning) throw new org.luaj.vm2.LuaError("VM Stopped");

        if (mob.level().getServer() != null && mob.level().getServer().isSameThread()) {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException("EntityVM: Main thread task failed", e);
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
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new org.luaj.vm2.LuaError("VM Stopped");
        } catch (Exception e) {
            throw new RuntimeException("EntityVM: Main thread task failed", e);
        }
    }

    public <T> T executeInMainThreadSync(Callable<T> task, int baseDelayMs, boolean isRender) {
        return executeInMainThreadSync(task);
    }

    public void loadMemoryFromTag(CompoundTag memTag) {
        for (String key : memTag.getAllKeys()) {
            LuaValue loadedVal = loadFromNBT(memTag.get(key));
            if (loadedVal != LuaValue.NIL) globals.set(key, loadedVal);
        }
    }

    public CompoundTag extractMemoryToTag() {
        CompoundTag memTag = new CompoundTag();
        if (globals != null) {
            for (LuaValue key : globals.keys()) {
                if (key.isstring()) {
                    String k = key.checkjstring();
                    if (SYSTEM_GLOBALS.contains(k)) continue;
                    Tag tag = saveToNBT(globals.get(key), 0);
                    if (tag != null) memTag.put(k, tag);
                }
            }
        }
        return memTag;
    }

    public void saveMemoryToNBT() {
        this.memoryBuffer = extractMemoryToTag();
    }

    protected record LuaEvent(String name, Object[] args) {
    }
}