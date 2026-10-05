package com.nishiyu.lunex.api.client.ui.tabs;

import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class SystemStorageBottomTab extends AbstractBottomTab {

    private EditBox searchBox;
    private Button sortBtn;
    private SortType currentSort = SortType.COUNT_DESC;
    private List<DisplayItem> cachedDisplayItems = null;

    // ホバー中のアイテムを一時保持する変数
    private ItemStack currentHoveredStack = null;

    // UIレイアウト定数
    private static final int PLAYER_AREA_WIDTH = 180; // (18px * 9列) + 両端パディング
    private static final int HEADER_HEIGHT = 30;
    private static final int PADDING = 9;
    private static final int SLOT_SIZE = 18;

    private int currentWidth = 0; // クリック判定用に親から渡された幅をキャッシュ

    public SystemStorageBottomTab() {}

    @Override
    public String getTitle() { return "System Storage"; }

    @Override
    protected int getHeaderHeight() {
        return HEADER_HEIGHT;
    }

    @Override
    public int getPlayerInventoryX() {
        return PADDING; // タブ内の左端からの相対位置
    }

    @Override
    public int getPlayerInventoryY(int screenHeight, int bottomHeight) {
        return (screenHeight - bottomHeight + 20) + HEADER_HEIGHT;
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick) {
        this.currentWidth = width;
        int playerAreaX = startX;
        int storageAreaX = startX + PLAYER_AREA_WIDTH;
        int storageAreaWidth = width - PLAYER_AREA_WIDTH;

        updateMenuSlots(playerAreaX + PADDING, startY + HEADER_HEIGHT);

        // 1. プレイヤーインベントリ側の背景描画 (隙間なく塗りつぶす)
        graphics.fill(playerAreaX, startY, playerAreaX + PLAYER_AREA_WIDTH, startY + height, 0xFF2D2D30);
        graphics.drawString(Minecraft.getInstance().font, "Player Inventory", playerAreaX + PADDING, startY + 10, 0xFFFFFFFF);
        drawPlayerInventoryBackground(graphics, playerAreaX + PADDING, startY + HEADER_HEIGHT);

        // 2. ストレージ側の背景描画 (コンテンツ領域)
        graphics.fill(storageAreaX, startY + HEADER_HEIGHT, storageAreaX + storageAreaWidth, startY + height, 0xFF1E1E1E);

        // 3. ストレージ側のヘッダー背景描画
        graphics.fill(storageAreaX, startY, storageAreaX + storageAreaWidth, startY + HEADER_HEIGHT, 0xFF2D2D30);
        graphics.drawString(Minecraft.getInstance().font, "Mainframe Storage", storageAreaX + PADDING, startY + 10, 0xFFFFFFFF);

        // ウィジェットの初期化とサイズ調整
        if (searchBox == null) {
            searchBox = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 12, Component.empty());
            searchBox.setBordered(true);
            searchBox.setMaxLength(50);
            addOverlayWidget(searchBox, 0, 9);

            sortBtn = Button.builder(Component.literal(currentSort.getLabel()), btn -> {
                currentSort = currentSort.next();
                btn.setMessage(Component.literal(currentSort.getLabel()));
            }).bounds(0, 0, 65, 12).build();
            addOverlayWidget(sortBtn, 0, 9);
        }

        int titleWidth = Minecraft.getInstance().font.width("Mainframe Storage");
        int requiredHeaderSpace = titleWidth + 100 + 65 + PADDING * 3;
        int availableHeaderSpace = storageAreaWidth - PADDING * 2;

        if (availableHeaderSpace < requiredHeaderSpace) {
            int newSearchWidth = Math.max(30, availableHeaderSpace - (titleWidth + 65 + PADDING * 2));
            searchBox.setWidth(newSearchWidth);
        } else {
            searchBox.setWidth(100);
        }

        // 親コンテナからの相対位置を更新
        overlayWidgets.get(0).relX = PLAYER_AREA_WIDTH + PADDING + titleWidth + 10;
        overlayWidgets.get(1).relX = overlayWidgets.get(0).relX + searchBox.getWidth() + 10;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick) {
        if (width <= 0) return;
        this.currentWidth = width;

        int storageAreaX = startX + PLAYER_AREA_WIDTH;
        int storageAreaWidth = width - PLAYER_AREA_WIDTH;

        int columns = (storageAreaWidth - PADDING * 2) / SLOT_SIZE;
        if (columns <= 0) columns = 1;

        if (Minecraft.getInstance().screen instanceof MainframeOverviewScreen screen) {
            BlockEntity be = screen.getMenu().getLevel().getBlockEntity(screen.getMenu().getMasterPos());

            // 物理層にある mainframeStorage に直接アクセスしてアイテム一覧を取得する
            if (be instanceof SimpleMachineBlockEntity master) {
                List<DisplayItem> displayItems = getDisplayItems(master.mainframeStorage.getStacks());
                this.cachedDisplayItems = displayItems;
                int rows = Math.max(10, (displayItems.size() / columns) + 2);

                this.maxScroll = Math.max(0, (rows * SLOT_SIZE) - height);

                ItemStack hoveredStack = null;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < columns; c++) {
                        int itemX = storageAreaX + PADDING + (c * SLOT_SIZE);
                        int itemY = startY + (r * SLOT_SIZE);
                        int index = r * columns + c;

                        if (index < displayItems.size()) {
                            drawSlotBox(graphics, itemX, itemY);
                            DisplayItem di = displayItems.get(index);
                            graphics.renderItem(di.stack, itemX, itemY);
                            renderCustomDecoration(graphics, Minecraft.getInstance().font, di.count, itemX, itemY);
                            if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) hoveredStack = di.stack;
                        } else if (index < rows * columns) {
                            drawSlotBox(graphics, itemX, itemY);
                        }
                    }
                }

                // 描画を直接行わず、ホバー中のアイテムを保持する
                this.currentHoveredStack = hoveredStack;
            }
        }
    }

    // クリッピング解除後にツールチップを描画する
    @Override
    protected void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.currentHoveredStack != null) {
            graphics.renderTooltip(Minecraft.getInstance().font, this.currentHoveredStack, mouseX, mouseY);
        }
    }

    @Override
    protected boolean contentMouseClicked(int startX, int startY, double mouseX, double mouseY, int button) {
        if (this.currentWidth <= 0) return false;

        int storageAreaX = startX + PLAYER_AREA_WIDTH;
        int storageAreaWidth = this.currentWidth - PLAYER_AREA_WIDTH;
        int columns = (storageAreaWidth - PADDING * 2) / SLOT_SIZE;
        if (columns <= 0) columns = 1;

        if (mouseX >= storageAreaX && mouseX < storageAreaX + storageAreaWidth && mouseY >= startY) {
            int gridXOffset = (int)mouseX - storageAreaX - PADDING;
            int gridYOffset = (int)mouseY - startY;

            if (gridXOffset >= 0 && gridXOffset < columns * SLOT_SIZE && gridYOffset >= 0) {
                int c = gridXOffset / SLOT_SIZE;
                int r = gridYOffset / SLOT_SIZE;
                int targetIndex = r * columns + c;

                if (Minecraft.getInstance().screen instanceof MainframeOverviewScreen screen) {
                    Minecraft mc = Minecraft.getInstance();

                    if (mc.player != null && !mc.player.containerMenu.getCarried().isEmpty()) {
                        PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(screen.getMenu().getMasterPos(), "storage_insert", String.valueOf(button)));
                        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                    else if (cachedDisplayItems != null && targetIndex < cachedDisplayItems.size()) {
                        int originalIndex = cachedDisplayItems.get(targetIndex).firstOriginalIndex;
                        PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(screen.getMenu().getMasterPos(), "storage_click", originalIndex + ":" + button));
                        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void updateMenuSlots(int startX, int startY) {
        if (Minecraft.getInstance().screen instanceof MainframeOverviewScreen screen) {
            var menu = screen.getMenu();
            if (menu == null || menu.slots == null) return;

            int startIndex = menu.slots.size() - 36;
            if (startIndex >= 0) {
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 9; c++) {
                        Slot slot = menu.slots.get(startIndex + r * 9 + c);
                        slot.x = startX + c * SLOT_SIZE + 1;
                        slot.y = startY + r * SLOT_SIZE + 1;
                    }
                }
                for (int c = 0; c < 9; c++) {
                    Slot slot = menu.slots.get(startIndex + 27 + c);
                    slot.x = startX + c * SLOT_SIZE + 1;
                    slot.y = startY + 3 * SLOT_SIZE + 4 + 1;
                }
            }
        }
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
        if (!query.isEmpty()) list.removeIf(di -> !di.stack.getHoverName().getString().toLowerCase().contains(query));
        list.sort((a, b) -> switch (currentSort) {
            case COUNT_DESC -> Long.compare(b.count, a.count);
            case COUNT_ASC -> Long.compare(a.count, b.count);
            case NAME_ASC ->
                    a.stack.getHoverName().getString().compareToIgnoreCase(b.stack.getHoverName().getString());
            case NAME_DESC ->
                    b.stack.getHoverName().getString().compareToIgnoreCase(a.stack.getHoverName().getString());
        });
        return list;
    }

    private void renderCustomDecoration(GuiGraphics graphics, Font font, long count, int x, int y) {
        if (count <= 1) return;
        String text;
        if (count < 1000) text = String.valueOf(count);
        else if (count < 10000) text = String.format("%.1fk", count / 1000.0f).replace(".0", "");
        else if (count < 1000000) text = (count / 1000) + "k";
        else text = String.format("%.1fM", count / 1000000.0f).replace(".0", "");
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        int width = font.width(text);
        graphics.drawString(font, text, x + 17 - width, y + 9, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    private void drawPlayerInventoryBackground(GuiGraphics graphics, int startX, int startY) {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) drawSlotBox(graphics, startX + c * SLOT_SIZE, startY + r * SLOT_SIZE);
        }
        for (int c = 0; c < 9; c++) drawSlotBox(graphics, startX + c * SLOT_SIZE, startY + 3 * SLOT_SIZE + 4);
    }

    private void drawSlotBox(GuiGraphics graphics, int itemX, int itemY) {
        graphics.fill(itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF6A6A6A);
        graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0xFF252526);
    }

    public enum SortType {
        COUNT_DESC("Count ↓"), COUNT_ASC("Count ↑"), NAME_ASC("Name A-Z"), NAME_DESC("Name Z-A");
        private final String label;
        SortType(String label) { this.label = label; }
        public String getLabel() { return label; }
        public SortType next() { return values()[(this.ordinal() + 1) % values().length]; }
    }

    private static class DisplayItem {
        ItemStack stack;
        long count;
        int firstOriginalIndex;
    }
}