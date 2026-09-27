package com.nishiyu.lunex.program.server.tool;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.machine.ItemMachineContext;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.program.server.tool.api.*;
import com.nishiyu.lunex.server.ServerProgramData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaValue;

import java.util.Map;

public class
ToolServerLuaVM extends ServerLuaVM {

    public ItemMachineContext context;
    public net.minecraft.world.entity.Entity currentTarget;
    public ToolAPI toolAPI;

    public String currentEventName = "";
    private boolean isLoaded = false;

    public ToolServerLuaVM(ItemMachineContext context) {
        super(context);
        this.context = context;
        this.toolAPI = new ToolAPI(this);
    }

    @Override
    protected void initSandboxAndAPIs() {
        super.initSandboxAndAPIs();

        registerAPI("tool", this.toolAPI);
        registerAPI("tool.target", getOrCreateAPI(ToolTargetAPI.class, vm -> new ToolTargetAPI(this)));
        registerAPI("tool.player", getOrCreateAPI(ToolPlayerAPI.class, vm -> new ToolPlayerAPI(this)));
        registerAPI("tool.world", getOrCreateAPI(ToolWorldAPI.class, vm -> new ToolWorldAPI(this)));
        registerAPI("tool.entity", getOrCreateAPI(ToolEntityAPI.class, vm -> new ToolEntityAPI(this)));
        registerAPI("tool.task", getOrCreateAPI(ToolTaskAPI.class, vm -> new ToolTaskAPI(this)));
    }

    public void loadProgram(String programName, CompoundTag previousMemory) {
        try {
            String wsId = context.getWorkspaceId();
            ServerProgramData.load(wsId);
            Map<String, String> progs = ServerProgramData.getPrograms(wsId);
            String rawCode = progs.getOrDefault(programName, "");

            initSandboxAndAPIs();

            if (previousMemory != null) {
                loadMemoryFromTag(previousMemory);
            }

            LuaValue chunk = globals.load(rawCode);
            chunk.call();

            LuaValue onInit = globals.get("on_init");
            if (onInit.isfunction()) {
                this.currentEventName = "on_init";
                onInit.call();
                this.currentEventName = "";
            }

            this.isLoaded = true;

        } catch (Exception e) {
            handleError(e);
        }
    }

    public LuaValue triggerEventSync(String eventName, Object... args) {
        if (!isLoaded || globals == null) return LuaValue.NIL;

        this.toolAPI.resetContext();
        this.currentEventName = eventName;

        try {
            LuaValue func = globals.get(eventName);
            if (func.isfunction()) {
                if (args == null || args.length == 0) {
                    return func.call();
                } else {
                    LuaValue[] luaArgs = new LuaValue[args.length];
                    for (int i = 0; i < args.length; i++) {
                        luaArgs[i] = org.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(args[i]);
                    }
                    return func.invoke(LuaValue.varargsOf(luaArgs)).arg1();
                }
            }
        } catch (Exception e) {
            handleError(e);
        } finally {
            this.currentEventName = "";
        }
        return LuaValue.NIL;
    }

    private void handleError(Exception e) {
        Lunex.LOGGER.error("[Lunex] Tool Lua実行エラー: ", e);
        if (context.isDebugChat() && currentPlayer != null && !currentPlayer.level().isClientSide) {
            String errMsg = e.getMessage();
            if (e instanceof org.luaj.vm2.LuaError luaError) {
                errMsg = luaError.getMessage();
                if (luaError.getCause() != null) {
                    errMsg += " (" + luaError.getCause().getMessage() + ")";
                }
            }
            currentPlayer.sendSystemMessage(Component.literal("§c[Tool Luaエラー] " + errMsg));
        }
    }
}