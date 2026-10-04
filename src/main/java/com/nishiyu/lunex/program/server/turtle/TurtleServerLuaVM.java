package com.nishiyu.lunex.program.server.turtle;

import com.nishiyu.lunex.machine.TurtleCore;
import com.nishiyu.lunex.mcnet.DeviceAPIRegistry;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.program.server.SystemAPI;
import com.nishiyu.lunex.program.server.machine.api.*;
import com.nishiyu.lunex.program.server.turtle.api.TurtleAPI;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.HashSet;
import java.util.Set;

public class TurtleServerLuaVM extends ServerLuaVM {

    private static final Set<String> TURTLE_SYSTEM_GLOBALS;

    static {
        TURTLE_SYSTEM_GLOBALS = new HashSet<>(ServerLuaVM.BASE_SYSTEM_GLOBALS);
        TURTLE_SYSTEM_GLOBALS.add("Direction");
    }

    public final TurtleCore core;

    public TurtleServerLuaVM(TurtleCore core) {
        super();
        this.core = core;
    }

    @Override
    protected Set<String> getSystemGlobals() {
        return TURTLE_SYSTEM_GLOBALS;
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

        // スケジューラー(scheduler.lua)が動作するために必須の system API
        registerAPI("system", getOrCreateAPI(SystemAPI.class, SystemAPI::new));

        // Turtle固有のAPI登録
        registerAPI("turtle", getOrCreateAPI(TurtleAPI.class, TurtleAPI::new));
    }
}