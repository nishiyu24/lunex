package com.nishiyu.lunex.program.server.turtle;

import com.nishiyu.lunex.program.server.machine.MachineAPIRegistry;
import com.nishiyu.lunex.program.server.turtle.api.TurtleAPI;

// タートルはマシンの機能を全て継承するため、MachineAPIRegistry を継承します
public class TurtleAPIRegistry extends MachineAPIRegistry {

    @Override
    protected void registerAPIs() {
        super.registerAPIs();
        registerAPIClass("turtle", TurtleAPI.class);
    }
}