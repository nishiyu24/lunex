package com.nishiyu.lunex.api.client.ui.extensions;

import com.nishiyu.lunex.api.client.IMainframeUIExtension;
import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

public class CrafterUIExtension implements IMainframeUIExtension {

    @Override
    public int getPanelHeight(BlockEntity be) {
        return 190; // ★変更: 検索ボタンを削除したため高さを元に調整
    }

    @Override
    public void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, BlockEntity be, int panelX, int textY, Consumer<AbstractWidget> addWidget) {
        CompoundTag recipeTag = be.getPersistentData().getCompound("CrafterRecipe");
        boolean hasResult = !recipeTag.getString("Slot_9").isEmpty();
        boolean isActive = be.getPersistentData().getBoolean("AutoCraftActive");

        Button activeBtn = Button.builder(Component.literal("Auto: " + (isActive ? "ON" : "OFF")), btn -> {
            boolean next = !be.getPersistentData().getBoolean("AutoCraftActive");
            be.getPersistentData().putBoolean("AutoCraftActive", next);
            btn.setMessage(Component.literal("Auto: " + (next ? "ON" : "OFF")));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_autocraft", ""));
        }).bounds(panelX + 5, textY + 15, 65, 20).build();

        activeBtn.active = hasResult;
        addWidget.accept(activeBtn);

        Button craftBtn = Button.builder(Component.literal("Craft"), btn -> {
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "force_craft", ""));
        }).bounds(panelX + 75, textY + 15, 60, 20).build();

        craftBtn.active = hasResult;
        addWidget.accept(craftBtn);

        int gridX = panelX + 15;
        int gridY = textY + 65;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                addWidget.accept(new GhostSlotWidget(gridX + col * 18, gridY + row * 18, index, pos, be, true, activeBtn, craftBtn));
            }
        }

        addWidget.accept(new GhostSlotWidget(gridX + 80, gridY + 18, 9, pos, be, false, activeBtn, craftBtn));

        // ★変更: ここにあった searchBtn (JEI呼び出しボタン) は不要になったため削除
    }

    @Override
    public void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, BlockEntity be, int panelX, int textY) {
        guiGraphics.drawString(font, "Crafter Settings:", panelX + 5, textY, 0x00E5FF);
        guiGraphics.drawString(font, "Recipe (Ghost):", panelX + 5, textY + 53, 0xFFFFFF);
        guiGraphics.drawString(font, "->", panelX + 70, textY + 85, 0xAAAAAA);
    }

    public static class GhostSlotWidget extends AbstractWidget {
        private final int slotIndex;
        private final BlockPos blockPos;
        private final BlockEntity be;
        private final boolean isEditable;
        private final Button activeBtn;
        private final Button craftBtn;

        private ItemStack displayStack = ItemStack.EMPTY;
        private String lastItemId = "";

        private boolean lastActiveState;
        private boolean lastHasResult;

        public GhostSlotWidget(int x, int y, int slotIndex, BlockPos pos, BlockEntity be, boolean isEditable, Button activeBtn, Button craftBtn) {
            super(x, y, 16, 16, Component.empty());
            this.slotIndex = slotIndex;
            this.blockPos = pos;
            this.be = be;
            this.isEditable = isEditable;
            this.activeBtn = activeBtn;
            this.craftBtn = craftBtn;

            this.lastActiveState = be.getPersistentData().getBoolean("AutoCraftActive");
            this.lastHasResult = !be.getPersistentData().getCompound("CrafterRecipe").getString("Slot_9").isEmpty();

            updateDisplayStack();
        }

        private void updateDisplayStack() {
            CompoundTag recipeTag = be.getPersistentData().getCompound("CrafterRecipe");
            String itemId = recipeTag.getString("Slot_" + slotIndex);

            int count = (this.slotIndex == 9 && recipeTag.contains("ResultCount")) ? recipeTag.getInt("ResultCount") : 1;

            this.lastItemId = itemId;
            if (!itemId.isEmpty()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                if (item != Items.AIR) {
                    this.displayStack = new ItemStack(item, count);
                }
            } else {
                this.displayStack = ItemStack.EMPTY;
            }
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            CompoundTag recipeTag = be.getPersistentData().getCompound("CrafterRecipe");
            if (!recipeTag.getString("Slot_" + slotIndex).equals(this.lastItemId)) {
                updateDisplayStack();
            }

            boolean currentActive = be.getPersistentData().getBoolean("AutoCraftActive");
            boolean hasResult = !recipeTag.getString("Slot_9").isEmpty();

            if (this.activeBtn != null && this.slotIndex == 9) {
                if (currentActive != this.lastActiveState) {
                    this.activeBtn.setMessage(Component.literal("Auto: " + (currentActive ? "ON" : "OFF")));
                    this.lastActiveState = currentActive;
                }

                if (hasResult != this.lastHasResult) {
                    this.activeBtn.active = hasResult;
                    if (this.craftBtn != null) this.craftBtn.active = hasResult;
                    this.lastHasResult = hasResult;
                }
            }

            boolean canEdit = isEditable && !currentActive;

            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, 0x88333333);
            guiGraphics.renderOutline(getX() - 1, getY() - 1, width + 2, height + 2, (isHovered() && canEdit) ? 0xFF00E5FF : 0xFF555555);

            if (!displayStack.isEmpty()) {
                guiGraphics.renderItem(displayStack, getX(), getY());

                if (displayStack.getCount() > 1) {
                    guiGraphics.renderItemDecorations(Minecraft.getInstance().font, displayStack, getX(), getY());
                }

                guiGraphics.fill(getX(), getY(), getX() + 16, getY() + 16, 0x40000000);
            }

            if (isHovered() && canEdit) {
                guiGraphics.fill(getX(), getY(), getX() + 16, getY() + 16, 0x80FFFFFF);
            }
        }

        public void acceptDrop(ItemStack stack) {
            if (!isEditable || be.getPersistentData().getBoolean("AutoCraftActive")) return;

            String itemId = "";
            if (!stack.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                itemId = id.toString();
            }

            CompoundTag recipeTag = be.getPersistentData().getCompound("CrafterRecipe");
            if (itemId.isEmpty()) {
                recipeTag.remove("Slot_" + slotIndex);
            } else {
                recipeTag.putString("Slot_" + slotIndex, itemId);
            }
            be.getPersistentData().put("CrafterRecipe", recipeTag);

            String payload = slotIndex + ":" + itemId;
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(blockPos, "set_crafter_recipe", payload));

            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isEditable || !isHovered() || be.getPersistentData().getBoolean("AutoCraftActive")) return false;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ItemStack carried = mc.player.containerMenu.getCarried();
                if (button == 1) {
                    acceptDrop(ItemStack.EMPTY);
                } else if (!carried.isEmpty()) {
                    acceptDrop(carried);
                } else {
                    return true;
                }
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
    }
}