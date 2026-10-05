package com.nishiyu.lunex.api.client.ui.panel;

import com.nishiyu.lunex.api.client.MainframeUIRegistry;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

public class FurnaceUIExtension extends AbstractRightPanel {

    public FurnaceUIExtension(BlockPos pos, BlockEntity be) {
        super(pos, be);
        this.maxScroll = 130;

        CompoundTag data = be.getPersistentData();
        boolean isActive = data.getBoolean("AutoSmeltActive");

        addWidget(new AutoToggleButton(0, 0, 65, 20, isActive, be, pos), 5, 65);

        int gridX = 15;
        int gridY = 118;
        addWidget(new TargetSlotWidget(0, 0, pos, be, true, "SmeltTarget"), gridX, gridY);
        addWidget(new TargetSlotWidget(0, 0, pos, be, false, "SmeltResult"), gridX + 50, gridY);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        Component displayName = MainframeUIRegistry.getDisplayBlockName(be);

        graphics.drawString(font, "Target: " + displayName.getString(), startX + 10, startY + 10, 0xFFD4D4D4);
        graphics.drawString(font, "Pos: " + pos.toShortString(), startX + 10, startY + 22, 0xFF4EC9B0);

        graphics.drawString(font, "Furnace Settings:", startX + 5, startY + 50, 0x00E5FF);
        graphics.drawString(font, "Target Item:", startX + 5, startY + 103, 0xFFFFFF);

        CompoundTag data = be.getPersistentData();
        int progress = data.getInt("CookTime");
        int total = data.getInt("CookTimeTotal");
        if (total <= 0) total = 200;

        int barX = startX + 15 + 22;
        int barY = startY + 118 + 3;
        int maxWidth = 22;
        int currentWidth = progress > 0 ? (progress * maxWidth / total) : 0;

        graphics.fill(barX, barY, barX + maxWidth, barY + 10, 0xFF555555);
        graphics.fill(barX, barY, barX + currentWidth, barY + 10, 0xFF00FF00);
    }

    private static class AutoToggleButton extends Button {
        private final BlockEntity be;
        private final BlockPos pos;
        private long lastClickedTime = 0;
        private boolean localState;

        public AutoToggleButton(int x, int y, int width, int height, boolean initialState, BlockEntity be, BlockPos pos) {
            super(x, y, width, height, Component.literal("Auto: " + (initialState ? "ON" : "OFF")), btn -> {}, DEFAULT_NARRATION);
            this.be = be;
            this.pos = pos;
            this.localState = initialState;
        }

        @Override
        public void onPress() {
            if (be.getPersistentData().getString("SmeltTarget").isEmpty()) return;
            this.localState = !this.localState;
            this.lastClickedTime = System.currentTimeMillis();
            this.setMessage(Component.literal("Auto: " + (this.localState ? "ON" : "OFF")));
            be.getPersistentData().putBoolean("AutoSmeltActive", this.localState);
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_autosmelt", ""));
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            String currentTargetId = be.getPersistentData().getString("SmeltTarget");
            boolean hasTarget = !currentTargetId.isEmpty();

            if (!hasTarget) {
                this.active = false;
                this.localState = false;
                this.setMessage(Component.literal("Auto: OFF"));
            } else {
                this.active = true;
                boolean serverState = be.getPersistentData().getBoolean("AutoSmeltActive");
                if (System.currentTimeMillis() - lastClickedTime > 2000) {
                    if (this.localState != serverState) {
                        this.localState = serverState;
                        this.setMessage(Component.literal("Auto: " + (this.localState ? "ON" : "OFF")));
                    }
                } else {
                    be.getPersistentData().putBoolean("AutoSmeltActive", this.localState);
                }
            }
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    public static class TargetSlotWidget extends AbstractWidget {
        private final BlockPos blockPos;
        private final BlockEntity be;
        private final boolean isEditable;
        private final String tagKey;
        private ItemStack displayStack = ItemStack.EMPTY;
        private String lastTargetId = "";

        public TargetSlotWidget(int x, int y, BlockPos pos, BlockEntity be, boolean isEditable, String tagKey) {
            super(x, y, 16, 16, Component.empty());
            this.blockPos = pos;
            this.be = be;
            this.isEditable = isEditable;
            this.tagKey = tagKey;
            updateDisplayStack();
        }

        private void updateDisplayStack() {
            String targetId = be.getPersistentData().getString("SmeltTarget");
            this.lastTargetId = targetId;

            if (tagKey.equals("SmeltTarget")) {
                if (!targetId.isEmpty()) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(targetId));
                    this.displayStack = (item != Items.AIR) ? new ItemStack(item) : ItemStack.EMPTY;
                } else {
                    this.displayStack = ItemStack.EMPTY;
                }
            } else if (tagKey.equals("SmeltResult")) {
                String resultId = be.getPersistentData().getString("SmeltResult");
                if (!resultId.isEmpty()) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(resultId));
                    int count = be.getPersistentData().contains("SmeltResultCount") ? be.getPersistentData().getInt("SmeltResultCount") : 1;
                    this.displayStack = (item != Items.AIR) ? new ItemStack(item, count) : ItemStack.EMPTY;
                } else if (!targetId.isEmpty()) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(targetId));
                    if (item != Items.AIR && Minecraft.getInstance().level != null) {
                        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(item));
                        Optional<RecipeHolder<SmeltingRecipe>> recipe = Minecraft.getInstance().level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, Minecraft.getInstance().level);
                        if (recipe.isPresent()) {
                            this.displayStack = recipe.get().value().assemble(input, Minecraft.getInstance().level.registryAccess());
                            return;
                        }
                    }
                    this.displayStack = ItemStack.EMPTY;
                } else {
                    this.displayStack = ItemStack.EMPTY;
                }
            }
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            CompoundTag data = be.getPersistentData();
            String currentTargetId = data.getString("SmeltTarget");
            if (!currentTargetId.equals(this.lastTargetId)) {
                updateDisplayStack();
            }

            boolean currentActive = data.getBoolean("AutoSmeltActive");
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
            if (!isEditable || be.getPersistentData().getBoolean("AutoSmeltActive")) return;
            String itemId = "";
            if (!stack.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                itemId = id.toString();
            }
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(blockPos, "set_furnace_target", itemId));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isEditable || !isHovered() || be.getPersistentData().getBoolean("AutoSmeltActive")) return false;
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