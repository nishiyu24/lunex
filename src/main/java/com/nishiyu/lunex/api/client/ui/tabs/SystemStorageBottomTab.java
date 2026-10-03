package com.nishiyu.lunex.api.client.ui.tabs;

import com.nishiyu.lunex.api.client.IdeScreenFramework;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class SystemStorageBottomTab implements IdeScreenFramework.IBottomTab {

    private int scrollY = 0;
    public int lastRenderY = 0;

    public int getScrollY() { return this.scrollY; }

    public SystemStorageBottomTab() {}

    @Override
    public String getTitle() { return "System Storage"; }

    // ★追加: プレイヤーインベントリを適正な位置に配置する
    @Override
    public int getPlayerInventoryX() {
        return 10;
    }

    @Override
    public int getPlayerInventoryY(int screenHeight, int bottomHeight) {
        // 実際にレンダリングされたY座標を基準にする
        return this.lastRenderY != 0 ? this.lastRenderY + 30 : (screenHeight - bottomHeight + 20) + 30;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
        this.lastRenderY = y; // 描画位置の記録

        int playerInvWidth = 180;
        int playerX = x + 10;

        // 1. 左側: プレイヤーインベントリ領域
        graphics.fill(x, y, playerX + playerInvWidth, y + height, 0xFF2D2D30);
        graphics.drawString(Minecraft.getInstance().font, "Player Inventory", playerX, y + 10, 0xFFFFFFFF);
        drawPlayerInventoryBackground(graphics, playerX, y + 30);

        // 2. 右側: メインフレームストレージエリア
        int storageX = playerX + playerInvWidth + 10;
        int storageWidth = width - storageX;

        graphics.fill(storageX, y, x + width, y + height, 0xFF1E1E1E);
        graphics.fill(storageX - 10, y, storageX, y + height, 0xFF2D2D30);

        int padding = 15;
        graphics.drawString(Minecraft.getInstance().font, "Mainframe Storage", storageX + padding, y + 10, 0xFFFFFFFF);

        graphics.enableScissor(storageX, y, x + width, y + height);
        drawInventoryGrid(graphics, storageX, y, storageWidth, height);
        graphics.disableScissor();
    }

    private void drawInventoryGrid(GuiGraphics graphics, int x, int y, int width, int height) {
        int slotSize = 18; int padding = 15;
        int columns = (width - padding * 2) / slotSize;
        if (columns <= 0) return;

        int startY = y + 30;

        if (Minecraft.getInstance().screen instanceof MainframeOverviewScreen screen) {
            BlockEntity be = screen.getMenu().getLevel().getBlockEntity(screen.getMenu().getMasterPos());
            if (be instanceof SimpleMachineBlockEntity master) {
                NonNullList<ItemStack> items = master.mainframeStorage.getStacks();
                int rows = Math.max(10, (items.size() / columns) + 2);

                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < columns; c++) {
                        int itemX = x + padding + (c * slotSize);
                        int itemY = startY + (r * slotSize) - this.scrollY;

                        if (itemY + slotSize < y || itemY > y + height) continue;

                        graphics.fill(itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF6A6A6A);
                        graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0xFF252526);

                        int index = r * columns + c;
                        if (index < items.size()) {
                            ItemStack stack = items.get(index);
                            if (!stack.isEmpty()) {
                                graphics.renderItem(stack, itemX, itemY);
                                graphics.renderItemDecorations(Minecraft.getInstance().font, stack, itemX, itemY);
                            }
                        }
                    }
                }
            }
        }
    }

    private void drawPlayerInventoryBackground(GuiGraphics graphics, int startX, int startY) {
        int slotSize = 18;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) drawSlotBox(graphics, startX + c * slotSize, startY + r * slotSize);
        }
        for (int c = 0; c < 9; c++) drawSlotBox(graphics, startX + c * slotSize, startY + 3 * slotSize + 4);
    }

    private void drawSlotBox(GuiGraphics graphics, int itemX, int itemY) {
        graphics.fill(itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF6A6A6A);
        graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0xFF252526);
    }

    // ★追加: MainframeOverviewScreen から引き継いだクリック・パケット処理
    @Override
    public boolean mouseClicked(int x, int y, int width, int height, double mouseX, double mouseY, int button) {
        int playerInvWidth = 180;
        int storageX = x + 10 + playerInvWidth + 10;
        int storageWidth = width - storageX;

        if (mouseX >= storageX && mouseX < x + width && mouseY >= y + 30 && mouseY <= y + height) {
            int padding = 15;
            int slotSize = 18;
            int columns = (storageWidth - padding * 2) / slotSize;
            if (columns <= 0) columns = 1;

            int relX = (int)mouseX - storageX - padding;
            int relY = (int)mouseY - (y + 30 - this.scrollY);

            int targetIndex = -1;
            if (relX >= 0 && relX < columns * slotSize && relY >= 0) {
                int c = relX / slotSize;
                int r = relY / slotSize;
                targetIndex = r * columns + c;
            }

            if (targetIndex >= 0) {
                if (Minecraft.getInstance().screen instanceof MainframeOverviewScreen screen) {
                    BlockEntity be = screen.getMenu().getLevel().getBlockEntity(screen.getMenu().getMasterPos());
                    if (be instanceof SimpleMachineBlockEntity master) {
                        int currentSize = master.mainframeStorage.getStacks().size();
                        if (targetIndex >= currentSize) {
                            targetIndex = currentSize;
                        }
                    }

                    PacketDistributor.sendToServer(
                            new MainframeOverviewActionC2SPacket(
                                    screen.getMenu().getMasterPos(), "storage_click", targetIndex + ":" + button
                            )
                    );
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(int x, int y, int width, int height, double mouseX, double mouseY, double scrollX, double scrollY) {
        int playerInvWidth = 180;
        int storageX = x + 10 + playerInvWidth;
        if (mouseX >= storageX) {
            this.scrollY = Mth.clamp(this.scrollY - (int)(scrollY * 15), 0, 1000);
            return true;
        }
        return false;
    }
}