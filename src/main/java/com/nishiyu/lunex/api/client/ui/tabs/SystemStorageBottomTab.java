package com.nishiyu.lunex.api.client.ui.tabs;

import com.nishiyu.lunex.api.client.IdeScreenFramework;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class SystemStorageBottomTab implements IdeScreenFramework.IBottomTab {

    private int scrollY = 0;
    public int lastRenderY = 0;

    private EditBox searchBox;
    private SortType currentSort = SortType.COUNT_DESC;
    private List<DisplayItem> cachedDisplayItems = null;

    // 動的レイアウト用の変数
    private int playerInvX = 5;
    private int storageX = 190;
    private int storageWidth = 315;

    public int getScrollY() { return this.scrollY; }

    public SystemStorageBottomTab() {}

    @Override
    public String getTitle() { return "System Storage"; }

    private void updateLayout(int screenWidth) {
        int jeiReservedWidth = 175; // JEI用の確保幅
        int playerInvWidth = 180;
        int spacing = 10; // Player Inventory と Mainframe Storage の隙間

        // 検索欄とソートボタンがはみ出さないための厳密な最小幅
        int minStorageWidth = 310;
        // 画面が広いときに広がりすぎないための最大幅
        int maxStorageWidth = 450;

        int availableWidth = screenWidth - jeiReservedWidth;

        // ストレージ幅の計算（利用可能な幅からPlayerInv等を引く）
        int calculatedStorageWidth = availableWidth - playerInvWidth - spacing - 20;
        this.storageWidth = Mth.clamp(calculatedStorageWidth, minStorageWidth, maxStorageWidth);

        // 全体の幅を計算し、JEIを除外した利用可能エリアの中央に配置する
        int totalContentWidth = playerInvWidth + spacing + this.storageWidth;
        this.playerInvX = Math.max(5, (availableWidth - totalContentWidth) / 2);
        this.storageX = this.playerInvX + playerInvWidth + spacing;
    }

    @Override
    public int getPlayerInventoryX() {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().getWindow() != null) {
            updateLayout(Minecraft.getInstance().getWindow().getGuiScaledWidth());
        }
        return this.playerInvX;
    }

    @Override
    public int getPlayerInventoryY(int screenHeight, int bottomHeight) {
        return this.lastRenderY != 0 ? this.lastRenderY + 30 : (screenHeight - bottomHeight + 20) + 30;
    }

    public int getCalculatedStorageX(int screenWidth) {
        updateLayout(screenWidth);
        return this.storageX;
    }

    public int getStorageAreaWidth(int totalWidth, int startX) {
        updateLayout(totalWidth);
        return this.storageWidth;
    }

    public enum SortType {
        COUNT_DESC("Count ↓"),
        COUNT_ASC("Count ↑"),
        NAME_ASC("Name A-Z"),
        NAME_DESC("Name Z-A");

        private final String label;
        SortType(String label) { this.label = label; }
        public String getLabel() { return label; }
        public SortType next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    private static class DisplayItem {
        ItemStack stack;
        long count;
        int firstOriginalIndex;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
        this.lastRenderY = y;
        updateLayout(width);

        int pX = this.playerInvX;
        int playerInvWidth = 180;

        graphics.fill(pX, y, pX + playerInvWidth, y + height, 0xFF2D2D30);
        graphics.drawString(Minecraft.getInstance().font, "Player Inventory", pX, y + 10, 0xFFFFFFFF);
        drawPlayerInventoryBackground(graphics, pX, y + 30);

        int sX = this.storageX;
        int sWidth = this.storageWidth;

        graphics.fill(sX, y, sX + sWidth, y + height, 0xFF1E1E1E);
        graphics.fill(sX - 10, y, sX, y + height, 0xFF2D2D30);

        int padding = 10;
        graphics.drawString(Minecraft.getInstance().font, "Mainframe Storage", sX + padding, y + 10, 0xFFFFFFFF);

        int titleWidth = Minecraft.getInstance().font.width("Mainframe Storage");
        int searchBoxX = sX + padding + titleWidth + 15;

        if (searchBox == null) {
            searchBox = new EditBox(Minecraft.getInstance().font, searchBoxX, y + 8, 100, 12, Component.empty());
            searchBox.setBordered(true);
            searchBox.setMaxLength(50);
        } else {
            searchBox.setX(searchBoxX);
            searchBox.setY(y + 8);
        }
        searchBox.render(graphics, mouseX, mouseY, partialTick);

        int sortBtnX = searchBox.getX() + searchBox.getWidth() + 10;
        int sortBtnY = y + 8;
        int sortBtnW = 65;
        int sortBtnH = 12;
        boolean hoverSort = mouseX >= sortBtnX && mouseX <= sortBtnX + sortBtnW && mouseY >= sortBtnY && mouseY <= sortBtnY + sortBtnH;
        graphics.fill(sortBtnX, sortBtnY, sortBtnX + sortBtnW, sortBtnY + sortBtnH, hoverSort ? 0xFF555555 : 0xFF333333);
        graphics.renderOutline(sortBtnX, sortBtnY, sortBtnW, sortBtnH, 0xFF6A6A6A);
        graphics.drawString(Minecraft.getInstance().font, currentSort.getLabel(), sortBtnX + 4, sortBtnY + 2, 0xFFFFFFFF);

        graphics.enableScissor(sX, y, sX + sWidth, y + height);
        drawInventoryGrid(graphics, sX, y, sWidth, height, mouseX, mouseY);
        graphics.disableScissor();
    }

    private List<DisplayItem> getDisplayItems(NonNullList<ItemStack> items) {
        List<DisplayItem> list = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;

            boolean found = false;
            for (DisplayItem di : list) {
                if (ItemStack.isSameItemSameComponents(di.stack, stack)) {
                    di.count += stack.getCount();
                    found = true;
                    break;
                }
            }
            if (!found) {
                DisplayItem di = new DisplayItem();
                di.stack = stack.copy();
                di.count = stack.getCount();
                di.firstOriginalIndex = i;
                list.add(di);
            }
        }

        String query = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        if (!query.isEmpty()) {
            list.removeIf(di -> !di.stack.getHoverName().getString().toLowerCase().contains(query));
        }

        list.sort((a, b) -> {
            switch (currentSort) {
                case COUNT_DESC: return Long.compare(b.count, a.count);
                case COUNT_ASC: return Long.compare(a.count, b.count);
                case NAME_ASC: return a.stack.getHoverName().getString().compareToIgnoreCase(b.stack.getHoverName().getString());
                case NAME_DESC: return b.stack.getHoverName().getString().compareToIgnoreCase(a.stack.getHoverName().getString());
                default: return 0;
            }
        });

        return list;
    }

    private void drawInventoryGrid(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        int slotSize = 18;
        int padding = 10;
        int columns = (width - padding * 2) / slotSize;
        if (columns <= 0) return;

        int startY = y + 30;

        if (Minecraft.getInstance().screen instanceof MainframeOverviewScreen screen) {
            BlockEntity be = screen.getMenu().getLevel().getBlockEntity(screen.getMenu().getMasterPos());
            if (be instanceof SimpleMachineBlockEntity master) {
                NonNullList<ItemStack> items = master.mainframeStorage.getStacks();
                List<DisplayItem> displayItems = getDisplayItems(items);
                this.cachedDisplayItems = displayItems;

                int rows = Math.max(10, (displayItems.size() / columns) + 2);
                ItemStack hoveredStack = null;

                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < columns; c++) {
                        int itemX = x + padding + (c * slotSize);
                        int itemY = startY + (r * slotSize) - this.scrollY;

                        if (itemY + slotSize < y || itemY > y + height) continue;

                        graphics.fill(itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF6A6A6A);
                        graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0xFF252526);

                        int index = r * columns + c;
                        if (index < displayItems.size()) {
                            DisplayItem di = displayItems.get(index);
                            ItemStack renderStack = di.stack;

                            graphics.renderItem(renderStack, itemX, itemY);
                            graphics.renderItemDecorations(Minecraft.getInstance().font, renderStack, itemX, itemY, "");
                            renderCustomDecoration(graphics, Minecraft.getInstance().font, di.count, itemX, itemY);

                            if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                                hoveredStack = renderStack;
                            }
                        }
                    }
                }

                if (hoveredStack != null) {
                    graphics.renderTooltip(Minecraft.getInstance().font, hoveredStack, mouseX, mouseY);
                }
            }
        }
    }

    private void renderCustomDecoration(GuiGraphics graphics, Font font, long count, int x, int y) {
        if (count <= 1) return;

        String text;
        if (count < 1000) {
            text = String.valueOf(count);
        } else if (count < 10000) {
            text = String.format("%.1fk", count / 1000.0f).replace(".0", "");
        } else if (count < 1000000) {
            text = (count / 1000) + "k";
        } else {
            text = String.format("%.1fM", count / 1000000.0f).replace(".0", "");
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        int width = font.width(text);
        graphics.drawString(font, text, x + 17 - width, y + 9, 0xFFFFFF, true);
        graphics.pose().popPose();
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

    @Override
    public boolean mouseClicked(int x, int y, int width, int height, double mouseX, double mouseY, int button) {
        updateLayout(width);

        if (searchBox != null) {
            if (mouseX >= searchBox.getX() && mouseX <= searchBox.getX() + searchBox.getWidth() &&
                    mouseY >= searchBox.getY() && mouseY <= searchBox.getY() + searchBox.getHeight()) {
                searchBox.setFocused(true);
                searchBox.mouseClicked(mouseX, mouseY, button);
                return true;
            } else {
                searchBox.setFocused(false);
            }
        }

        int sX = this.storageX;
        int sWidth = this.storageWidth;
        int padding = 10;

        int sortBtnX = (searchBox != null ? searchBox.getX() + searchBox.getWidth() : sX + padding + 120) + 10;
        int sortBtnY = y + 8;
        int sortBtnW = 65;
        int sortBtnH = 12;
        if (mouseX >= sortBtnX && mouseX <= sortBtnX + sortBtnW && mouseY >= sortBtnY && mouseY <= sortBtnY + sortBtnH) {
            currentSort = currentSort.next();
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (mouseX >= sX && mouseX < sX + sWidth && mouseY >= y + 30 && mouseY <= y + height) {
            int slotSize = 18;
            int columns = (sWidth - padding * 2) / slotSize;
            if (columns <= 0) columns = 1;

            int relX = (int)mouseX - sX - padding;
            int relY = (int)mouseY - (y + 30 - this.scrollY);

            int originalIndex = -1;

            if (relX >= 0 && relX < columns * slotSize && relY >= 0) {
                int c = relX / slotSize;
                int r = relY / slotSize;
                int targetIndex = r * columns + c;

                if (cachedDisplayItems != null && targetIndex < cachedDisplayItems.size()) {
                    originalIndex = cachedDisplayItems.get(targetIndex).firstOriginalIndex;
                }
            }

            if (originalIndex == -1) {
                if (Minecraft.getInstance().screen instanceof MainframeOverviewScreen screen) {
                    BlockEntity be = screen.getMenu().getLevel().getBlockEntity(screen.getMenu().getMasterPos());
                    if (be instanceof SimpleMachineBlockEntity master) {
                        originalIndex = master.mainframeStorage.getStacks().size();
                    }
                }
            }

            if (originalIndex >= 0) {
                if (Minecraft.getInstance().screen instanceof MainframeOverviewScreen screen) {
                    PacketDistributor.sendToServer(
                            new MainframeOverviewActionC2SPacket(
                                    screen.getMenu().getMasterPos(), "storage_click", originalIndex + ":" + button
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
        updateLayout(width);

        if (mouseX >= this.storageX && mouseX < this.storageX + this.storageWidth) {
            this.scrollY = Mth.clamp(this.scrollY - (int)(scrollY * 15), 0, 1000);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == 256) {
                searchBox.setFocused(false);
                return false;
            }
            searchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.charTyped(codePoint, modifiers);
            return true;
        }
        return false;
    }
}