package com.nishiyu.lunex.program.server.turtle;

import com.nishiyu.lunex.blockentity.TurtleBotBlockEntity;
import com.nishiyu.lunex.program.server.machine.MachineServerLuaVM;
import com.nishiyu.lunex.program.server.turtle.api.TurtleAPI;

public class TurtleServerLuaVM extends MachineServerLuaVM {

    public TurtleServerLuaVM(TurtleBotBlockEntity turtleEntity) {
        super(turtleEntity);
    }

    @Override
    protected void initSandboxAndAPIs() {
        super.initSandboxAndAPIs();

        registerAPI("turtle", getOrCreateAPI(TurtleAPI.class, TurtleAPI::new));
    }
}