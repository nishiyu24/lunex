package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.api.MainframeConstants;
import com.nishiyu.lunex.api.mainframe.IMainframeAPI;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.mcnet.VirtualStorage;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
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

public class StorageAPI implements IMainframeAPI {
    private ServerLuaVM vm;

    public StorageAPI() {}
    public StorageAPI(ServerLuaVM vm) { this.vm = vm; }

    @Override
    public String getNamespace() { return MainframeConstants.API_STORAGE; }

    @Override
    public String getRequiredFeature() { return MainframeConstants.FEATURE_ROUTER; }

    @Override
    public Object createInstance(ServerLuaVM vm) {
        return new StorageAPI(vm);
    }

    private SimpleMachineBlockEntity findConnectedRouter() {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null || machine.getLevel() == null || machine.getCore() == null) return null;

        // ★修正: getCore() 経由に変更
        if (machine.isMainframeMaster && machine.getCore().activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
            return machine;
        }

        CompoundTag data = machine.getCore().persistentData;
        if (data != null && data.contains("RouterPos")) {
            BlockPos routerPos = BlockPos.of(data.getLong("RouterPos"));
            BlockEntity be = machine.getLevel().getBlockEntity(routerPos);
            if (be instanceof SimpleMachineBlockEntity router && router.isMainframeMaster && router.getCore() != null && router.getCore().activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                return router;
            }
        }
        return null;
    }

    @LuaFunction(
            value = "ルーター配下のインベントリを仮想ストレージとして構築し、操作用オブジェクトを返します。",
            args = {},
            rets = {"table:storage"},
            isAsync = true
    )
    public LuaTable build() {
        return vm.executeInMainThreadSync(() -> {
            LuaTable storageObj = new LuaTable();
            SimpleMachineBlockEntity router = findConnectedRouter();

            // ★修正: virtualStorage は getCore() 経由に変更
            if (router == null || router.getLevel() == null || router.getCore() == null || router.getCore().virtualStorage == null) return storageObj;

            if (router.getCore().virtualStorage.getAllItems().isEmpty() && router.getCore().virtualStorage.rules.isEmpty()) {
                router.getCore().virtualStorage.rebuildNetworkCache(router.getLevel());
            }

            final SimpleMachineBlockEntity finalRouter = router;

            storageObj.set("getItemCount", new OneArgFunction() {
                @Override public LuaValue call(LuaValue itemName) { return getItemCount(finalRouter, itemName); }
            });

            storageObj.set("pushToTag", new ThreeArgFunction() {
                @Override public LuaValue call(LuaValue tag, LuaValue itemName, LuaValue amount) { return pushToTag(finalRouter, tag, itemName, amount); }
            });

            storageObj.set("forceUpdate", new ZeroArgFunction() {
                @Override public LuaValue call() { return forceUpdate(finalRouter); }
            });

            LuaTable logisticsObj = new LuaTable();
            logisticsObj.set("newRule", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    LuaTable builder = new LuaTable();
                    VirtualStorage.LogisticsRule rule = new VirtualStorage.LogisticsRule();

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
                    VirtualStorage.CraftingPattern pattern = new VirtualStorage.CraftingPattern();

                    builder.set("inputs", new OneArgFunction() {
                        @Override public LuaValue call(LuaValue arg) {
                            if (arg.istable()) {
                                LuaTable t = arg.checktable();
                                for (LuaValue k : t.keys()) pattern.inputs.put(k.tojstring(), t.get(k).toint());
                            }
                            return builder;
                        }
                    });
                    builder.set("outputs", new OneArgFunction() {
                        @Override public LuaValue call(LuaValue arg) {
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
                        if (!hidden.isnil() && hidden.isuserdata(VirtualStorage.CraftingPattern.class)) {
                            finalRouter.getCore().virtualStorage.crafting.addPattern((VirtualStorage.CraftingPattern) hidden.checkuserdata());
                            return LuaValue.TRUE;
                        }
                    }
                    return LuaValue.FALSE;
                }
            });

            craftingObj.set("request", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue item, LuaValue amount) {
                    int jobId = finalRouter.getCore().virtualStorage.crafting.requestCraft(item.tojstring(), amount.toint(), finalRouter.getCore().virtualStorage);
                    return jobId >= 0 ? LuaValue.valueOf(jobId) : LuaValue.NIL;
                }
            });

            craftingObj.set("getStatus", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue jobId) {
                    VirtualStorage.CraftingJob job = finalRouter.getCore().virtualStorage.crafting.getJob(jobId.toint());
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
                    for (VirtualStorage.CraftingJob job : finalRouter.getCore().virtualStorage.crafting.getJobs()) {
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
            storageObj.set("userdata", CoerceJavaToLua.coerce(finalRouter.getCore().virtualStorage));

            return storageObj;
        });
    }

    public LuaValue getItemCount(SimpleMachineBlockEntity router, LuaValue itemName) {
        if (router.getCore() == null || router.getCore().virtualStorage == null) return LuaValue.valueOf(0);
        return LuaValue.valueOf(router.getCore().virtualStorage.getItemCount(itemName.tojstring()));
    }

    public LuaValue pushToTag(SimpleMachineBlockEntity router, LuaValue tag, LuaValue itemName, LuaValue amount) {
        if (router.getCore() == null || router.getCore().virtualStorage == null) return LuaValue.FALSE;
        boolean result = router.getCore().virtualStorage.pushToTag(tag.tojstring(), itemName.tojstring(), amount.toint(), router.getLevel());
        return LuaValue.valueOf(result);
    }

    public LuaValue forceUpdate(SimpleMachineBlockEntity router) {
        if (router.getCore() != null && router.getCore().virtualStorage != null) {
            router.getCore().virtualStorage.onStorageChanged(router.getLevel());
        }
        return LuaValue.NIL;
    }

    public LuaValue addRule(SimpleMachineBlockEntity router, LuaValue ruleArg) {
        if (ruleArg.istable() && router.getCore() != null && router.getCore().virtualStorage != null) {
            LuaValue hiddenRule = ruleArg.get("_rule");
            if (!hiddenRule.isnil() && hiddenRule.isuserdata(VirtualStorage.LogisticsRule.class)) {
                VirtualStorage.LogisticsRule rule = (VirtualStorage.LogisticsRule) hiddenRule.checkuserdata();
                rule.compile();
                router.getCore().virtualStorage.addRule(rule);
                return LuaValue.TRUE;
            }
        }
        return LuaValue.FALSE;
    }

    public LuaValue clearRules(SimpleMachineBlockEntity router) {
        if (router.getCore() != null && router.getCore().virtualStorage != null) {
            router.getCore().virtualStorage.clearRules();
        }
        return LuaValue.NIL;
    }
}