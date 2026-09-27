package com.nishiyu.lunex.program.server.tool.api;

import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.tool.ToolServerLuaVM;

public class ToolPlayerAPI extends ToolAPIBase {

    public ToolPlayerAPI(ToolServerLuaVM vm) {
        super(vm);
    }

    @LuaFunction(
            value = "プレイヤー自身のUUID(文字列)を取得します。(消費なし)",
            en = "Gets the player's own UUID (string). (No cost)",
            args = {},
            rets = {"str:uuid"},
            isAsync = false
    )
    public String getId() {
        return vm.currentPlayer != null ? vm.currentPlayer.getStringUUID() : "";
    }
}