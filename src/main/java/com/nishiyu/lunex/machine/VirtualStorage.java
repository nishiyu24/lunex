package com.nishiyu.lunex.machine;

import com.nishiyu.lunex.api.mainframe.MainframeConstants;
import com.nishiyu.lunex.blockentity.DatabaseBlockEntity;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class VirtualStorage {
    private final SimpleMachineBlockEntity router;

    private final Map<String, Integer> itemCache = new ConcurrentHashMap<>();
    public final List<LogisticsRule> rules = new CopyOnWriteArrayList<>();

    private final Map<String, List<InventoryNode>> physicalNodesByTag = new ConcurrentHashMap<>();
    private final Map<String, List<DatabaseNode>> digitalNodesByTag = new ConcurrentHashMap<>();
    private final List<InventoryNode> allPhysicalNodes = new CopyOnWriteArrayList<>();
    private final List<DatabaseNode> allDigitalNodes = new CopyOnWriteArrayList<>();

    private final List<BlockPos> scanKeys = new CopyOnWriteArrayList<>();
    private final Map<BlockPos, InventoryNode> inventoryCache = new ConcurrentHashMap<>();
    private final Map<BlockPos, DatabaseNode> databaseCache = new ConcurrentHashMap<>();

    private final LinkedHashSet<LogisticsRule> evaluateQueue = new LinkedHashSet<>();
    private final Map<String, List<LogisticsRule>> tagToRules = new ConcurrentHashMap<>();

    public final CraftingManager crafting = new CraftingManager();

    private int currentScanIndex = 0;
    private static final int SCANS_PER_TICK = 10;
    private static final int RULES_PER_TICK = 10;
    private boolean isEvaluating = false;
    private int tickCount = 0;

    public VirtualStorage(SimpleMachineBlockEntity router) {
        this.router = router;
    }

    public void rebuildNetworkCache(Level level) {
        if (level == null || level.isClientSide) return;
        inventoryCache.clear();
        databaseCache.clear();
        physicalNodesByTag.clear();
        digitalNodesByTag.clear();
        allPhysicalNodes.clear();
        allDigitalNodes.clear();
        scanKeys.clear();
        itemCache.clear();

        List<BlockPos> connectedNodes = com.nishiyu.lunex.mcnet.MCNetUtil.getConnectedDevices(level, router.getBlockPos());
        for (BlockPos p : connectedNodes) {
            addDeviceNode(p, level, true);
        }

        forceFullScan();
        updateRuleIndex();
    }

    public void addDeviceNode(BlockPos devicePos, Level level, boolean isRebuilding) {
        if (level == null || level.isClientSide) return;
        BlockEntity be = level.getBlockEntity(devicePos);
        if (be == null) return;
        removeDeviceNode(devicePos);

        String tag = "";
        CompoundTag data = getBeData(be);
        if (data.contains("NetworkTag")) tag = data.getString("NetworkTag");

        switch (be) {
            case DatabaseBlockEntity db -> {
                DatabaseNode node = new DatabaseNode(devicePos, devicePos, db, tag);
                databaseCache.put(devicePos, node);
                allDigitalNodes.add(node);
                digitalNodesByTag.computeIfAbsent(tag, k -> new CopyOnWriteArrayList<>()).add(node);
                if (!scanKeys.contains(devicePos)) scanKeys.add(devicePos);
                if (!isRebuilding) {
                    if (node.updateDeltaAndCheck(this, false)) wakeUpRulesForTag(tag);
                }
            }
            case ProbeBlockEntity probeBlockEntity -> {
                BlockState state = level.getBlockState(devicePos);
                for (Direction dir : Direction.values()) {
                    if (state.getBlock() instanceof com.nishiyu.lunex.block.ProbeBlock && state.getValue(com.nishiyu.lunex.block.ProbeBlock.getPropertyByDirection(dir))) {
                        BlockPos targetPos = devicePos.relative(dir);
                        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, dir.getOpposite());
                        if (handler != null) {
                            InventoryNode node = new InventoryNode(devicePos, targetPos, handler, tag);
                            inventoryCache.put(targetPos, node);
                            allPhysicalNodes.add(node);
                            physicalNodesByTag.computeIfAbsent(tag, k -> new CopyOnWriteArrayList<>()).add(node);
                            if (!scanKeys.contains(targetPos)) scanKeys.add(targetPos);
                            if (!isRebuilding) {
                                if (node.updateDeltaAndCheck(this, false)) wakeUpRulesForTag(tag);
                            }
                        }
                    }
                }
            }
            case com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity sm when sm.isMainframeMaster -> {
                String mTag = tag;
                if (data.contains("MainframeNetworkTag") && !data.getString("MainframeNetworkTag").isEmpty()) {
                    mTag = data.getString("MainframeNetworkTag");
                }

                IItemHandler smHandler = sm.mainframeStorage;
                if(smHandler != null) {
                    InventoryNode node = new InventoryNode(devicePos, devicePos, smHandler, mTag);
                    inventoryCache.put(devicePos, node);
                    allPhysicalNodes.add(node);
                    physicalNodesByTag.computeIfAbsent(mTag, k -> new CopyOnWriteArrayList<>()).add(node);
                    if (!scanKeys.contains(devicePos)) scanKeys.add(devicePos);
                    if (!isRebuilding) {
                        if (node.updateDeltaAndCheck(this, false)) wakeUpRulesForTag(mTag);
                    }
                }
            }
            default -> {
            }
        }
    }

    public void removeDeviceNode(BlockPos devicePos) {
        List<InventoryNode> toRemoveP = new ArrayList<>();
        for (InventoryNode node : allPhysicalNodes) {
            if (node.devicePos.equals(devicePos)) {
                toRemoveP.add(node);
                if (node.snapshot != null) {
                    for (ItemStack old : node.snapshot) {
                        if (!old.isEmpty()) {
                            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(old.getItem()).toString();
                            modifyCache(id, -old.getCount(), true);
                        }
                    }
                }
            }
        }
        allPhysicalNodes.removeAll(toRemoveP);
        for (InventoryNode node : toRemoveP) {
            inventoryCache.remove(node.pos);
            scanKeys.remove(node.pos);
            List<InventoryNode> list = physicalNodesByTag.get(node.tag);
            if (list != null) list.remove(node);
        }

        List<DatabaseNode> toRemoveD = new ArrayList<>();
        for (DatabaseNode node : allDigitalNodes) {
            if (node.devicePos.equals(devicePos)) {
                toRemoveD.add(node);
                for (Map.Entry<String, Integer> entry : node.snapshot.entrySet()) {
                    modifyCache(entry.getKey(), -entry.getValue(), true);
                }
            }
        }
        allDigitalNodes.removeAll(toRemoveD);
        for (DatabaseNode node : toRemoveD) {
            databaseCache.remove(node.pos);
            scanKeys.remove(node.pos);
            List<DatabaseNode> list = digitalNodesByTag.get(node.tag);
            if (list != null) list.remove(node);
        }
    }

    public void modifyCache(String itemName, int delta, boolean silent) {
        if (delta == 0) return;
        int current = itemCache.getOrDefault(itemName, 0);
        itemCache.put(itemName, Math.max(0, current + delta));
        if (delta > 0 && !silent) {
            crafting.onItemAdded(itemName, delta);
        }
    }

    private void updateRuleIndex() {
        tagToRules.clear();
        for (LogisticsRule rule : rules) {
            addRuleToIndex(rule.sourceTag, rule);
            addRuleToIndex(rule.targetTag, rule);
            addRuleToIndex(rule.condRedstoneTarget, rule);
        }
    }

    private void addRuleToIndex(String tag, LogisticsRule rule) {
        if (tag == null) tag = "";
        tagToRules.computeIfAbsent(tag, k -> new ArrayList<>()).add(rule);
    }

    private void wakeUpRulesForTag(String tag) {
        if (tag == null) return;
        List<LogisticsRule> related = tagToRules.get(tag);
        if (related != null) evaluateQueue.addAll(related);
        List<LogisticsRule> global = tagToRules.get("");
        if (global != null) evaluateQueue.addAll(global);

        crafting.wakeUp();
    }

    public void tick(Level level) {
        if (level == null || level.isClientSide) return;
        tickCount++;

        if (!scanKeys.isEmpty()) {
            for (int i = 0; i < SCANS_PER_TICK; i++) {
                if (currentScanIndex >= scanKeys.size()) currentScanIndex = 0;
                BlockPos pos = scanKeys.get(currentScanIndex);

                InventoryNode invNode = inventoryCache.get(pos);
                if (invNode != null && invNode.updateDeltaAndCheck(this, false)) wakeUpRulesForTag(invNode.tag);

                DatabaseNode dbNode = databaseCache.get(pos);
                if (dbNode != null && dbNode.updateDeltaAndCheck(this, false)) wakeUpRulesForTag(dbNode.tag);

                currentScanIndex++;
            }
        }

        if (tickCount % 40 == 0) {
            evaluateQueue.addAll(rules);
            crafting.wakeUp();
        }

        crafting.tick(this, level);

        if (!evaluateQueue.isEmpty() && !isEvaluating) {
            isEvaluating = true;
            try {
                int evaluated = 0;
                List<LogisticsRule> toRun = new ArrayList<>();
                for (LogisticsRule rule : evaluateQueue) {
                    toRun.add(rule);
                    evaluated++;
                    if (evaluated >= RULES_PER_TICK) break;
                }
                toRun.forEach(evaluateQueue::remove);

                boolean anyMoved = false;
                for (LogisticsRule rule : toRun) {
                    if (evaluateSingleRule(rule, level)) {
                        anyMoved = true;
                        evaluateQueue.add(rule);
                    }
                }

                if (anyMoved) forceUpdateDeltas();
            } finally {
                isEvaluating = false;
            }
        }
    }

    public void forceUpdateDeltas() {
        for (InventoryNode node : allPhysicalNodes) {
            if (node.updateDeltaAndCheck(this, false)) wakeUpRulesForTag(node.tag);
        }
        for (DatabaseNode node : allDigitalNodes) {
            if (node.updateDeltaAndCheck(this, false)) wakeUpRulesForTag(node.tag);
        }
    }

    public void onStorageChanged(Level level) {
        if (level == null || level.isClientSide) return;
        forceUpdateDeltas();
        evaluateQueue.addAll(rules);
        crafting.wakeUp();
    }

    public void forceFullScan() {
        itemCache.clear();
        for (InventoryNode node : allPhysicalNodes) {
            Arrays.fill(node.snapshot, ItemStack.EMPTY);
            node.updateDeltaAndCheck(this, true);
        }
        for (DatabaseNode node : allDigitalNodes) {
            node.snapshot.clear();
            node.updateDeltaAndCheck(this, true);
        }
        currentScanIndex = 0;
    }

    public int getItemCount(String itemName) {
        return itemCache.getOrDefault(itemName, 0);
    }

    public Map<String, Integer> getAllItems() {
        Map<String, Integer> result = new HashMap<>(itemCache);
        for (CraftingPattern pattern : crafting.patterns) {
            for (String outputItem : pattern.outputs.keySet()) {
                result.putIfAbsent(outputItem, 0);
            }
        }
        return result;
    }

    public void addRule(LogisticsRule rule) {
        this.rules.add(rule);
        List<LogisticsRule> sortedList = new ArrayList<>(this.rules);
        Collections.sort(sortedList);
        this.rules.clear();
        this.rules.addAll(sortedList);

        updateRuleIndex();
        if (router.getLevel() != null) {
            onStorageChanged(router.getLevel());
        }
    }

    public void clearRules() {
        this.rules.clear();
        this.evaluateQueue.clear();
        updateRuleIndex();
    }

    private boolean isItemMatch(ItemStack stack, LogisticsRule rule) {
        if (stack.isEmpty()) return false;
        boolean idMatched = false;

        if (rule.itemNames.isEmpty() || rule.itemNames.contains("*")) {
            idMatched = true;
        } else {
            if (!rule.compiledTags.isEmpty()) {
                for (net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tagKey : rule.compiledTags) {
                    if (stack.is(tagKey)) {
                        idMatched = true;
                        break;
                    }
                }
            }
            if (!idMatched && !rule.compiledNames.isEmpty()) {
                String stackName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (rule.compiledNames.contains(stackName)) {
                    idMatched = true;
                }
            }
        }

        if (rule.invertFilter) idMatched = !idMatched;
        if (!idMatched) return false;

        if (rule.compiledNbt != null) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag stackTag = customData != null ? customData.copyTag() : new CompoundTag();
            if (rule.nbtExact) {
                return stackTag.equals(rule.compiledNbt);
            } else {
                return NbtUtils.compareNbt(rule.compiledNbt, stackTag, true);
            }
        }
        return true;
    }

    private IItemHandler getHandlerForNode(InventoryNode node, String sideName, Level level) {
        if (sideName == null || sideName.isEmpty() || level == null) return node.handler;
        for (Direction dir : Direction.values()) {
            if (dir.getName().equalsIgnoreCase(sideName)) {
                return level.getCapability(Capabilities.ItemHandler.BLOCK, node.pos, dir);
            }
        }
        return node.handler;
    }

    private ItemStack insertIntoSlots(IItemHandler handler, ItemStack stack, boolean simulate, LogisticsRule rule) {
        if (rule.insertSlotStart < 0 && rule.insertSlotEnd < 0) return ItemHandlerHelper.insertItem(handler, stack, simulate);
        int s = Math.max(0, rule.insertSlotStart);
        int e = Math.min(handler.getSlots() - 1, rule.insertSlotEnd < 0 ? handler.getSlots() - 1 : rule.insertSlotEnd);
        ItemStack remaining = stack.copy();
        for (int i = s; i <= e; i++) {
            if (remaining.isEmpty()) break;
            remaining = handler.insertItem(i, remaining, simulate);
        }
        return remaining;
    }

    public boolean pushToTag(String tag, String itemName, int amount, Level level) {
        int remaining = amount;
        boolean success = false;
        List<InventoryNode> targetPhysicalNodes = physicalNodesByTag.getOrDefault(tag, Collections.emptyList());

        List<DatabaseNode> targetDigitalNodes = new ArrayList<>(digitalNodesByTag.getOrDefault(tag, Collections.emptyList()));
        targetDigitalNodes.sort((a, b) -> Integer.compare(b.db.getPersistentData().getInt("Priority"), a.db.getPersistentData().getInt("Priority")));

        if (targetPhysicalNodes.isEmpty() && targetDigitalNodes.isEmpty()) return false;

        for (DatabaseNode srcNode : allDigitalNodes) {
            if (remaining <= 0) break;
            ItemStack extractedSim = srcNode.db.extractItem(itemName, remaining, true);
            if (!extractedSim.isEmpty()) {
                int extractable = extractedSim.getCount();
                for (DatabaseNode targetNode : targetDigitalNodes) {
                    if (srcNode.pos.equals(targetNode.pos)) continue;
                    ItemStack leftover = targetNode.db.insertItem(extractedSim.copy(), true);
                    int accepted = extractedSim.getCount() - leftover.getCount();
                    if (accepted > 0) {
                        targetNode.db.insertItem(srcNode.db.extractItem(itemName, accepted, false), false);
                        remaining -= accepted;
                        extractable -= accepted;
                        success = true;
                    }
                    if (extractable <= 0) break;
                }
                if (extractable > 0) {
                    for (InventoryNode targetNode : targetPhysicalNodes) {
                        ItemStack leftover = ItemHandlerHelper.insertItem(targetNode.handler, extractedSim.copy(), true);
                        int accepted = extractedSim.getCount() - leftover.getCount();
                        if (accepted > 0) {
                            ItemHandlerHelper.insertItem(targetNode.handler, srcNode.db.extractItem(itemName, accepted, false), false);
                            remaining -= accepted;
                            extractable -= accepted;
                            success = true;
                        }
                        if (extractable <= 0) break;
                    }
                }
            }
        }
        if (remaining > 0) {
            for (InventoryNode srcNode : allPhysicalNodes) {
                IItemHandler src = srcNode.handler;
                for (int i = 0; i < src.getSlots(); i++) {
                    ItemStack stack = src.getStackInSlot(i);
                    if (!stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemName)) {
                        int extractable = src.extractItem(i, remaining, true).getCount();
                        if (extractable > 0) {
                            ItemStack extractedSim = src.extractItem(i, extractable, true);
                            for (DatabaseNode targetNode : targetDigitalNodes) {
                                ItemStack leftover = targetNode.db.insertItem(extractedSim.copy(), true);
                                int accepted = extractedSim.getCount() - leftover.getCount();
                                if (accepted > 0) {
                                    targetNode.db.insertItem(src.extractItem(i, accepted, false), false);
                                    remaining -= accepted;
                                    extractable -= accepted;
                                    success = true;
                                    extractedSim.setCount(leftover.getCount());
                                }
                                if (extractable <= 0) break;
                            }
                            if (extractable > 0) {
                                for (InventoryNode targetNode : targetPhysicalNodes) {
                                    if (srcNode.pos.equals(targetNode.pos)) continue;
                                    ItemStack leftover = ItemHandlerHelper.insertItem(targetNode.handler, extractedSim.copy(), true);
                                    int accepted = extractedSim.getCount() - leftover.getCount();
                                    if (accepted > 0) {
                                        ItemHandlerHelper.insertItem(targetNode.handler, src.extractItem(i, accepted, false), false);
                                        remaining -= accepted;
                                        extractable -= accepted;
                                        success = true;
                                    }
                                    if (extractable <= 0 || remaining <= 0) break;
                                }
                            }
                        }
                    }
                    if (remaining <= 0) break;
                }
                if (remaining <= 0) break;
            }
        }
        return success;
    }

    private static class DestWrapper {
        DatabaseNode dNode;
        InventoryNode pNode;
        IItemHandler handler;
        boolean isFullCache = false;

        DestWrapper(DatabaseNode d) { this.dNode = d; }
        DestWrapper(InventoryNode p, IItemHandler h) { this.pNode = p; this.handler = h; }

        int getPriority() {
            if (dNode != null) return dNode.db.getPersistentData().getInt("Priority");
            return 0;
        }

        double getFullness(LogisticsRule rule) {
            if (dNode != null) return 0.5;
            if (handler == null) return 1.0;
            int total = 0, filled = 0;
            int s = Math.max(rule.insertSlotStart, 0);
            int e = Math.min(handler.getSlots() - 1, rule.insertSlotEnd < 0 ? handler.getSlots() - 1 : rule.insertSlotEnd);
            for(int i = s; i <= e; i++) {
                total += handler.getSlotLimit(i);
                filled += handler.getStackInSlot(i).getCount();
            }
            return total == 0 ? 1.0 : (double) filled / total;
        }
    }

    public int executeRuleTransfer(LogisticsRule rule, Level level) {
        int remaining = rule.batchSize;
        int totalMoved = 0;

        List<InventoryNode> srcPhysical = (rule.type.equals("export") || rule.sourceTag == null || rule.sourceTag.isEmpty()) ? allPhysicalNodes : physicalNodesByTag.getOrDefault(rule.sourceTag, Collections.emptyList());
        List<DatabaseNode> srcDigital = (rule.type.equals("export") || rule.sourceTag == null || rule.sourceTag.isEmpty()) ? allDigitalNodes : digitalNodesByTag.getOrDefault(rule.sourceTag, Collections.emptyList());

        List<DestWrapper> validDsts = new ArrayList<>();
        List<DatabaseNode> dstDigitals = (rule.targetTag == null || rule.targetTag.isEmpty()) ? allDigitalNodes : digitalNodesByTag.getOrDefault(rule.targetTag, Collections.emptyList());
        for (DatabaseNode node : dstDigitals) validDsts.add(new DestWrapper(node));

        List<InventoryNode> dstPhysicals = (rule.targetTag == null || rule.targetTag.isEmpty()) ? allPhysicalNodes : physicalNodesByTag.getOrDefault(rule.targetTag, Collections.emptyList());
        for (InventoryNode node : dstPhysicals) {
            IItemHandler h = getHandlerForNode(node, rule.insertSide, level);
            if (h != null) validDsts.add(new DestWrapper(node, h));
        }

        if ((srcPhysical.isEmpty() && srcDigital.isEmpty()) || validDsts.isEmpty()) return 0;

        if ("random".equals(rule.distribution)) Collections.shuffle(validDsts);
        else if ("least_full".equals(rule.distribution)) validDsts.sort(Comparator.comparingDouble(w -> w.getFullness(rule)));
        else if ("round_robin".equals(rule.distribution)) {
            rule.rrIndex = rule.rrIndex % validDsts.size();
            Collections.rotate(validDsts, -rule.rrIndex);
        } else {
            validDsts.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        }

        for (DatabaseNode srcNode : srcDigital) {
            if (remaining <= 0) break;
            for (String targetItem : new ArrayList<>(srcNode.db.itemIds.values())) {
                if (remaining <= 0) break;

                ItemStack extractedSim = srcNode.db.extractItem(targetItem, remaining, true);
                if (extractedSim.isEmpty() || !isItemMatch(extractedSim, rule)) continue;

                int extractable = extractedSim.getCount();
                for (DestWrapper dst : validDsts) {
                    if (dst.isFullCache) continue;
                    if (dst.dNode != null && srcNode.pos.equals(dst.dNode.pos)) continue;

                    ItemStack leftover = (dst.dNode != null) ? dst.dNode.db.insertItem(extractedSim.copy(), true) : insertIntoSlots(dst.handler, extractedSim.copy(), true, rule);
                    int accepted = extractedSim.getCount() - leftover.getCount();

                    if (accepted == 0) {
                        dst.isFullCache = true;
                        continue;
                    }

                    ItemStack actual = srcNode.db.extractItem(targetItem, accepted, false);
                    if (dst.dNode != null) dst.dNode.db.insertItem(actual, false);
                    else insertIntoSlots(dst.handler, actual, false, rule);

                    remaining -= accepted;
                    extractable -= accepted;
                    totalMoved += accepted;
                    extractedSim.setCount(leftover.getCount());

                    if ("round_robin".equals(rule.distribution)) rule.rrIndex++;
                    if (extractable <= 0) break;
                }
            }
        }

        if (remaining > 0) {
            for (InventoryNode srcNode : srcPhysical) {
                IItemHandler src = getHandlerForNode(srcNode, rule.extractSide, level);
                if (src == null) continue;

                int s = Math.max(rule.extractSlotStart, 0);
                int e = Math.min(src.getSlots() - 1, rule.extractSlotEnd < 0 ? src.getSlots() - 1 : rule.extractSlotEnd);

                for (int i = s; i <= e; i++) {
                    if (remaining <= 0) break;
                    ItemStack stack = src.getStackInSlot(i);
                    if (stack.isEmpty() || !isItemMatch(stack, rule)) continue;

                    int extractable = src.extractItem(i, remaining, true).getCount();
                    if (extractable > 0) {
                        ItemStack extractedSim = src.extractItem(i, extractable, true);

                        for (DestWrapper dst : validDsts) {
                            if (dst.isFullCache) continue;
                            if (dst.pNode != null && srcNode.pos.equals(dst.pNode.pos)) continue;

                            ItemStack leftover = (dst.dNode != null) ? dst.dNode.db.insertItem(extractedSim.copy(), true) : insertIntoSlots(dst.handler, extractedSim.copy(), true, rule);
                            int accepted = extractedSim.getCount() - leftover.getCount();

                            if (accepted == 0) {
                                dst.isFullCache = true;
                                continue;
                            }

                            ItemStack actual = src.extractItem(i, accepted, false);
                            if (dst.dNode != null) dst.dNode.db.insertItem(actual, false);
                            else insertIntoSlots(dst.handler, actual, false, rule);

                            remaining -= accepted;
                            extractable -= accepted;
                            totalMoved += accepted;
                            extractedSim.setCount(leftover.getCount());

                            if ("round_robin".equals(rule.distribution)) rule.rrIndex++;
                            if (extractable <= 0) break;
                        }
                    }
                }
            }
        }
        return totalMoved;
    }

    private boolean checkCondition(int current, String op, int target) {
        return switch(op) {
            case "<" -> current < target;
            case ">" -> current > target;
            case "<=" -> current <= target;
            case ">=" -> current >= target;
            case "==" -> current == target;
            case "!=" -> current != target;
            default -> true;
        };
    }

    private int getRedstonePowerForTag(String tag, Level level) {
        int maxPower = 0;
        List<InventoryNode> nodes = physicalNodesByTag.get(tag);
        if (nodes != null) {
            for (InventoryNode node : nodes) {
                maxPower = Math.max(maxPower, level.getBestNeighborSignal(node.pos));
            }
        }
        return maxPower;
    }

    private boolean evaluateSingleRule(LogisticsRule rule, Level level) {
        if (rule.condItem != null && !rule.condItem.isEmpty()) {
            int currentCount = getItemCount(rule.condItem);
            if (!checkCondition(currentCount, rule.condOp, rule.condAmount)) return false;
        }

        if (rule.condRedstoneTarget != null && !rule.condRedstoneTarget.isEmpty()) {
            int currentPower = getRedstonePowerForTag(rule.condRedstoneTarget, level);
            if (!checkCondition(currentPower, rule.condRedstoneOp, rule.condRedstonePower)) return false;
        }

        if ("export".equals(rule.type) || "transfer".equals(rule.type)) {
            return executeRuleTransfer(rule, level) > 0;
        }
        return false;
    }

    private CompoundTag getBeData(BlockEntity be) {
        if (be instanceof DatabaseBlockEntity db) return db.getPersistentData();
        return be.getPersistentData();
    }

    public static class CraftingPattern {
        public Map<String, Integer> inputs = new HashMap<>();
        public Map<String, Integer> outputs = new HashMap<>();
        public String targetTag = "";
        public String insertSide = "";
    }

    public enum JobState { PENDING, CALCULATING, WAITING_FOR_INGREDIENTS, PROCESSING, DONE, FAILED }

    public static class CraftingJob {
        public int id;
        public String requestItem;
        public int requestAmount;
        public int completedAmount = 0;
        public JobState state = JobState.PENDING;
        public Map<String, Integer> missingIngredients = new HashMap<>();
        public Map<String, Integer> activeProcessing = new HashMap<>();
        public int ticksSinceDone = 0;
    }

    public static class CraftingManager {
        public final List<CraftingPattern> patterns = new ArrayList<>();
        private final Map<Integer, CraftingJob> jobs = new ConcurrentHashMap<>();
        private boolean asleep = false;

        public void addPattern(CraftingPattern pattern) {
            patterns.add(pattern);
            wakeUp();
        }

        public int requestCraft(String item, int amount, VirtualStorage storage) {
            CraftingJob job = new CraftingJob();
            int newId = 1;
            while (jobs.containsKey(newId)) {
                newId++;
            }
            job.id = newId;

            job.requestItem = item;
            job.requestAmount = amount;
            jobs.put(job.id, job);
            System.out.println("[Lunex Crafting] ジョブ登録 ID: " + job.id + " アイテム: " + item + " x" + amount);
            wakeUp();
            return job.id;
        }

        public CraftingJob getJob(int id) {
            return jobs.get(id);
        }

        public List<CraftingJob> getJobs() {
            return new ArrayList<>(jobs.values());
        }

        public void wakeUp() {
            this.asleep = false;
        }

        public void onItemAdded(String item, int amount) {
            for (CraftingJob job : jobs.values()) {
                if (job.state == JobState.PROCESSING && job.requestItem.equals(item)) {
                    int needed = job.requestAmount - job.completedAmount;
                    int take = Math.min(amount, needed);
                    job.completedAmount += take;
                    amount -= take;

                    System.out.println("[Lunex Crafting] アイテム検知 ID: " + job.id + " " + item + " +" + take + " (進捗: " + job.completedAmount + "/" + job.requestAmount + ")");

                    if (job.completedAmount >= job.requestAmount) {
                        job.state = JobState.DONE;
                        System.out.println("[Lunex Crafting] ジョブ完了 ID: " + job.id);
                    }
                    if (amount <= 0) break;
                }
            }
        }

        public void tick(VirtualStorage storage, Level level) {
            if (asleep || jobs.isEmpty()) return;
            boolean anyActive = false;

            for (CraftingJob job : jobs.values()) {
                if (job.state == JobState.DONE || job.state == JobState.FAILED) {
                    job.ticksSinceDone++;
                    if (job.ticksSinceDone > 100) {
                        System.out.println("[Lunex Crafting] ジョブ削除 (猶予経過) ID: " + job.id);
                        jobs.remove(job.id);
                    }
                    continue;
                }

                anyActive = true;

                if (job.state == JobState.PENDING) {
                    System.out.println("[Lunex Crafting] ジョブ計算開始 ID: " + job.id);
                    job.state = JobState.CALCULATING;
                    Map<String, Integer> needed = new HashMap<>();
                    needed.put(job.requestItem, job.requestAmount);

                    Map<String, Integer> virtualInv = new HashMap<>(storage.getAllItems());
                    calculateDependencies(needed, virtualInv, job.missingIngredients);

                    job.state = job.missingIngredients.isEmpty() ? JobState.PROCESSING : JobState.WAITING_FOR_INGREDIENTS;
                    System.out.println("[Lunex Crafting] ジョブ状態変更 ID: " + job.id + " -> " + job.state);
                    if (job.state == JobState.WAITING_FOR_INGREDIENTS) {
                        System.out.println("[Lunex Crafting] 不足アイテム ID: " + job.id + " " + job.missingIngredients);
                    }
                }

                if (job.state == JobState.WAITING_FOR_INGREDIENTS) {
                    boolean allMet = true;
                    Map<String, Integer> currentMissing = new HashMap<>();
                    for (Map.Entry<String, Integer> req : job.missingIngredients.entrySet()) {
                        int have = storage.getItemCount(req.getKey());
                        if (have < req.getValue()) {
                            allMet = false;
                            currentMissing.put(req.getKey(), req.getValue() - have);
                        }
                    }
                    job.missingIngredients = currentMissing;
                    if (allMet) {
                        job.state = JobState.PROCESSING;
                        System.out.println("[Lunex Crafting] ジョブ状態変更 ID: " + job.id + " -> PROCESSING (素材が揃いました)");
                    }
                }

                if (job.state == JobState.PROCESSING) {
                    CraftingPattern pattern = findPatternFor(job.requestItem);
                    if (pattern != null) {
                        for (Map.Entry<String, Integer> input : pattern.inputs.entrySet()) {
                            int totalCraftsNeeded = (int) Math.ceil((double) job.requestAmount / pattern.outputs.get(job.requestItem));
                            int neededToPush = totalCraftsNeeded * input.getValue();
                            int alreadyPushed = job.activeProcessing.getOrDefault(input.getKey(), 0);

                            if (alreadyPushed < neededToPush) {
                                int toPush = Math.min(64, neededToPush - alreadyPushed);

                                LogisticsRule tempRule = new LogisticsRule();
                                tempRule.type = "transfer";
                                tempRule.sourceTag = "";
                                tempRule.targetTag = pattern.targetTag;
                                tempRule.insertSide = pattern.insertSide;
                                tempRule.itemNames.add(input.getKey());
                                tempRule.batchSize = toPush;
                                tempRule.compile();

                                int moved = storage.executeRuleTransfer(tempRule, level);
                                if (moved > 0) {
                                    job.activeProcessing.put(input.getKey(), alreadyPushed + moved);
                                    storage.forceUpdateDeltas();
                                    System.out.println("[Lunex Crafting] 素材投入 ID: " + job.id + " " + input.getKey() + " を " + moved + " 個投入しました");
                                }
                            }
                        }
                    } else {
                        System.out.println("[Lunex Crafting] エラー: レシピが見つかりません ID: " + job.id + " " + job.requestItem);
                        job.state = JobState.FAILED;
                    }
                }
            }

            if (!anyActive) this.asleep = true;
        }

        private void calculateDependencies(Map<String, Integer> required, Map<String, Integer> virtualInv, Map<String, Integer> totalMissing) {
            for (Map.Entry<String, Integer> req : required.entrySet()) {
                String item = req.getKey();
                int amtNeeded = req.getValue();

                int inStock = virtualInv.getOrDefault(item, 0);
                if (inStock >= amtNeeded) {
                    virtualInv.put(item, inStock - amtNeeded);
                    continue;
                }

                int deficit = amtNeeded - inStock;
                virtualInv.put(item, 0);

                CraftingPattern p = findPatternFor(item);
                if (p != null) {
                    int craftsNeeded = (int) Math.ceil((double) deficit / p.outputs.get(item));
                    Map<String, Integer> nextRequirements = new HashMap<>();
                    for (Map.Entry<String, Integer> in : p.inputs.entrySet()) {
                        nextRequirements.put(in.getKey(), in.getValue() * craftsNeeded);
                    }
                    calculateDependencies(nextRequirements, virtualInv, totalMissing);
                } else {
                    totalMissing.put(item, totalMissing.getOrDefault(item, 0) + deficit);
                }
            }
        }

        private CraftingPattern findPatternFor(String item) {
            for (CraftingPattern p : patterns) {
                if (p.outputs.containsKey(item)) return p;
            }
            return null;
        }
    }

    public static class LogisticsRule implements Comparable<LogisticsRule> {
        public String type = "export";
        public String sourceTag = "";
        public String targetTag = "";
        public String extractSide = "";
        public String insertSide = "";
        public int extractSlotStart = -1;
        public int extractSlotEnd = -1;
        public int insertSlotStart = -1;
        public int insertSlotEnd = -1;
        public List<String> itemNames = new ArrayList<>();
        public boolean invertFilter = false;
        public String distribution = "default";
        public int rrIndex = 0;
        public int batchSize = 64;
        public int priority = 0;
        public String nbtJson = "";
        public boolean nbtExact = false;
        public String condItem = null;
        public String condOp = "";
        public int condAmount = 0;
        public String condRedstoneTarget = null;
        public String condRedstoneOp = "";
        public int condRedstonePower = 0;

        public final List<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> compiledTags = new ArrayList<>();
        public final List<String> compiledNames = new ArrayList<>();
        public CompoundTag compiledNbt = null;

        public void compile() {
            compiledTags.clear();
            compiledNames.clear();
            for (String name : itemNames) {
                if (name.startsWith("#")) {
                    compiledTags.add(ItemTags.create(ResourceLocation.parse(name.substring(1))));
                } else if (!name.equals("*")) {
                    compiledNames.add(name);
                }
            }
            if (nbtJson != null && !nbtJson.isEmpty()) {
                try {
                    compiledNbt = TagParser.parseTag(nbtJson);
                } catch (Exception ignored) {}
            }
        }

        @Override
        public int compareTo(LogisticsRule other) {
            return Integer.compare(other.priority, this.priority);
        }
    }

    public static class InventoryNode {
        public final BlockPos devicePos;
        public final BlockPos pos;
        public final IItemHandler handler;
        public final String tag;
        private ItemStack[] snapshot;

        public InventoryNode(BlockPos devicePos, BlockPos pos, IItemHandler handler, String tag) {
            this.devicePos = devicePos;
            this.pos = pos;
            this.handler = handler;
            this.tag = tag;
            this.snapshot = new ItemStack[handler.getSlots()];
            for (int i = 0; i < handler.getSlots(); i++) {
                this.snapshot[i] = ItemStack.EMPTY;
            }
        }

        public boolean updateDeltaAndCheck(VirtualStorage storage, boolean silent) {
            boolean changed = false;
            int slots = handler.getSlots();

            if (snapshot.length != slots) {
                for (ItemStack old : snapshot) {
                    if (!old.isEmpty()) {
                        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(old.getItem()).toString();
                        storage.modifyCache(id, -old.getCount(), silent);
                    }
                }
                snapshot = new ItemStack[slots];
                for (int i = 0; i < slots; i++) snapshot[i] = ItemStack.EMPTY;
                changed = true;
            }

            Map<String, Integer> netDeltas = new HashMap<>();
            for (int i = 0; i < slots; i++) {
                ItemStack current = handler.getStackInSlot(i);
                ItemStack old = snapshot[i];
                if (!ItemStack.matches(old, current) || old.getCount() != current.getCount()) {
                    changed = true;
                    if (!old.isEmpty()) {
                        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(old.getItem()).toString();
                        netDeltas.put(id, netDeltas.getOrDefault(id, 0) - old.getCount());
                    }
                    if (!current.isEmpty()) {
                        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(current.getItem()).toString();
                        netDeltas.put(id, netDeltas.getOrDefault(id, 0) + current.getCount());
                    }
                    snapshot[i] = current.copy();
                }
            }
            if (changed) {
                for (Map.Entry<String, Integer> entry : netDeltas.entrySet()) {
                    storage.modifyCache(entry.getKey(), entry.getValue(), silent);
                }
            }
            return changed;
        }
    }

    public static class DatabaseNode {
        public final BlockPos devicePos;
        public final BlockPos pos;
        public final DatabaseBlockEntity db;
        public final String tag;
        private final Map<String, Integer> snapshot = new HashMap<>();

        public DatabaseNode(BlockPos devicePos, BlockPos pos, DatabaseBlockEntity db, String tag) {
            this.devicePos = devicePos;
            this.pos = pos;
            this.db = db;
            this.tag = tag;
        }

        public boolean updateDeltaAndCheck(VirtualStorage storage, boolean silent) {
            Map<String, Integer> currentCounts = new HashMap<>();
            for (Map.Entry<String, String> entry : db.itemIds.entrySet()) {
                long count = db.itemCounts.getOrDefault(entry.getKey(), 0L);
                if (count > 0) currentCounts.put(entry.getValue(), (int) count);
            }

            if (!snapshot.equals(currentCounts)) {
                Map<String, Integer> netDeltas = new HashMap<>();
                for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
                    netDeltas.put(entry.getKey(), netDeltas.getOrDefault(entry.getKey(), 0) - entry.getValue());
                }
                for (Map.Entry<String, Integer> entry : currentCounts.entrySet()) {
                    netDeltas.put(entry.getKey(), netDeltas.getOrDefault(entry.getKey(), 0) + entry.getValue());
                }
                for (Map.Entry<String, Integer> entry : netDeltas.entrySet()) {
                    storage.modifyCache(entry.getKey(), entry.getValue(), silent);
                }
                snapshot.clear();
                snapshot.putAll(currentCounts);
                return true;
            }
            return false;
        }
    }
}