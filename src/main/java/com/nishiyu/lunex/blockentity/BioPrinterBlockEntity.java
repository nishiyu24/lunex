package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.entity.BioMobGenerator;
import com.nishiyu.lunex.entity.CustomBehaviorRegistry;
import com.nishiyu.lunex.entity.traits.TraitRegistry;
import com.nishiyu.lunex.menu.bioprinter.BioPrinterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BioPrinterBlockEntity extends BlockEntity implements MenuProvider {

    public final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    // ★ 変更箇所：第2引数（最大受け入れ量）を 1000 に制限
    public final EnergyStorage energyStorage = new EnergyStorage(100000, 1000, 100000);

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored();
                case 1 -> currentTotal;
                case 2 -> maxMaterials;
                case 3 -> getMachinePoints();
                case 4 -> getAnimalPoints();
                case 5 -> getMonsterPoints();
                case 6 -> 0;
                case 7 -> (int) (getTotalFailureRate() * 100);
                case 8 -> TraitRegistry.getBaseMaxTraits(materialCounts);
                case 12 -> CustomBehaviorRegistry.getUnlockedBehaviorsMask(materialCounts);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 16;
        }
    };

    private final Map<String, Integer> materialCounts = new HashMap<>();
    private final List<Integer> selectedBehaviors = new ArrayList<>();
    private final List<Integer> selectedTraits = new ArrayList<>();
    private int currentTotal = 0;
    private int maxMaterials = 100;
    private float failureRate = 0.0f;

    public BioPrinterBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.BIO_PRINTER_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BioPrinterBlockEntity entity) {
        if (level.isClientSide) return;

        ItemStack stack = entity.itemHandler.getStackInSlot(0);

        if (!stack.isEmpty()) {
            if (entity.currentTotal < entity.maxMaterials && entity.isValidMaterial(stack)) {
                int consumeCount = Math.min(stack.getCount(), entity.maxMaterials - entity.currentTotal);
                if (consumeCount > 0) {
                    for (int i = 0; i < consumeCount; i++) {
                        ItemStack consumed = stack.copy();
                        consumed.setCount(1);
                        entity.processMaterial(consumed);
                    }
                    stack.shrink(consumeCount);
                    entity.setChanged();
                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private boolean isValidMaterial(ItemStack stack) {
        Item item = stack.getItem();
        if (TraitRegistry.getAllValidMaterials().contains(item)) return true;
        if (CustomBehaviorRegistry.getAllValidMaterials().contains(item)) return true;
        if (BioMobGenerator.STATUS_BONUS_ITEMS.contains(item)) return true;
        return false;
    }

    public boolean hasRequiredBaseMaterials() {
        int bones = BioMobGenerator.getCount(this.materialCounts, Items.BONE);
        int meats = BioMobGenerator.getCount(this.materialCounts, Items.ROTTEN_FLESH)
                + BioMobGenerator.getCount(this.materialCounts, Items.BEEF)
                + BioMobGenerator.getCount(this.materialCounts, Items.PORKCHOP)
                + BioMobGenerator.getCount(this.materialCounts, Items.CHICKEN)
                + BioMobGenerator.getCount(this.materialCounts, Items.MUTTON)
                + BioMobGenerator.getCount(this.materialCounts, Items.RABBIT);

        return bones >= 5 && meats >= 5;
    }

    public float getTotalFailureRate() {
        int activeTraitsCount = 0;
        for (int i = 0; i < TraitRegistry.TRAITS.size(); i++) {
            if (TraitRegistry.isTraitUnlocked(i, this.materialCounts) && this.selectedTraits.contains(i)) {
                activeTraitsCount++;
            }
        }
        float total = this.failureRate + (activeTraitsCount + this.selectedBehaviors.size()) * 0.10f;
        return Math.min(1.0f, Math.max(0.0f, total));
    }

    private void processMaterial(ItemStack stack) {
        Item item = stack.getItem();
        String itemName = BuiltInRegistries.ITEM.getKey(item).toString();
        materialCounts.put(itemName, materialCounts.getOrDefault(itemName, 0) + 1);
        if (stack.is(Items.NETHER_STAR)) this.failureRate += 0.30f;
        if (stack.is(Items.EXPERIENCE_BOTTLE)) this.failureRate -= 0.05f;
        this.currentTotal++;
    }

    public void toggleBehavior(int behaviorId) {
        int unlockedMask = CustomBehaviorRegistry.getUnlockedBehaviorsMask(this.materialCounts);
        if ((unlockedMask & (1 << behaviorId)) == 0) return;

        if (this.selectedBehaviors.contains(behaviorId)) {
            this.selectedBehaviors.remove(Integer.valueOf(behaviorId));
            this.setChanged();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        } else {
            if (behaviorId == 19) {
                this.selectedBehaviors.clear();
                this.selectedBehaviors.add(19);
                this.setChanged();
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
            } else {
                if (this.selectedBehaviors.contains(19)) return;
                if (this.selectedBehaviors.size() < 3) {
                    this.selectedBehaviors.add(behaviorId);
                    this.setChanged();
                    this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    public void toggleTrait(int traitId) {
        if (!TraitRegistry.isTraitUnlocked(traitId, this.materialCounts)) return;

        if (this.selectedTraits.contains(traitId)) {
            List<Integer> tempSelected = new ArrayList<>(this.selectedTraits);
            tempSelected.remove(Integer.valueOf(traitId));

            int newMax = TraitRegistry.getMaxPoints(this.materialCounts, tempSelected);
            int newCost = TraitRegistry.getConsumedPoints(tempSelected);

            if (newCost <= newMax) {
                this.selectedTraits.remove(Integer.valueOf(traitId));
                this.setChanged();
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
            }
        } else {
            List<Integer> tempSelected = new ArrayList<>(this.selectedTraits);
            tempSelected.add(traitId);

            int newMax = TraitRegistry.getMaxPoints(this.materialCounts, tempSelected);
            int newCost = TraitRegistry.getConsumedPoints(tempSelected);

            if (newCost <= newMax) {
                this.selectedTraits.add(traitId);
                this.setChanged();
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    public void forceGenerate() {
        if (this.currentTotal >= this.maxMaterials && this.currentTotal > 0 && this.energyStorage.getEnergyStored() >= 100000 && hasRequiredBaseMaterials()) {
            int extracted = this.energyStorage.extractEnergy(100000, false);
            if (extracted < 100000) {
                return;
            }

            List<Integer> validTraits = new ArrayList<>();
            for (int i = 0; i < TraitRegistry.TRAITS.size(); i++) {
                if (TraitRegistry.isTraitUnlocked(i, this.materialCounts) && this.selectedTraits.contains(i)) {
                    validTraits.add(i);
                }
            }

            BioMobGenerator.generateMob(this.level, this.worldPosition, this.materialCounts, getTotalFailureRate(), this.selectedBehaviors, validTraits);
            resetPrinter();
        }
    }

    public void extractMaterials(Player player) {
        if (this.level == null || this.level.isClientSide) return;
        for (Map.Entry<String, Integer> entry : this.materialCounts.entrySet()) {
            String itemName = entry.getKey();
            int count = entry.getValue();
            net.minecraft.resources.ResourceLocation location = net.minecraft.resources.ResourceLocation.parse(itemName);
            Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(Items.AIR);

            if (item != Items.AIR) {
                while (count > 0) {
                    int stackSize = Math.min(count, item.getDefaultMaxStackSize());
                    ItemStack stackToGive = new ItemStack(item, stackSize);
                    if (!player.getInventory().add(stackToGive)) {
                        player.drop(stackToGive, false);
                    }
                    count -= stackSize;
                }
            }
        }
        resetPrinter();
    }

    public int getMachinePoints() {
        return BioMobGenerator.getMachinePoints(this.materialCounts);
    }

    public int getMonsterPoints() {
        return BioMobGenerator.getMonsterPoints(this.materialCounts);
    }

    public int getAnimalPoints() {
        return BioMobGenerator.getAnimalPoints(this.materialCounts);
    }

    public int getAbilityMask() {
        return BioMobGenerator.getAbilityMask(this.materialCounts);
    }

    public Map<String, Integer> getMaterialCounts() {
        return this.materialCounts;
    }

    public List<Integer> getSelectedBehaviors() {
        return this.selectedBehaviors;
    }

    public List<Integer> getSelectedTraits() {
        return this.selectedTraits;
    }

    private void resetPrinter() {
        this.materialCounts.clear();
        this.currentTotal = 0;
        this.maxMaterials = 100;
        this.failureRate = 0.0f;
        this.selectedBehaviors.clear();
        this.selectedTraits.clear();
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CurrentTotal", this.currentTotal);
        tag.putInt("MaxMaterials", this.maxMaterials);
        tag.putFloat("FailureRate", this.failureRate);
        tag.putInt("Energy", this.energyStorage.getEnergyStored());
        tag.putIntArray("SelectedBehaviors", this.selectedBehaviors);
        tag.putIntArray("SelectedTraits", this.selectedTraits);
        tag.put("Inventory", this.itemHandler.serializeNBT(registries));

        CompoundTag materialsTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : materialCounts.entrySet()) {
            materialsTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("Materials", materialsTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.currentTotal = tag.getInt("CurrentTotal");
        this.maxMaterials = tag.getInt("MaxMaterials");
        this.failureRate = tag.getFloat("FailureRate");

        this.selectedBehaviors.clear();
        if (tag.contains("SelectedBehaviors")) {
            for (int val : tag.getIntArray("SelectedBehaviors")) {
                this.selectedBehaviors.add(val);
            }
        }

        this.selectedTraits.clear();
        if (tag.contains("SelectedTraits")) {
            for (int val : tag.getIntArray("SelectedTraits")) {
                this.selectedTraits.add(val);
            }
        }

        if (tag.contains("Energy")) this.energyStorage.receiveEnergy(tag.getInt("Energy"), false);
        if (tag.contains("Inventory")) this.itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"));

        this.materialCounts.clear();
        if (tag.contains("Materials")) {
            CompoundTag materialsTag = tag.getCompound("Materials");
            for (String key : materialsTag.getAllKeys()) {
                this.materialCounts.put(key, materialsTag.getInt(key));
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Bio Printer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new BioPrinterMenu(id, inv, this, this.dataAccess);
    }
}