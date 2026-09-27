package com.nishiyu.lunex.program.server.tool.api;

import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.tool.ToolServerLuaVM;

public class ToolAPI extends ToolAPIBase {

    public final ToolTargetAPI target;
    public final ToolPlayerAPI player;
    public final ToolWorldAPI world;
    public final ToolEntityAPI entity;
    public final ToolTaskAPI task;

    public ToolAPI(ToolServerLuaVM vm) {
        super(vm);
        this.target = new ToolTargetAPI(vm);
        this.player = new ToolPlayerAPI(vm);
        this.world = new ToolWorldAPI(vm);
        this.entity = new ToolEntityAPI(vm);
        this.task = new ToolTaskAPI(vm);
    }

    public void resetContext() {
        this.target.resetContext();
    }

    public float getPendingBonusDamage() {
        return this.target.getPendingBonusDamage();
    }

    @LuaFunction(
            value = "ツール使用者に直接メッセージを送ります。",
            en = "Sends a direct message to the tool user.",
            args = {"str:message"},
            rets = {},
            isAsync = true
    )
    public void chat(String message) {
        super.chat(message);
    }

    @LuaFunction(
            value = "現在のツールのエネルギー残量を取得します。",
            en = "Gets the remaining energy of the current tool.",
            args = {},
            rets = {"num:energy"},
            isAsync = false
    )
    public int getEnergy() {
        return vm.context.getEnergy();
    }
}