package com.nishiyu.lunex.menu;

import com.nishiyu.lunex.api.MainframeComponentData;
import com.nishiyu.lunex.api.MainframeComponentRegistry;
import com.nishiyu.lunex.api.client.IdeScreenFramework;
import com.nishiyu.lunex.api.client.MainframeUIRegistry;
import com.nishiyu.lunex.api.client.ui.panel.AbstractRightPanel;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * マルチブロック（メインフレーム）の概要および構成パーツ一覧を表示・操作するシンプルなリスト形式スクリーン。
 */
public class MainframeOverviewScreen extends AbstractContainerScreen<MainframeOverviewMenu> {

    private static final int GUI_WIDTH = 420;
    private static final int GUI_HEIGHT = 230;
    private static final int LIST_ITEM_HEIGHT = 26;

    // 検索・フィルタリング
    private EditBox searchBox;
    private String filterQuery = "";

    // リストアイテム
    private final List<PartItem> allItems = new ArrayList<>();
    private final List<PartItem> filteredItems = new ArrayList<>();
    private PartItem selectedItem = null;

    // スクロール状態
    private int scrollOffset = 0;
    private boolean isDraggingScrollBar = false;

    // 右側設定・詳細パネル
    private AbstractRightPanel currentRightPanel = null;
    private BlockPos selectedPos = null;

    public MainframeOverviewScreen(MainframeOverviewMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    public BlockPos getSelectedPos() {
        return this.selectedPos;
    }

    public int getGuiStartX() {
        return (this.width - this.imageWidth) / 2;
    }

    public int getGuiStartY() {
        return (this.height - this.imageHeight) / 2;
    }

    private int getListX() { return getGuiStartX() + 8; }
    private int getListY() { return getGuiStartY() + 48; }
    private int getListWidth() { return 175; }
    private int getListHeight() { return this.imageHeight - 56; }

    private int getRightPanelX() { return getGuiStartX() + 190; }
    private int getRightPanelY() { return getGuiStartY() + 26; }
    private int getRightPanelWidth() { return this.imageWidth - 198; }
    private int getRightPanelHeight() { return this.imageHeight - 34; }

    @Override
    protected void init() {
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        super.init();

        this.leftPos = getGuiStartX();
        this.topPos = getGuiStartY();

        // プレイヤーインベントリのスロットを画面外へ退避
        for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
            slot.x = -10000;
            slot.y = -10000;
        }

        // IdeScreenFrameworkのオフセットを初期化（AbstractRightPanelのシザー座標用）
        IdeScreenFramework.guiOffsetX = 0;
        IdeScreenFramework.guiOffsetY = 0;

        // パーツ検索ボックス
        int searchX = getGuiStartX() + 8;
        int searchY = getGuiStartY() + 28;
        int searchW = 175;
        this.searchBox = new EditBox(this.font, searchX, searchY, searchW, 16, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search parts...").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        this.searchBox.setResponder(text -> {
            this.filterQuery = text;
            applyFilter();
        });
        this.addRenderableWidget(this.searchBox);

        // 閉じるボタン
        int closeBtnX = getGuiStartX() + this.imageWidth - 22;
        int closeBtnY = getGuiStartY() + 4;
        Button closeBtn = Button.builder(Component.literal("✕"), btn -> this.onClose())
                .bounds(closeBtnX, closeBtnY, 18, 16).build();
        this.addRenderableWidget(closeBtn);

        // パーツリスト構築
        refreshPartsList();

