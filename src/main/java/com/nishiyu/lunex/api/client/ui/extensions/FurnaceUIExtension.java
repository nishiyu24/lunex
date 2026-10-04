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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;
import java.util.function.Consumer;

public class FurnaceUIExtension implements IMainframeUIExtension {

    @Override
    public int getPanelHeight(BlockEntity be) {
        return 120;
    }

    @Override
    public void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, BlockEntity be, int panelX, int textY, Consumer<AbstractWidget> addWidget) {
        CompoundTag data = be.getPersistentData();
        boolean isActive = data.getBoolean("AutoSmeltActive");

        // ★修正: activeBtnのコンストラクタからはhasTargetの初期代入を削除し、内部で完結させました
        AutoToggleButton activeBtn = new AutoToggleButton(panelX + 5, textY + 15, 65, 20, isActive, be, pos);
        addWidget.accept(activeBtn);

        int gridX = panelX + 15;
        int gridY = textY + 68;

        // ★修正: TargetSlotWidgetにactiveBtnを渡す必要がなくなったため引数を削除
        addWidget.accept(new TargetSlotWidget(gridX, gridY, pos, be, true, "SmeltTarget"));

        int barX = gridX + 22;
        addWidget.accept(new TargetSlotWidget(barX + 28, gridY, pos, be, false, "SmeltResult"));
    }

    @Override
    public void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, BlockEntity be, int panelX, int textY) {
        guiGraphics.drawString(font, "Furnace Settings:", panelX + 5, textY, 0x00E5FF);
        guiGraphics.drawString(font, "Target Item:", panelX + 5, textY + 53, 0xFFFFFF);

        CompoundTag data = be.getPersistentData();
        int progress = data.getInt("CookTime");
        int total = data.getInt("CookTimeTotal");
        if (total <= 0) total = 200;

        int gridX = panelX + 15;
        int gridY = textY + 68;
        int barX = gridX + 22;
        int barY = gridY + 3;

        int maxWidth = 22;
        int currentWidth = progress > 0 ? (progress * maxWidth / total) : 0;
        guiGraphics.fill(barX, barY, barX + maxWidth, barY + 10, 0xFF555555);
        guiGraphics.fill(barX, barY, barX + currentWidth, barY + 10, 0xFF00FF00);
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
            // 安全対策: ターゲットがない時はクリック処理を無視
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

            // ★修正: ターゲットがない時はボタンを無効化し、強制的にOFF状態で固定する（チカチカ防止）
            if (!hasTarget) {
                this.active = false;
                this.localState = false;
                this.setMessage(Component.literal("Auto: OFF"));
            } else {
                this.active = true;
                boolean serverState = be.getPersistentData().getBoolean("AutoSmeltActive");

                // クリックしてから2秒間はサーバーから送られてきた古いデータを強制的に無視する
                if (System.currentTimeMillis() - lastClickedTime > 2000) {
                    if (this.localState != serverState) {
                        this.localState = serverState;
                        this.setMessage(Component.literal("Auto: " + (this.localState ? "ON" : "OFF")));
                    }
                } else {
                    // 2秒以内の間はローカル状態を固定し続ける
                    be.getPersistentData().putBoolean("AutoSmeltActive", this.localState);
                }
            }

            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private static class TargetSlotWidget extends AbstractWidget {
        private final BlockPos blockPos;
        private final BlockEntity be;
        private final boolean isEditable;
        private final String tagKey;

        private ItemStack displayStack = ItemStack.EMPTY;
        private String lastTargetId = "";

        // ★修正: コンストラクタから activeBtn を削除
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
            // ★修正: activeBtnの操作をここから削除し、TargetSlotWidgetの役割をアイテム描画のみに専念させました
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

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isEditable || !isHovered() || be.getPersistentData().getBoolean("AutoSmeltActive")) return false;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ItemStack carried = mc.player.containerMenu.getCarried();
                String itemId = "";

                if (button == 1) {
                    itemId = "";
                } else if (!carried.isEmpty()) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(carried.getItem());
                    itemId = id.toString();
                } else {
                    return true;
                }

                PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(blockPos, "set_furnace_target", itemId));
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {}
    }
}