package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.blockentity.RouterBlockEntity;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.util.Map;

public class StorageAPI {
    private final ServerLuaVM vm;

    public StorageAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    private RouterBlockEntity findConnectedRouter() {
        BlockEntity hardware = vm.hardware;
        if (hardware == null || hardware.getLevel() == null) return null;
        CompoundTag data = hardware instanceof AdvancedMachineBlockEntity m ? m.persistentData : hardware.getPersistentData();

        // ★ 変更: MainframeNetworkTagやメインフレーム構成にルーターが含まれる場合の拡張対応をここで行うことも可能ですが、
        // 基本的には既存の "RouterPos" NBTを参照するロジックをベースにします。
        if (data != null && data.contains("RouterPos")) {
            BlockPos routerPos = BlockPos.of(data.getLong("RouterPos"));
            BlockEntity be = hardware.getLevel().getBlockEntity(routerPos);
            if (be instanceof RouterBlockEntity router) return router;
        }
        return null;
    }

    @LuaFunction(
            value = "ルーター配下のインベントリを仮想ストレージとして構築し、操作用オブジェクトを返します。",
            en = "Builds virtual storage from inventories under the router and returns an operation object.",
            args = {},
            rets = {"table:storage"},
            isAsync = true
    )
    public LuaTable build() {
        return vm.executeInMainThreadSync(() -> {
            LuaTable storageObj = new LuaTable();
            RouterBlockEntity router = findConnectedRouter();

            BlockEntity machine = (BlockEntity) vm.machine;
            if (machine instanceof RouterBlockEntity rbe) router = rbe;

            if (router == null || router.getLevel() == null) return storageObj;

            if (router.virtualStorage.getAllItems().isEmpty() && router.virtualStorage.rules.isEmpty()) {
                router.virtualStorage.rebuildNetworkCache(router.getLevel());
            }

            final RouterBlockEntity finalRouter = router;

            storageObj.set("getItemCount", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue itemName) {
                    return getItemCount(finalRouter, itemName);
                }
            });

            storageObj.set("pushToTag", new ThreeArgFunction() {
                @Override
                public LuaValue call(LuaValue tag, LuaValue itemName, LuaValue amount) {
                    return pushToTag(finalRouter, tag, itemName, amount);
                }
            });

            storageObj.set("forceUpdate", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    return forceUpdate(finalRouter);
                }
            });

            LuaTable logisticsObj = new LuaTable();
            logisticsObj.set("newRule", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    LuaTable builder = new LuaTable();
                    com.nishiyu.lunex.machine.VirtualStorage.LogisticsRule rule = new com.nishiyu.lunex.machine.VirtualStorage.LogisticsRule();

                    builder.set("type", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { rule.type = arg.tojstring(); return builder; }});
                    builder.set("source", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { rule.sourceTag = arg.tojstring(); return builder; }});
                    builder.set("target", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { rule.targetTag = arg.tojstring(); return builder; }});
                    builder.set("extractSide", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { rule.extractSide = arg.tojstring(); return builder; }});
                    builder.set("insertSide", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { rule.insertSide = arg.tojstring(); return builder; }});
                    builder.set("extractSlot", new TwoArgFunction() { @Override public LuaValue call(LuaValue min, LuaValue max) { rule.extractSlotStart = min.toint() - 1; rule.extractSlotEnd = max.isnil() ? rule.extractSlotStart : max.toint() - 1; return builder; }});
                    builder.set("insertSlot", new TwoArgFunction() { @Override public LuaValue call(LuaValue min, LuaValue max) { rule.insertSlotStart = min.toint() - 1; rule.insertSlotEnd = max.isnil() ? rule.insertSlotStart : max.toint() - 1; return builder; }});
                    builder.set("item", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { rule.itemNames.clear(); rule.itemNames.add(arg.tojstring()); return builder; }});
                    builder.set("items", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { rule.itemNames.clear(); if (arg.istable()) { LuaTable t = arg.checktable(); for (int i = 1; i <= t.length(); i++) { rule.itemNames.add(t.get(i).tojstring()); } } else { rule.itemNames.add(arg.tojstring()); } return builder; }});
                    builder.set("invertFilter", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { rule.invertFilter = arg.toboolean(); return builder; }});
                    builder.set("distribution", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { rule.distribution = arg.tojstring(); return builder; }});
                    builder.set("batch", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { rule.batchSize = arg.toint(); return builder; }});
                    builder.set("priority", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { rule.priority = arg.toint(); return builder; }});
                    builder.set("nbt", new TwoArgFunction() { @Override public LuaValue call(LuaValue json, LuaValue exact) { rule.nbtJson = json.tojstring(); rule.nbtExact = exact.toboolean(); return builder; }});
                    builder.set("conditionItem", new ThreeArgFunction() { @Override public LuaValue call(LuaValue item, LuaValue op, LuaValue amt) { rule.condItem = item.tojstring(); rule.condOp = op.tojstring(); rule.condAmount = amt.toint(); return builder; }});
                    builder.set("conditionRedstone", new ThreeArgFunction() { @Override public LuaValue call(LuaValue targetTag, LuaValue op, LuaValue power) { rule.condRedstoneTarget = targetTag.tojstring(); rule.condRedstoneOp = op.tojstring(); rule.condRedstonePower = power.toint(); return builder; }});
                    builder.set("_rule", CoerceJavaToLua.coerce(rule));
                    return builder;
                }
            });
            logisticsObj.set("addRule", new OneArgFunction() { @Override public LuaValue call(LuaValue ruleArg) { return addRule(finalRouter, ruleArg); }});
            logisticsObj.set("clearRules", new ZeroArgFunction() { @Override public LuaValue call() { return clearRules(finalRouter); }});
            storageObj.set("logistics", logisticsObj);

            LuaTable craftingObj = new LuaTable();
            craftingObj.set("newPattern", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    LuaTable builder = new LuaTable();
                    com.nishiyu.lunex.machine.VirtualStorage.CraftingPattern pattern = new com.nishiyu.lunex.machine.VirtualStorage.CraftingPattern();

                    builder.set("inputs", new OneArgFunction() {
                        @Override
                        public LuaValue call(LuaValue arg) {
                            if (arg.istable()) {
                                LuaTable t = arg.checktable();
                                for (LuaValue k : t.keys()) pattern.inputs.put(k.tojstring(), t.get(k).toint());
                            }
                            return builder;
                        }
                    });
                    builder.set("outputs", new OneArgFunction() {
                        @Override
                        public LuaValue call(LuaValue arg) {
                            if (arg.istable()) {
                                LuaTable t = arg.checktable();
                                for (LuaValue k : t.keys()) pattern.outputs.put(k.tojstring(), t.get(k).toint());
                            }
                            return builder;
                        }
                    });
                    builder.set("target", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { pattern.targetTag = arg.tojstring(); return builder; }});
                    builder.set("insertSide", new OneArgFunction() { @Override public LuaValue call(LuaValue arg) { pattern.insertSide = arg.tojstring(); return builder; }});
                    builder.set("_pattern", CoerceJavaToLua.coerce(pattern));
                    return builder;
                }
            });

            craftingObj.set("addPattern", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue arg) {
                    if (arg.istable()) {
                        LuaValue hidden = arg.get("_pattern");
                        if (!hidden.isnil() && hidden.isuserdata(com.nishiyu.lunex.machine.VirtualStorage.CraftingPattern.class)) {
                            finalRouter.virtualStorage.crafting.addPattern((com.nishiyu.lunex.machine.VirtualStorage.CraftingPattern) hidden.checkuserdata());
                            return LuaValue.TRUE;
                        }
                    }
                    return LuaValue.FALSE;
                }
            });

            craftingObj.set("request", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue item, LuaValue amount) {
                    int jobId = finalRouter.virtualStorage.crafting.requestCraft(item.tojstring(), amount.toint(), finalRouter.virtualStorage);
                    return jobId >= 0 ? LuaValue.valueOf(jobId) : LuaValue.NIL;
                }
            });

            craftingObj.set("getStatus", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue jobId) {
                    com.nishiyu.lunex.machine.VirtualStorage.CraftingJob job = finalRouter.virtualStorage.crafting.getJob(jobId.toint());
                    if (job == null) return LuaValue.NIL;

                    LuaTable status = new LuaTable();
                    status.set("state", job.state.name());
                    status.set("completed", LuaValue.valueOf(job.completedAmount));

                    LuaTable missing = new LuaTable();
                    for (Map.Entry<String, Integer> entry : job.missingIngredients.entrySet()) {
                        missing.set(entry.getKey(), entry.getValue());
                    }
                    status.set("missing", missing);
                    return status;
                }
            });

            craftingObj.set("getActiveJobs", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    LuaTable list = new LuaTable();
                    int i = 1;
                    for (com.nishiyu.lunex.machine.VirtualStorage.CraftingJob job : finalRouter.virtualStorage.crafting.getJobs()) {
                        LuaTable j = new LuaTable();
                        j.set("id", job.id);
                        j.set("item", job.requestItem);
                        j.set("amount", job.requestAmount);
                        j.set("completed", job.completedAmount);
                        j.set("state", job.state.name());
                        list.set(i++, j);
                    }
                    return list;
                }
            });

            storageObj.set("crafting", craftingObj);
            storageObj.set("userdata", CoerceJavaToLua.coerce(finalRouter.virtualStorage));

            return storageObj;
        });
    }

    public LuaValue getItemCount(RouterBlockEntity router, LuaValue itemName) {
        return LuaValue.valueOf(router.virtualStorage.getItemCount(itemName.tojstring()));
    }

    public LuaValue pushToTag(RouterBlockEntity router, LuaValue tag, LuaValue itemName, LuaValue amount) {
        boolean result = router.virtualStorage.pushToTag(tag.tojstring(), itemName.tojstring(), amount.toint(), router.getLevel());
        return LuaValue.valueOf(result);
    }

    public LuaValue forceUpdate(RouterBlockEntity router) {
        router.virtualStorage.onStorageChanged(router.getLevel());
        return LuaValue.NIL;
    }

    public LuaValue addRule(RouterBlockEntity router, LuaValue ruleArg) {
        if (ruleArg.istable()) {
            LuaValue hiddenRule = ruleArg.get("_rule");
            if (!hiddenRule.isnil() && hiddenRule.isuserdata(com.nishiyu.lunex.machine.VirtualStorage.LogisticsRule.class)) {
                com.nishiyu.lunex.machine.VirtualStorage.LogisticsRule rule = (com.nishiyu.lunex.machine.VirtualStorage.LogisticsRule) hiddenRule.checkuserdata();
                rule.compile();
                router.virtualStorage.addRule(rule);
                return LuaValue.TRUE;
            }
        }
        return LuaValue.FALSE;
    }

    public LuaValue clearRules(RouterBlockEntity router) {
        router.virtualStorage.clearRules();
        return LuaValue.NIL;
    }
}