package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.blockentity.DatabaseBlockEntity;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.util.TargetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class InventoryAPI {
    private final ServerLuaVM vm;

    public InventoryAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    public record TargetInfo(Level level, BlockPos pos, Direction direction) {}

    public TargetInfo resolveTarget(String targetStr) {
        AdvancedMachineBlockEntity machine = vm.hardware;
        if (machine == null || machine.getLevel() == null || targetStr == null) return null;
        Level level = machine.getLevel();

        if (targetStr.contains(":")) {
            String[] parts = targetStr.split(":", 2);
            BlockPos basePos = machine.resolveDevice(parts[0]);
            if (basePos != null) {
                Direction dir = TargetUtil.getDirectionRelative(parts[1], level.getBlockState(basePos));
                if (dir != null) {
                    return new TargetInfo(level, basePos.relative(dir), dir.getOpposite());
                }
            }
            return null;
        }

        BlockPos resolvedPos = machine.resolveDevice(targetStr);
        if (resolvedPos != null) {
            return new TargetInfo(level, resolvedPos, null);
        }

        Direction dir = TargetUtil.getDirectionRelative(targetStr, machine.getBlockState());
        if (dir != null) {
            return new TargetInfo(level, machine.getBlockPos().relative(dir), dir.getOpposite());
        }

        return null;
    }

    public DatabaseBlockEntity getTargetDatabase(String targetStr) {
        TargetInfo info = resolveTarget(targetStr);
        if (info != null && info.level().getBlockEntity(info.pos()) instanceof DatabaseBlockEntity db) {
            return db;
        }
        return null;
    }

    public IItemHandler getTargetInventory(String targetStr) {
        AdvancedMachineBlockEntity machine = vm.hardware;
        if (machine == null || machine.getLevel() == null || targetStr == null) return null;
        Level level = machine.getLevel();

        if (targetStr.contains(":")) {
            TargetInfo info = resolveTarget(targetStr);
            if (info != null) {
                return level.getCapability(Capabilities.ItemHandler.BLOCK, info.pos(), info.direction());
            }
            return null;
        }

        BlockPos resolvedPos = machine.resolveDevice(targetStr);
        if (resolvedPos != null) {
            BlockState state = level.getBlockState(resolvedPos);
            if (state.getBlock() instanceof com.nishiyu.lunex.block.ProbeBlock) {
                // 純粋にProbeの面設定だけを参照する
                List<IItemHandlerModifiable> handlers = new ArrayList<>();
                for (Direction dir : Direction.values()) {
                    if (state.getValue(com.nishiyu.lunex.block.ProbeBlock.getPropertyByDirection(dir))) {
                        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, resolvedPos.relative(dir), dir.getOpposite());
                        if (handler instanceof IItemHandlerModifiable modifiableHandler) handlers.add(modifiableHandler);
                    }
                }
                if (handlers.isEmpty()) return null;
                if (handlers.size() == 1) return handlers.getFirst();
                return new CombinedInvWrapper(handlers.toArray(new IItemHandlerModifiable[0]));
            } else {
                return level.getCapability(Capabilities.ItemHandler.BLOCK, resolvedPos, null);
            }
        }

        Direction dir = TargetUtil.getDirectionRelative(targetStr, machine.getBlockState());
        if (dir != null) {
            return level.getCapability(Capabilities.ItemHandler.BLOCK, machine.getBlockPos().relative(dir), dir.getOpposite());
        }

        return null;
    }

    @LuaFunction(
            value = "ターゲットのインベントリ内でアイテムを指定したスロット間で移動させます。",
            en = "Moves items between specified slots within the target's inventory.",
            args = {"str:target", "num:fromSlot", "num:toSlot", "num:amount"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean moveItem(String targetStr, int fromSlot, int toSlot, int amount) {
        return vm.executeInMainThreadSync(() -> {
            try {
                if (getTargetDatabase(targetStr) != null) return false;
                IItemHandler targetInv = getTargetInventory(targetStr);
                if (targetInv == null) return false;

                int slots = targetInv.getSlots();
                if (fromSlot < 1 || fromSlot > slots || toSlot < 1 || toSlot > slots || fromSlot == toSlot || amount <= 0) return false;

                int fSlot = fromSlot - 1;
                int tSlot = toSlot - 1;

                ItemStack extractSim = targetInv.extractItem(fSlot, amount, true);
                if (extractSim.isEmpty()) return false;

                ItemStack remaining = targetInv.insertItem(tSlot, extractSim, false);
                int actuallyMoved = extractSim.getCount() - remaining.getCount();

                if (actuallyMoved > 0) {
                    targetInv.extractItem(fSlot, actuallyMoved, false);
                    if (vm.hardware != null) vm.hardware.setChanged();
                    return true;
                }
                return false;
            } catch (Exception e) { return false; }
        });
    }

    @LuaFunction(
            value = "マシンのスロットからターゲットのインベントリへアイテムを搬出(Push)します。",
            en = "Pushes items from the machine's slot to the target's inventory.",
            args = {"str:target", "num:machineSlot", "num:amount"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean pushItem(String targetStr, int machineSlot, int amount) {
        return vm.executeInMainThreadSync(() -> {
            try {
                AdvancedMachineBlockEntity machine = vm.hardware;
                if (machine == null || amount <= 0) return false;

                ItemStack extractSim = machine.itemHandler.extractItem(machineSlot - 1, amount, true);
                if (extractSim.isEmpty()) return false;

                DatabaseBlockEntity db = getTargetDatabase(targetStr);
                if (db != null) {
                    ItemStack leftover = db.insertItem(extractSim.copy(), true);
                    int inserted = extractSim.getCount() - leftover.getCount();
                    if (inserted > 0) {
                        db.insertItem(machine.itemHandler.extractItem(machineSlot - 1, inserted, false), false);
                        machine.setChanged();
                        return true;
                    }
                    return false;
                }

                IItemHandler targetInv = getTargetInventory(targetStr);
                if (targetInv != null) {
                    ItemStack remaining = ItemHandlerHelper.insertItem(targetInv, extractSim, false);
                    int inserted = extractSim.getCount() - remaining.getCount();
                    if (inserted > 0) {
                        machine.itemHandler.extractItem(machineSlot - 1, inserted, false);
                        machine.setChanged();
                        return true;
                    }
                }
                return false;
            } catch (Exception e) { return false; }
        });
    }

    @LuaFunction(
            value = "ターゲットのインベントリからマシンへアイテムを搬入(Pull)します。",
            en = "Pulls items from the target's inventory into the machine.",
            args = {"str:target", "num:targetSlot", "num:amount"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean pullItem(String targetStr, int targetSlot, int amount) {
        return vm.executeInMainThreadSync(() -> {
            try {
                AdvancedMachineBlockEntity machine = vm.hardware;
                if (machine == null || amount <= 0) return false;

                DatabaseBlockEntity db = getTargetDatabase(targetStr);
                if (db != null) {
                    if (targetSlot < 1 || targetSlot > db.itemCounts.size()) return false;
                    String targetId = db.itemIds.get(db.itemCounts.keySet().toArray()[targetSlot - 1]);

                    ItemStack extractSim = db.extractItem(targetId, amount, true);
                    if (extractSim.isEmpty()) return false;

                    ItemStack leftover = ItemHandlerHelper.insertItem(machine.itemHandler, extractSim.copy(), true);
                    int accepted = extractSim.getCount() - leftover.getCount();
                    if (accepted > 0) {
                        ItemHandlerHelper.insertItem(machine.itemHandler, db.extractItem(targetId, accepted, false), false);
                        machine.setChanged();
                        return true;
                    }
                    return false;
                }

                IItemHandler targetInv = getTargetInventory(targetStr);
                if (targetInv != null) {
                    ItemStack extractSim = targetInv.extractItem(targetSlot - 1, amount, true);
                    if (extractSim.isEmpty()) return false;

                    ItemStack remaining = ItemHandlerHelper.insertItem(machine.itemHandler, extractSim, false);
                    int inserted = extractSim.getCount() - remaining.getCount();

                    if (inserted > 0) {
                        targetInv.extractItem(targetSlot - 1, inserted, false);
                        machine.setChanged();
                        return true;
                    }
                }
                return false;
            } catch (Exception e) { return false; }
        });
    }

    @LuaFunction(
            value = "指定したインベントリの最大スロット数(サイズ)を取得します。",
            en = "Gets the maximum number of slots (size) of the specified inventory.",
            args = {"str:target"},
            rets = {"num:size"},
            isAsync = false
    )
    public int getInventorySize(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            try {
                DatabaseBlockEntity db = getTargetDatabase(targetStr);
                if (db != null) return db.itemCounts.size();
                IItemHandler targetInv = getTargetInventory(targetStr);
                return targetInv != null ? targetInv.getSlots() : 0;
            } catch (Exception e) { return 0; }
        }, 0, false);
    }

    @LuaFunction(
            value = "指定したスロットに存在するアイテムのID(名前)を取得します。",
            en = "Gets the ID (name) of the item present in the specified slot.",
            args = {"str:target", "num:slot"},
            rets = {"str:itemName"},
            isAsync = false
    )
    public String getItemName(String targetStr, int targetSlot) {
        return vm.executeInMainThreadSync(() -> {
            try {
                DatabaseBlockEntity db = getTargetDatabase(targetStr);
                if (db != null) {
                    if (targetSlot < 1 || targetSlot > db.itemCounts.size()) return "empty";
                    return db.itemIds.get(db.itemCounts.keySet().toArray()[targetSlot - 1]);
                }
                IItemHandler targetInv = getTargetInventory(targetStr);
                if (targetInv == null || targetSlot < 1 || targetSlot > targetInv.getSlots()) return "empty";
                ItemStack stack = targetInv.getStackInSlot(targetSlot - 1);
                return stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            } catch (Exception e) { return "empty"; }
        }, 0, false);
    }

    @LuaFunction(
            value = "指定したスロットに存在するアイテムの個数を取得します。",
            en = "Gets the quantity of items present in the specified slot.",
            args = {"str:target", "num:slot"},
            rets = {"num:count"},
            isAsync = false
    )
    public int getItemCount(String targetStr, int targetSlot) {
        return vm.executeInMainThreadSync(() -> {
            try {
                DatabaseBlockEntity db = getTargetDatabase(targetStr);
                if (db != null) {
                    if (targetSlot < 1 || targetSlot > db.itemCounts.size()) return 0;
                    long count = db.itemCounts.get((String) db.itemCounts.keySet().toArray()[targetSlot - 1]);
                    return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
                }
                IItemHandler targetInv = getTargetInventory(targetStr);
                if (targetInv == null || targetSlot < 1 || targetSlot > targetInv.getSlots()) return 0;
                return targetInv.getStackInSlot(targetSlot - 1).getCount();
            } catch (Exception e) { return 0; }
        }, 0, false);
    }

    @LuaFunction(
            value = "指定したスロットのアイテムの1スタックあたりの最大個数を取得します。",
            en = "Gets the maximum stack size of the item in the specified slot.",
            args = {"str:target", "num:slot"},
            rets = {"num:maxStackSize"},
            isAsync = false
    )
    public int getMaxStackSize(String targetStr, int targetSlot) {
        return vm.executeInMainThreadSync(() -> {
            try {
                DatabaseBlockEntity db = getTargetDatabase(targetStr);
                if (db != null) return Integer.MAX_VALUE;
                IItemHandler targetInv = getTargetInventory(targetStr);
                if (targetInv == null || targetSlot < 1 || targetSlot > targetInv.getSlots()) return 0;
                return targetInv.getStackInSlot(targetSlot - 1).isEmpty() ? 0 : targetInv.getStackInSlot(targetSlot - 1).getMaxStackSize();
            } catch (Exception e) { return 0; }
        }, 0, false);
    }

    @LuaFunction(
            value = "指定したスロットのアイテムのNBTデータ情報を取得します。",
            en = "Gets the NBT data information of the item in the specified slot.",
            args = {"str:target", "num:slot"},
            rets = {"table:nbtData"},
            isAsync = false
    )
    public LuaValue getItemData(String targetStr, int slot) {
        return vm.executeInMainThreadSync(() -> {
            try {
                if (getTargetDatabase(targetStr) != null) return LuaValue.NIL;
                IItemHandler targetInv = getTargetInventory(targetStr);
                if (targetInv == null || slot < 1 || slot > targetInv.getSlots()) return LuaValue.NIL;
                ItemStack stack = targetInv.getStackInSlot(slot - 1);
                if (stack.isEmpty()) return LuaValue.NIL;
                Tag tag = stack.saveOptional(Objects.requireNonNull(vm.hardware.getLevel()).registryAccess());
                return vm.loadFromNBT(tag);
            } catch (Exception e) { return LuaValue.NIL; }
        });
    }

    @LuaFunction(
            value = "指定したスロットのアイテムに付与されているタグ一覧をカンマ区切りの文字列で取得します。",
            en = "Gets a comma-separated string of tags attached to the item in the specified slot.",
            args = {"str:target", "num:slot"},
            rets = {"str:tags"},
            isAsync = false
    )
    public String getTags(String targetStr, int targetSlot) {
        return vm.executeInMainThreadSync(() -> {
            try {
                if (getTargetDatabase(targetStr) != null) return "";
                IItemHandler targetInv = getTargetInventory(targetStr);
                if (targetInv == null || targetSlot < 1 || targetSlot > targetInv.getSlots()) return "";
                ItemStack stack = targetInv.getStackInSlot(targetSlot - 1);
                return stack.isEmpty() ? "" : stack.getTags().map(t -> t.location().toString()).collect(Collectors.joining(","));
            } catch (Exception e) { return ""; }
        }, 0, false);
    }

    @LuaFunction(
            value = "ターゲットのインベントリに存在するすべてのアイテムのリスト(名前と個数)を取得します。",
            en = "Gets a list of all items (name and quantity) present in the target's inventory.",
            args = {"str:target"},
            rets = {"table:items"},
            isAsync = false
    )
    public LuaTable listItems(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            LuaTable result = new LuaTable();
            try {
                DatabaseBlockEntity db = getTargetDatabase(targetStr);
                if (db != null) {
                    int i = 1;
                    for (Map.Entry<String, Long> entry : db.itemCounts.entrySet()) {
                        LuaTable item = new LuaTable();
                        item.set("name", db.itemIds.get(entry.getKey()));
                        item.set("count", entry.getValue() > Integer.MAX_VALUE ? Integer.MAX_VALUE : entry.getValue().intValue());
                        result.set(i++, item);
                    }
                    return result;
                }

                IItemHandler targetInv = getTargetInventory(targetStr);
                if (targetInv == null) return result;

                for (int i = 0; i < targetInv.getSlots(); i++) {
                    ItemStack stack = targetInv.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        LuaTable itemInfo = new LuaTable();
                        itemInfo.set("name", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                        itemInfo.set("count", stack.getCount());
                        result.set(i + 1, itemInfo);
                    }
                }
            } catch (Exception ignored) {}
            return result;
        }, 0, false);
    }

    @LuaFunction(
            value = "指定したアイテム名に一致するアイテムをインベントリ内から検索し、合致したスロット情報等のリストを返します。",
            en = "Searches the inventory for an item matching the specified name and returns a list of matching slot information.",
            args = {"any:targetVal", "str:itemName"},
            rets = {"table:results"},
            isAsync = false
    )
    public LuaTable searchItem(LuaValue targetVal, String itemName) {
        return vm.executeInMainThreadSync(() -> {
            LuaTable resultTable = new LuaTable();
            try {
                List<String> targets = new ArrayList<>();
                if (targetVal.istable()) {
                    for (int i = 1; i <= targetVal.checktable().length(); i++) targets.add(targetVal.get(i).tojstring());
                } else if (targetVal.isstring()) {
                    targets.add(targetVal.tojstring());
                }

                int index = 1;
                for (String targetStr : targets) {
                    DatabaseBlockEntity db = getTargetDatabase(targetStr);
                    if (db != null) {
                        int slot = 1;
                        for (Map.Entry<String, String> entry : db.itemIds.entrySet()) {
                            if (entry.getValue().equals(itemName)) {
                                LuaTable match = new LuaTable();
                                match.set("id", targetStr);
                                match.set("slot", slot);
                                long count = db.itemCounts.get(entry.getKey());
                                match.set("count", count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count);
                                resultTable.set(index++, match);
                            }
                            slot++;
                        }
                        continue;
                    }

                    IItemHandler targetInv = getTargetInventory(targetStr);
                    if (targetInv == null) continue;

                    for (int i = 0; i < targetInv.getSlots(); i++) {
                        ItemStack stack = targetInv.getStackInSlot(i);
                        if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemName)) {
                            LuaTable match = new LuaTable();
                            match.set("id", targetStr);
                            match.set("slot", i + 1);
                            match.set("count", stack.getCount());
                            resultTable.set(index++, match);
                        }
                    }
                }
            } catch (Exception ignored) {}
            return resultTable;
        }, 0, false);
    }
}