package com.nishiyu.lunex.blockentity;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.mcnet.IMCNetDevice;
import com.nishiyu.lunex.recipe.PrinterRecipe;
import com.nishiyu.lunex.recipe.PrinterRecipeInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PrinterBlockEntity extends BlockEntity implements IMCNetDevice {

    public class MyEnergyStorage extends EnergyStorage {
        public MyEnergyStorage(int capacity, int maxReceive, int maxExtract, int energy) {
            super(capacity, maxReceive, maxExtract, energy);
        }
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0) setChanged();
            return received;
        }
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = super.extractEnergy(maxExtract, simulate);
            if (extracted > 0) setChanged();
            return extracted;
        }
        public void setEnergy(int energyIn) {
            this.energy = Math.clamp(energyIn, 0, this.capacity);
        }
    }

    public final MyEnergyStorage energyStorage = new MyEnergyStorage(10000, 1000, 1000, 0);

    public final ItemStackHandler itemHandler = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    public int progress = 0;
    public int maxProgress = 100;

    // 現在進行中のカスタムレシピ（毎ティック検索するのを防ぐキャッシュ）
    private RecipeHolder<PrinterRecipe> currentRecipe = null;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored();
                case 1 -> energyStorage.getMaxEnergyStored();
                case 2 -> progress;
                case 3 -> maxProgress;
                default -> 0;
            };
        }
        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStorage.setEnergy(value);
                case 2 -> progress = value;
                case 3 -> maxProgress = value;
            }
        }
        @Override
        public int getCount() {
            return 4;
        }
    };

    public PrinterBlockEntity(BlockPos pos, BlockState state) {
        super(Lunex.PRINTER_BE.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide()) return;

        boolean hasChanged = false;

        // 出力スロットに空きがない場合は処理しない
        if (!itemHandler.getStackInSlot(3).isEmpty() && itemHandler.getStackInSlot(3).getCount() >= itemHandler.getStackInSlot(3).getMaxStackSize()) {
            if (progress > 0) {
                progress = 0;
                setChanged();
            }
            return;
        }

        PrinterRecipeInput input = new PrinterRecipeInput(itemHandler);

        // 1. まずカスタムレシピ（JSON/KubeJS）に一致するか確認
        Optional<RecipeHolder<PrinterRecipe>> match = level.getRecipeManager().getRecipeFor(PrinterRecipe.Type.INSTANCE, input, level);

        if (match.isPresent()) {
            hasChanged = processCustomRecipe(match.get().value(), input);
        }
        // 2. カスタムレシピがない場合、既存の「エンチャント本のコピー」を判定
        else if (canCopyEnchantmentBook()) {
            int energyPerTick = 10;
            if (energyStorage.getEnergyStored() >= energyPerTick) {
                maxProgress = 100; // コピーの所要時間
                progress++;
                energyStorage.extractEnergy(energyPerTick, false);
                hasChanged = true;

                if (progress >= maxProgress) {
                    performCopyEnchantmentBook();
                    progress = 0;
                }
            }
        }
        // 3. どちらの条件も満たさない場合は進行度をリセット
        else {
            if (progress > 0) {
                progress = 0;
                hasChanged = true;
            }
        }

        if (hasChanged) {
            setChanged();
        }
    }

    // ===============================================
    // カスタムレシピ（JSON/KubeJS）の処理メソッド
    // ===============================================
    private boolean processCustomRecipe(PrinterRecipe recipe, PrinterRecipeInput input) {
        if (energyStorage.getEnergyStored() < recipe.energyPerTick()) {
            return false;
        }

        this.maxProgress = recipe.processingTime();
        this.progress++;
        this.energyStorage.extractEnergy(recipe.energyPerTick(), false);

        if (this.progress >= this.maxProgress) {
            // アイテムの消費
            for (int i = 0; i < recipe.ingredients().size(); i++) {
                for (int slot = 0; slot < 3; slot++) {
                    if (recipe.ingredients().get(i).test(itemHandler.getStackInSlot(slot))) {
                        itemHandler.extractItem(slot, 1, false);
                        break;
                    }
                }
            }

            // アイテムの生成
            ItemStack result = recipe.assemble(input, Objects.requireNonNull(level).registryAccess());
            ItemStack currentOutput = itemHandler.getStackInSlot(3);
            if (currentOutput.isEmpty()) {
                itemHandler.setStackInSlot(3, result);
            } else if (ItemStack.isSameItemSameComponents(currentOutput, result)) {
                currentOutput.grow(result.getCount());
            }

            this.progress = 0;
        }
        return true;
    }

    // ===============================================
    // 以下、既存の特殊処理（JEIではダミー表示する対象）
    // ===============================================

    private boolean hasIngredients(Object... requirements) {
        int[] virtualConsumed = new int[3];
        for (int i = 0; i < requirements.length; i += 2) {
            Item requiredItem = (Item) requirements[i];
            int requiredCount = (Integer) requirements[i + 1];
            int foundCount = 0;

            for (int slot = 0; slot < 3; slot++) {
                ItemStack stack = itemHandler.getStackInSlot(slot);
                if (stack.getItem() == requiredItem) {
                    int available = stack.getCount() - virtualConsumed[slot];
                    if (available > 0) {
                        int take = Math.min(requiredCount - foundCount, available);
                        foundCount += take;
                        virtualConsumed[slot] += take;
                    }
                }
                if (foundCount >= requiredCount) break;
            }
            if (foundCount < requiredCount) return false;
        }
        return true;
    }

    private boolean consumeIngredients(Object... requirements) {
        int[] consumed = new int[3];
        for (int i = 0; i < requirements.length; i += 2) {
            Item requiredItem = (Item) requirements[i];
            int requiredCount = (Integer) requirements[i + 1];
            int foundCount = 0;
            for (int slot = 0; slot < 3; slot++) {
                ItemStack stack = itemHandler.getStackInSlot(slot);
                if (stack.getItem() == requiredItem) {
                    int available = stack.getCount() - consumed[slot];
                    if (available > 0) {
                        int take = Math.min(requiredCount - foundCount, available);
                        foundCount += take;
                        consumed[slot] += take;
                    }
                }
                if (foundCount >= requiredCount) break;
            }
            if (foundCount < requiredCount) return false;
        }
        for (int slot = 0; slot < 3; slot++) {
            if (consumed[slot] > 0) {
                itemHandler.extractItem(slot, consumed[slot], false);
            }
        }
        return true;
    }

    public boolean canCopyEnchantmentBook() {
        if (!itemHandler.getStackInSlot(3).isEmpty()) return false;
        boolean hasOriginal = false;
        for (int i = 0; i < 3; i++) {
            if (itemHandler.getStackInSlot(i).getItem() == Items.ENCHANTED_BOOK) {
                hasOriginal = true;
                break;
            }
        }
        if (!hasOriginal) return false;
        return hasIngredients(Items.INK_SAC, 10, Items.EXPERIENCE_BOTTLE, 10);
    }

    private void performCopyEnchantmentBook() {
        ItemStack originalBook = ItemStack.EMPTY;
        for (int i = 0; i < 3; i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (stack.getItem() == Items.ENCHANTED_BOOK) {
                originalBook = stack;
                break;
            }
        }
        if (consumeIngredients(Items.INK_SAC, 10, Items.EXPERIENCE_BOTTLE, 10)) {
            ItemStack resultBook = new ItemStack(Lunex.INACTIVE_BOOK.get());
            ItemEnchantments enchantments = originalBook.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            resultBook.set(DataComponents.STORED_ENCHANTMENTS, enchantments);
            itemHandler.setStackInSlot(3, resultBook);
        }
    }

    public boolean createDisc(String scriptName, String code) {
        if (!itemHandler.getStackInSlot(3).isEmpty()) return false;
        if (consumeIngredients(Items.IRON_INGOT, 2, Items.REDSTONE, 5, Items.GOLD_INGOT, 1)) {
            ItemStack resultDisc = new ItemStack(Lunex.PROGRAM_DISK.get());
            CustomData.update(DataComponents.CUSTOM_DATA, resultDisc, tag -> {
                tag.putString("ProgramName", scriptName);
                tag.putString("ProgramCode", code);
            });
            itemHandler.setStackInSlot(3, resultDisc);
            return true;
        }
        return false;
    }

    public boolean printBook(String title, String content) {
        if (!itemHandler.getStackInSlot(3).isEmpty()) return false;
        if (consumeIngredients(Items.INK_SAC, 5, Items.BOOK, 1)) {
            ItemStack resultBook = new ItemStack(Items.WRITTEN_BOOK);
            WrittenBookContent bookContent = new WrittenBookContent(
                    Filterable.passThrough(title), "Printer", 0,
                    List.of(Filterable.passThrough(Component.literal(content))), false
            );
            resultBook.set(DataComponents.WRITTEN_BOOK_CONTENT, bookContent);
            itemHandler.setStackInSlot(3, resultBook);
            return true;
        }
        return false;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", itemHandler.serializeNBT(registries));
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.contains("Energy")) energyStorage.setEnergy(tag.getInt("Energy"));
        if (tag.contains("Progress")) progress = tag.getInt("Progress");
        if (tag.contains("MaxProgress")) maxProgress = tag.getInt("MaxProgress");
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}