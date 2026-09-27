package com.nishiyu.lunex.program.server.tool.api;

import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.tool.ToolServerLuaVM;
import org.luaj.vm2.LuaValue;

public class ToolTargetAPI extends ToolAPIBase {
    private float pendingBonusDamage = 0;

    public ToolTargetAPI(ToolServerLuaVM vm) {
        super(vm);
    }

    public void resetContext() {
        this.pendingBonusDamage = 0;
    }

    public float getPendingBonusDamage() {
        return this.pendingBonusDamage;
    }

    @LuaFunction(
            value = "攻撃した対象のUUID(文字列)を取得します。対象がいない場合はnilを返します。(消費なし)",
            en = "Gets the UUID (string) of the attacked target. Returns nil if there is no target. (No cost)",
            args = {},
            rets = {"str:uuid"},
            isAsync = false
    )
    public LuaValue getId() {
        if (vm.currentTarget != null) {
            return LuaValue.valueOf(vm.currentTarget.getStringUUID());
        }
        return LuaValue.NIL;
    }

    @LuaFunction(
            value = "攻撃時(on_attack_entity)に追加のダメージを要求します。(1ダメージにつき 1000 FE消費)",
            en = "Requests additional damage upon attack (on_attack_entity). (Costs 1000 FE per damage point)",
            args = {"num:amount"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean addDamage(double amount) {
        if (amount <= 0) return false;
        float requestedDamage = (float) Math.min(amount, 10.0);
        int cost = (int) (requestedDamage * COST_BASE);

        if (consumeEnergy(cost, "addDamage")) {
            this.pendingBonusDamage += requestedDamage;
            return true;
        }
        return false;
    }
}