        // 初回選択（Overview）
        if (!this.allItems.isEmpty()) {
            selectItem(this.allItems.get(0));
        }
    }

    private void refreshPartsList() {
        this.allItems.clear();
        BlockPos masterPos = this.menu.getMasterPos();
        Level level = this.menu.getLevel();
        BlockEntity masterBe = level.getBlockEntity(masterPos);
        SimpleMachineBlockEntity master = (masterBe instanceof SimpleMachineBlockEntity m) ? m : null;

        // 1. 全体概要 (Overview)
        this.allItems.add(new PartItem(
                masterPos,
                true,
                false,
                new ItemStack(Blocks.BEACON),
                Component.literal("★ System Overview"),
                "Multiblock Summary & Stats",
                "Overview",
                null
        ));

        // 2. コアマシン
        BlockState masterState = level.getBlockState(masterPos);
        ItemStack masterIcon = masterState.getBlock().asItem().getDefaultInstance();
        if (masterIcon.isEmpty()) masterIcon = new ItemStack(Items.IRON_BLOCK);
        this.allItems.add(new PartItem(
                masterPos,
                false,
                true,
                masterIcon,
                Component.literal("Mainframe Core"),
                String.format("(%d, %d, %d)", masterPos.getX(), masterPos.getY(), masterPos.getZ()),
                "Core",
                masterState
        ));

        // 3. 構成パーツ
        if (master != null && !master.mainframeParts.isEmpty()) {
            List<BlockPos> sortedParts = new ArrayList<>(master.mainframeParts);
            sortedParts.sort(Comparator.comparingInt(BlockPos::getY)
                    .thenComparingInt(BlockPos::getX)
                    .thenComparingInt(BlockPos::getZ));

            for (BlockPos p : sortedParts) {
                if (p.equals(masterPos)) continue;

                BlockState state = level.getBlockState(p);
                BlockEntity be = level.getBlockEntity(p);
                BlockState displayState = state;
                if (be instanceof MainframeAdapterBlockEntity adapter && adapter.getOriginalState() != null) {
                    displayState = adapter.getOriginalState();
                }

                Block block = displayState.getBlock();
                ItemStack icon = block.asItem().getDefaultInstance();
                if (icon.isEmpty()) {
                    icon = new ItemStack(Items.IRON_BLOCK);
                }

                Component name = MainframeUIRegistry.getDisplayBlockName(be);
                if (name == null || name.getString().isEmpty()) {
                    name = block.getName();
                }

                int dx = p.getX() - masterPos.getX();
                int dy = p.getY() - masterPos.getY();
                int dz = p.getZ() - masterPos.getZ();
                String coordText = String.format("Δ[%+d, %+d, %+d] (%d, %d, %d)", dx, dy, dz, p.getX(), p.getY(), p.getZ());
                String roleTag = determineRoleTag(block);

                this.allItems.add(new PartItem(p, false, false, icon, name, coordText, roleTag, displayState));
            }
        }

        applyFilter();
    }

    private String determineRoleTag(Block block) {
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath().toLowerCase(Locale.ROOT);
        if (name.contains("database")) return "Storage";
        if (name.contains("crafter")) return "Crafting";
        if (name.contains("furnace")) return "Smelting";
        if (name.contains("probe")) return "IO / Port";
        if (name.contains("screen")) return "Display";
        if (name.contains("speaker")) return "Audio";
        if (name.contains("router")) return "Network";
        if (name.contains("cable")) return "Bus";
        if (name.contains("frame")) return "Structure";
        return "Component";
    }

    private void applyFilter() {
        this.filteredItems.clear();
        String query = this.filterQuery.trim().toLowerCase(Locale.ROOT);

        for (PartItem item : this.allItems) {
            if (query.isEmpty() || item.isOverview) {
                this.filteredItems.add(item);
                continue;
            }
            boolean match = item.title.getString().toLowerCase(Locale.ROOT).contains(query)
                    || item.subText.toLowerCase(Locale.ROOT).contains(query)
                    || item.roleTag.toLowerCase(Locale.ROOT).contains(query);
            if (match) {
                this.filteredItems.add(item);
            }
        }

        clampScroll();

        if (this.selectedItem != null && !this.filteredItems.contains(this.selectedItem)) {
            if (!this.filteredItems.isEmpty()) {
                selectItem(this.filteredItems.get(0));
            } else {
                selectItem(null);
            }
        }
    }

    private void selectItem(PartItem item) {
        this.selectedItem = item;

        if (item == null || item.isOverview) {
            this.selectedPos = null;
            this.currentRightPanel = null;
            return;
        }

        this.selectedPos = item.pos;
        BlockEntity be = this.menu.getLevel().getBlockEntity(item.pos);
        this.currentRightPanel = MainframeUIRegistry.createRightPanel(item.pos, be);
    }

    private int getMaxListScroll() {
        return Math.max(0, this.filteredItems.size() * LIST_ITEM_HEIGHT - getListHeight());
    }

    private void clampScroll() {
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, getMaxListScroll());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        BlockEntity be = this.menu.getLevel().getBlockEntity(this.menu.getMasterPos());
        if (!(be instanceof SimpleMachineBlockEntity master) || !master.isMainframeMaster) {
            this.onClose();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int startX = getGuiStartX();
        int startY = getGuiStartY();

        // 1. 全体背景と外枠
        guiGraphics.fill(startX, startY, startX + this.imageWidth, startY + this.imageHeight, 0xFF1E1E2E);
        guiGraphics.renderOutline(startX, startY, this.imageWidth, this.imageHeight, 0xFF45475A);

        // 2. ヘッダーバー
        guiGraphics.fill(startX + 1, startY + 1, startX + this.imageWidth - 1, startY + 24, 0xFF181825);
        guiGraphics.fill(startX + 1, startY + 24, startX + this.imageWidth - 1, startY + 25, 0xFF45475A);
        guiGraphics.drawString(this.font, "§lMAINFRAME OVERVIEW", startX + 10, startY + 8, 0xFFCDD6F4);
        guiGraphics.drawString(this.font, "● ASSEMBLED", startX + 140, startY + 8, 0xFF55FF55);

        // 3. 左右境界線
        guiGraphics.fill(startX + 186, startY + 25, startX + 187, startY + this.imageHeight - 1, 0xFF45475A);

        // 4. 左側パーツリスト
        renderPartsList(guiGraphics, mouseX, mouseY);

        // 5. 右側パネル（詳細・設定）
        renderRightPanel(guiGraphics, mouseX, mouseY, partialTick);

        // 6. ウィジェット（検索ボックス、閉じるボタン等）の描画
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 7. リスト項目のツールチップ
        renderListTooltips(guiGraphics, mouseX, mouseY);
    }

    private void renderPartsList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int lx = getListX();
        int ly = getListY();
        int lw = getListWidth();
        int lh = getListHeight();

        // リスト背景
        guiGraphics.fill(lx, ly, lx + lw, ly + lh, 0xFF181825);
        guiGraphics.renderOutline(lx - 1, ly - 1, lw + 2, lh + 2, 0xFF313244);

        guiGraphics.enableScissor(lx, ly, lx + lw, ly + lh);

        int itemY = ly - this.scrollOffset;
        for (PartItem item : this.filteredItems) {
            if (itemY + LIST_ITEM_HEIGHT >= ly && itemY <= ly + lh) {
                boolean isSelected = (item == this.selectedItem);
                boolean isHovered = mouseX >= lx && mouseX <= lx + lw && mouseY >= itemY && mouseY < itemY + LIST_ITEM_HEIGHT;

                // 項目背景
                int bgColor = isSelected ? 0xFF313244 : (isHovered ? 0xFF262738 : 0x00000000);
                if (bgColor != 0) {
                    guiGraphics.fill(lx, itemY, lx + lw, itemY + LIST_ITEM_HEIGHT, bgColor);
                }
                if (isSelected) {
                    guiGraphics.fill(lx, itemY, lx + 3, itemY + LIST_ITEM_HEIGHT, 0xFF89B4FA);
                }

                // アイコン
                guiGraphics.renderFakeItem(item.icon, lx + 5, itemY + 5);

                // タイトル
                int textX = lx + 25;
                int textW = lw - 30;
                String titleStr = item.title.getString();
                if (this.font.width(titleStr) > textW - 35) {
                    titleStr = this.font.plainSubstrByWidth(titleStr, textW - 40) + "...";
                }
                guiGraphics.drawString(this.font, titleStr, textX, itemY + 4, isSelected ? 0xFF89B4FA : 0xFFCDD6F4);

                // サブテキスト（座標など）
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(textX, itemY + 15, 0);
                guiGraphics.pose().scale(0.72f, 0.72f, 1.0f);
                guiGraphics.drawString(this.font, item.subText, 0, 0, 0xFFA6ADC8, false);
                guiGraphics.pose().popPose();

                // ロールタグ
                if (!item.isOverview && !item.roleTag.isEmpty()) {
                    int tagW = (int) (this.font.width(item.roleTag) * 0.65f) + 4;
                    int tagX = lx + lw - tagW - 4;
                    int tagY = itemY + 4;
                    guiGraphics.fill(tagX, tagY, tagX + tagW, tagY + 9, 0xFF45475A);
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(tagX + 2, tagY + 1, 0);
                    guiGraphics.pose().scale(0.65f, 0.65f, 1.0f);
                    guiGraphics.drawString(this.font, item.roleTag, 0, 0, 0xFFCDD6F4, false);
                    guiGraphics.pose().popPose();
                }
            }
            itemY += LIST_ITEM_HEIGHT;
        }

        guiGraphics.disableScissor();

        // スクロールバー
        int maxScroll = getMaxListScroll();
        if (maxScroll > 0) {
            int barTrackX = lx + lw - 3;
            int barTrackY = ly;
            int barTrackH = lh;
            guiGraphics.fill(barTrackX, barTrackY, barTrackX + 3, barTrackY + barTrackH, 0x33000000);

            int thumbH = Math.max(16, (int) ((float) barTrackH * barTrackH / (this.filteredItems.size() * LIST_ITEM_HEIGHT)));
            int thumbY = barTrackY + (int) ((float) this.scrollOffset / maxScroll * (barTrackH - thumbH));
            guiGraphics.fill(barTrackX, thumbY, barTrackX + 3, thumbY + thumbH, 0xFF585B70);
        }
    }

    private void renderRightPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int px = getRightPanelX();
        int py = getRightPanelY();
        int pw = getRightPanelWidth();
        int ph = getRightPanelHeight();

        if (this.currentRightPanel != null) {
            this.currentRightPanel.render(guiGraphics, px, py, pw, ph, mouseX, mouseY, partialTick);
        } else if (this.selectedItem != null && !this.selectedItem.isOverview) {
            renderFallbackPartPanel(guiGraphics, px, py, pw, ph);
        } else {
            renderSystemOverviewPanel(guiGraphics, px, py, pw, ph);
        }
    }

    private void renderSystemOverviewPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        IdeScreenFramework.drawEditorPanelBackground(guiGraphics, x, y, width, height, "System Overview", false);

        int contentX = x + 10;
        int contentY = y + 26;
        int contentW = width - 20;

        BlockEntity be = this.menu.getLevel().getBlockEntity(this.menu.getMasterPos());
        if (!(be instanceof SimpleMachineBlockEntity master)) {
            guiGraphics.drawString(this.font, "Mainframe Master not found.", contentX, contentY, 0xFFFF5555);
            return;
        }

        // コアステータス
        guiGraphics.drawString(this.font, "§lMainframe Status", contentX, contentY, 0xFF89B4FA);
        contentY += 12;
        guiGraphics.drawString(this.font, "State: §aOnline (Assembled)", contentX + 6, contentY, 0xFFCDD6F4);
        contentY += 11;
        guiGraphics.drawString(this.font, "Core Pos: §7" + this.menu.getMasterPos().toShortString(), contentX + 6, contentY, 0xFFCDD6F4);
        contentY += 11;
        guiGraphics.drawString(this.font, "Total Blocks: §f" + (master.mainframeParts.size() + 1), contentX + 6, contentY, 0xFFCDD6F4);
        contentY += 16;

        // エネルギー
        guiGraphics.drawString(this.font, "§lEnergy Storage", contentX, contentY, 0xFFE5C07B);
        contentY += 11;
        long energyStored = master.energyStorage.getEnergyStored();
        long energyMax = master.energyStorage.getMaxEnergyStored();
        drawProgressBar(guiGraphics, contentX, contentY, contentW, 12, energyStored, energyMax, 0xFFE5C07B, "FE");
        contentY += 18;

        // アイテムストレージ
        guiGraphics.drawString(this.font, "§lItem Storage", contentX, contentY, 0xFF61AFEF);
        contentY += 11;
        long itemsStored = master.mainframeStorage.getTotalItems();
        long itemsMax = master.mainframeStorage.getCapacity();
        drawProgressBar(guiGraphics, contentX, contentY, contentW, 12, itemsStored, itemsMax, 0xFF61AFEF, "Items");
        contentY += 18;

        // 有効機能
        guiGraphics.drawString(this.font, "§lActive Features (" + master.getCore().activeFeatures.size() + ")", contentX, contentY, 0xFFC678DD);
        contentY += 11;
        if (master.getCore().activeFeatures.isEmpty()) {
            guiGraphics.drawString(this.font, "§7None", contentX + 6, contentY, 0xFFA6ADC8);
            contentY += 12;
        } else {
            String features = String.join(", ", master.getCore().activeFeatures);
            if (this.font.width(features) > contentW) {
                features = this.font.plainSubstrByWidth(features, contentW - 10) + "...";
            }
            guiGraphics.drawString(this.font, "§f" + features, contentX + 6, contentY, 0xFFCDD6F4);
            contentY += 13;
        }

        // 有効API
        guiGraphics.drawString(this.font, "§lLua APIs (" + master.getCore().activeApis.size() + ")", contentX, contentY, 0xFF98C379);
        contentY += 11;
        if (master.getCore().activeApis.isEmpty()) {
            guiGraphics.drawString(this.font, "§7None", contentX + 6, contentY, 0xFFA6ADC8);
            contentY += 12;
        } else {
            String apis = String.join(", ", master.getCore().activeApis);
            if (this.font.width(apis) > contentW) {
                apis = this.font.plainSubstrByWidth(apis, contentW - 10) + "...";
            }
            guiGraphics.drawString(this.font, "§f" + apis, contentX + 6, contentY, 0xFFCDD6F4);
            contentY += 13;
        }

        // コンポーネント内訳
        if (!master.componentCounts.isEmpty()) {
            guiGraphics.drawString(this.font, "§lComponents Breakdown", contentX, contentY, 0xFF61AFEF);
            contentY += 11;
            int col = 0;
            for (Map.Entry<String, Integer> entry : master.componentCounts.entrySet()) {
                ResourceLocation res = ResourceLocation.tryParse(entry.getKey());
                String blockName = (res != null) ? res.getPath() : entry.getKey();
                String countStr = "• " + blockName + ": " + entry.getValue();
                guiGraphics.drawString(this.font, countStr, contentX + 6 + (col % 2) * 100, contentY, 0xFFCDD6F4);
                if (col % 2 == 1) {
                    contentY += 10;
                }
                col++;
            }
        }
    }

    private void renderFallbackPartPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        String title = (this.selectedItem != null) ? this.selectedItem.title.getString() : "Component Details";
        IdeScreenFramework.drawEditorPanelBackground(guiGraphics, x, y, width, height, title, false);

        if (this.selectedItem == null) return;

        int contentX = x + 10;
        int contentY = y + 28;

        // アイコン + 名前
        guiGraphics.renderFakeItem(this.selectedItem.icon, contentX, contentY);
        guiGraphics.drawString(this.font, this.selectedItem.title, contentX + 22, contentY + 4, 0xFFFFFFFF);
        contentY += 24;

        // 座標
        guiGraphics.drawString(this.font, "§lCoordinates", contentX, contentY, 0xFF89B4FA);
        contentY += 11;
        guiGraphics.drawString(this.font, "World: §7" + this.selectedItem.pos.toShortString(), contentX + 6, contentY, 0xFFCDD6F4);
        contentY += 11;

        BlockPos masterPos = this.menu.getMasterPos();
        int dx = this.selectedItem.pos.getX() - masterPos.getX();
        int dy = this.selectedItem.pos.getY() - masterPos.getY();
        int dz = this.selectedItem.pos.getZ() - masterPos.getZ();
        guiGraphics.drawString(this.font, String.format("Relative: §7ΔX: %+d, ΔY: %+d, ΔZ: %+d", dx, dy, dz), contentX + 6, contentY, 0xFFCDD6F4);
        contentY += 16;

        // コンポーネント情報
        Block block = (this.selectedItem.state != null) ? this.selectedItem.state.getBlock() : null;
        MainframeComponentData data = (block != null) ? MainframeComponentRegistry.get(block) : null;

        guiGraphics.drawString(this.font, "§lComponent Info", contentX, contentY, 0xFFE5C07B);
        contentY += 11;

        if (data != null) {
            guiGraphics.drawString(this.font, "Category: §f" + this.selectedItem.roleTag, contentX + 6, contentY, 0xFFCDD6F4);
            contentY += 11;
            guiGraphics.drawString(this.font, "Max Allowed: §f" + data.getMaxCount(), contentX + 6, contentY, 0xFFCDD6F4);
            contentY += 11;

            if (!data.getFeatures().isEmpty()) {
                guiGraphics.drawString(this.font, "Features: §a" + String.join(", ", data.getFeatures()), contentX + 6, contentY, 0xFFCDD6F4);
                contentY += 11;
            }
            if (!data.getApis().isEmpty()) {
                guiGraphics.drawString(this.font, "APIs: §b" + String.join(", ", data.getApis()), contentX + 6, contentY, 0xFFCDD6F4);
                contentY += 11;
            }
            if (!data.getResourceCapacities().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                data.getResourceCapacities().forEach((k, v) -> sb.append(k).append(": +").append(v).append(" "));
                guiGraphics.drawString(this.font, "Capacities: §e" + sb.toString().trim(), contentX + 6, contentY, 0xFFCDD6F4);
                contentY += 11;
            }
        } else {
            guiGraphics.drawString(this.font, "Category: §f" + this.selectedItem.roleTag, contentX + 6, contentY, 0xFFCDD6F4);
            contentY += 11;
            guiGraphics.drawString(this.font, "§7Structural component", contentX + 6, contentY, 0xFFA6ADC8);
            contentY += 11;
        }

        contentY += 8;
        guiGraphics.drawString(this.font, "§7No configurable settings for this part.", contentX + 6, contentY, 0xFF6C7086);
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height, long current, long max, int barColor, String unit) {
        guiGraphics.fill(x, y, x + width, y + height, 0xFF11111B);
        guiGraphics.renderOutline(x, y, width, height, 0xFF45475A);

        float pct = (max > 0) ? Mth.clamp((float) current / (float) max, 0.0f, 1.0f) : 0.0f;
        int barW = (int) ((width - 2) * pct);
        if (barW > 0) {
            guiGraphics.fill(x + 1, y + 1, x + 1 + barW, y + height - 1, barColor);
        }

        String label = String.format("%,d / %,d %s (%.1f%%)", current, max, unit, pct * 100.0f);
        int textW = this.font.width(label);
        int textX = x + (width - textW) / 2;
        int textY = y + (height - 8) / 2;
        guiGraphics.drawString(this.font, label, textX, textY, 0xFFFFFFFF, true);
    }

    private void renderListTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int lx = getListX();
        int ly = getListY();
        int lw = getListWidth();
        int lh = getListHeight();

        if (mouseX >= lx && mouseX < lx + lw - 6 && mouseY >= ly && mouseY < ly + lh) {
            int hoveredY = mouseY - ly + this.scrollOffset;
            int index = hoveredY / LIST_ITEM_HEIGHT;
            if (index >= 0 && index < this.filteredItems.size()) {
                PartItem item = this.filteredItems.get(index);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(item.title);
                if (item.isOverview) {
                    tooltip.add(Component.literal("Click to view complete system status").withStyle(net.minecraft.ChatFormatting.GRAY));
                } else {
                    tooltip.add(Component.literal("Location: " + item.pos.toShortString()).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
                    tooltip.add(Component.literal("Category: " + item.roleTag).withStyle(net.minecraft.ChatFormatting.YELLOW));
                    if (item.isMaster) {
                        tooltip.add(Component.literal("Role: Core Controller").withStyle(net.minecraft.ChatFormatting.GREEN));
                    }
                }
                guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // スクロールバードラッグ
        int maxScroll = getMaxListScroll();
        if (maxScroll > 0) {
            int barX = getListX() + getListWidth() - 6;
            if (mouseX >= barX && mouseX <= barX + 6 && mouseY >= getListY() && mouseY <= getListY() + getListHeight()) {
                this.isDraggingScrollBar = true;
                return true;
            }
        }

        // リスト項目クリック
        if (mouseX >= getListX() && mouseX < getListX() + getListWidth() && mouseY >= getListY() && mouseY < getListY() + getListHeight()) {
            int clickedY = (int) (mouseY - getListY() + this.scrollOffset);
            int index = clickedY / LIST_ITEM_HEIGHT;
            if (index >= 0 && index < this.filteredItems.size()) {
                selectItem(this.filteredItems.get(index));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        // 右パネルクリック
        if (this.currentRightPanel != null) {
            if (this.currentRightPanel.mouseClicked(getRightPanelX(), getRightPanelY(), getRightPanelWidth(), getRightPanelHeight(), mouseX, mouseY, button)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingScrollBar) {
            int maxScroll = getMaxListScroll();
            if (maxScroll > 0) {
                float ratio = (float) dragY / (float) getListHeight();
                this.scrollOffset = Mth.clamp(this.scrollOffset + (int) (ratio * (this.filteredItems.size() * LIST_ITEM_HEIGHT)), 0, maxScroll);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingScrollBar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= getListX() && mouseX <= getListX() + getListWidth() && mouseY >= getListY() && mouseY <= getListY() + getListHeight()) {
            int maxScroll = getMaxListScroll();
            this.scrollOffset = Mth.clamp(this.scrollOffset - (int) (scrollY * LIST_ITEM_HEIGHT), 0, maxScroll);
            return true;
        }

        if (this.currentRightPanel != null) {
            if (mouseX >= getRightPanelX() && mouseX <= getRightPanelX() + getRightPanelWidth() && mouseY >= getRightPanelY() && mouseY <= getRightPanelY() + getRightPanelHeight()) {
                return this.currentRightPanel.mouseScrolled(getRightPanelX(), getRightPanelY(), getRightPanelWidth(), getRightPanelHeight(), mouseX, mouseY, scrollX, scrollY);
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused() && this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.currentRightPanel != null && this.currentRightPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused() && this.searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (this.currentRightPanel != null && this.currentRightPanel.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {}

    /**
     * パーツリストの各エントリ用データクラス
     */
    public static class PartItem {
        public final BlockPos pos;
        public final boolean isOverview;
        public final boolean isMaster;
        public final ItemStack icon;
        public final Component title;
        public final String subText;
        public final String roleTag;
        public final BlockState state;

        public PartItem(BlockPos pos, boolean isOverview, boolean isMaster, ItemStack icon, Component title, String subText, String roleTag, BlockState state) {
            this.pos = pos;
            this.isOverview = isOverview;
            this.isMaster = isMaster;
            this.icon = icon;
            this.title = title;
            this.subText = subText;
            this.roleTag = roleTag;
            this.state = state;
        }
    }
}