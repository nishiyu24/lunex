package com.nishiyu.lunex.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nishiyu.lunex.api.client.*;
import com.nishiyu.lunex.api.client.ui.panel.AbstractRightPanel;
import com.nishiyu.lunex.api.client.ui.panel.MachineOverviewUIExtension;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class MainframeOverviewScreen extends AbstractContainerScreen<MainframeOverviewMenu> {

    public static BlockPos lastSelectedPos = null;
    private BlockPos selectedPos = null;

    private final IdeScreenFramework uiFramework;
    private static final int TOP_BAR_HEIGHT = 22;

    private int virtualWidth;
    private int virtualHeight;
    private int startX;
    private int startY;
    private int guiWidth;
    private int guiHeight;

    private static BlockPos lastMasterPos3D = null;
    private static float savedRenderScale = 30F;
    private static float savedYaw = 45f, savedPitch = 30f;
    private static boolean savedIsExploded = false;
    private static boolean savedScaleInitialized = false;

    private float renderScale;
    private boolean scaleInitialized;
    private float yaw, pitch;
    private boolean isDraggingView = false;
    private boolean isExploded;
    private float currentExplodeOffset = 0.0f;

    // 絞り込み用の変数
    private Block filterBlock = null;

    // プルダウンメニュー用の変数
    private boolean isDropdownOpen = false;
    private final List<Block> availableBlocksForFilter = new ArrayList<>();

    public BlockPos getSelectedPos() { return this.selectedPos; }
    public int getGuiStartX() { return this.startX; }
    public int getGuiStartY() { return this.startY; }
    public int getGuiWidth() { return this.guiWidth; }
    public int getGuiHeight() { return this.guiHeight; }
    public IdeScreenFramework getUiFramework() { return this.uiFramework; }

    public void setFilterBlock(Block block) { this.filterBlock = block; }
    public Block getFilterBlock() { return this.filterBlock; }

    public MainframeOverviewScreen(MainframeOverviewMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.uiFramework = new IdeScreenFramework();
    }

    private int getVpX() { return 0; }
    private int getVpY() { return TOP_BAR_HEIGHT; }
    private int getVpWidth() { return this.virtualWidth - uiFramework.rightWidth; }
    private int getVpHeight() { return this.virtualHeight - TOP_BAR_HEIGHT - uiFramework.bottomHeight; }

    @Override
    protected void init() {
        super.init();

        int maxGuiWidth = 650;
        int maxGuiHeight = 400;

        this.guiWidth = Math.min(maxGuiWidth, this.width - 40);
        this.guiHeight = Math.min(maxGuiHeight, this.height - 40);

        this.startX = (this.width - this.guiWidth) / 2;
        this.startY = (this.height - this.guiHeight) / 2;

        IdeScreenFramework.guiOffsetX = this.startX;
        IdeScreenFramework.guiOffsetY = this.startY;
        IdeScreenFramework.currentUiScale = 1.0f;

        this.virtualWidth = this.guiWidth;
        this.virtualHeight = this.guiHeight;

        this.leftPos = this.startX;
        this.topPos = this.startY;
        this.imageWidth = this.guiWidth;
        this.imageHeight = this.guiHeight;

        this.titleLabelX = 9999; this.titleLabelY = 9999;
        this.inventoryLabelX = 9999; this.inventoryLabelY = 9999;

        if (this.selectedPos == null && lastSelectedPos != null) {
            this.selectedPos = lastSelectedPos;
        }

        this.uiFramework.updateLayoutConstraints(this.virtualWidth, this.virtualHeight);

        if (!this.menu.getMasterPos().equals(lastMasterPos3D)) {
            lastMasterPos3D = this.menu.getMasterPos();
            savedRenderScale = 30F;
            savedYaw = 45f;
            savedPitch = 30f;
            savedIsExploded = false;
            savedScaleInitialized = false;
        }
        this.renderScale = savedRenderScale;
        this.yaw = savedYaw;
        this.pitch = savedPitch;
        this.isExploded = savedIsExploded;
        this.scaleInitialized = savedScaleInitialized;
        this.currentExplodeOffset = this.isExploded ? 1.0f : 0.0f;

        init3DScale(getVpWidth(), getVpHeight());

        updateAvailableFilters();

        BlockEntity masterBe = this.menu.getLevel().getBlockEntity(this.menu.getMasterPos());
        this.uiFramework.clearBottomTabs();

        if (masterBe instanceof SimpleMachineBlockEntity master) {
            List<IdeScreenFramework.IBottomTab> dynamicTabs = MainframeBottomTabRegistry.getTabsFor(master, this.menu.getLevel());
            for (IdeScreenFramework.IBottomTab tab : dynamicTabs) {
                this.uiFramework.addBottomTab(tab);
            }
        }
        rebuildUI();
    }

    private void updateAvailableFilters() {
        this.availableBlocksForFilter.clear();
        List<BlockPos> parts = get3DParts();
        for (BlockPos p : parts) {
            BlockState state = this.menu.getLevel().getBlockState(p);
            if (state.isAir()) continue;

            BlockEntity be = this.menu.getLevel().getBlockEntity(p);
            if (be instanceof MainframeAdapterBlockEntity adapter && adapter.getOriginalState() != null) {
                state = adapter.getOriginalState();
            }

            Block b = state.getBlock();
            String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath().toLowerCase();

            if (!blockId.contains("frame")) {
                if (!this.availableBlocksForFilter.contains(b)) {
                    this.availableBlocksForFilter.add(b);
                }
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.currentExplodeOffset += ((isExploded ? 1.0f : 0.0f) - currentExplodeOffset) * 0.2f;
    }

    public void rebuildUI() {
        if (this.selectedPos == null) {
            // 未選択時はマスターブロックの情報を元に SimpleMachineUIExtension を汎用UIとして表示
            BlockPos masterPos = this.menu.getMasterPos();
            BlockEntity masterBe = this.menu.getLevel().getBlockEntity(masterPos);
            this.uiFramework.setRightPanel(new MachineOverviewUIExtension(masterPos, masterBe));
        } else {
            // 選択時はそのブロック専用のUIを表示
            BlockEntity be = this.menu.getLevel().getBlockEntity(this.selectedPos);
            this.uiFramework.setRightPanel(MainframeUIRegistry.createRightPanel(this.selectedPos, be));
        }
    }

    private void selectBlock(BlockPos pos) {
        this.selectedPos = pos;
        if (pos != null) {
            BlockEntity be = this.menu.getLevel().getBlockEntity(pos);
            // Screenの場合はマスターにリダイレクト
            if (be instanceof ScreenBlockEntity screenBe && screenBe.masterPos != null) {
                this.selectedPos = screenBe.masterPos;
            }
        }
        lastSelectedPos = this.selectedPos;
        rebuildUI();
    }

    public void renderBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}
    public void renderBackground(GuiGraphics guiGraphics) {}

    // ★追加: スロットを画面外に退避させるメソッド
    private void hidePlayerSlots() {
        int startIndex = this.menu.slots.size() - 36;
        if (startIndex >= 0) {
            for (int i = startIndex; i < this.menu.slots.size(); i++) {
                net.minecraft.world.inventory.Slot slot = this.menu.slots.get(i);
                slot.x = -10000;
                slot.y = -10000;
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        hidePlayerSlots();

        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);

        guiGraphics.fill(this.startX, this.startY, this.startX + this.guiWidth, this.startY + this.guiHeight, 0xFF1E1E1E);
        guiGraphics.renderOutline(this.startX - 1, this.startY - 1, this.guiWidth + 2, this.guiHeight + 2, 0xFF555555);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.startX, this.startY, 0);

        double relMouseX = mouseX - this.startX;
        double relMouseY = mouseY - this.startY;
        int vMouseX = (int) relMouseX;
        int vMouseY = (int) relMouseY;

        guiGraphics.fill(0, 0, this.virtualWidth, TOP_BAR_HEIGHT, 0xFF3C3C3C);
        guiGraphics.drawString(this.font, "LUNEX MAINFRAME EDITOR", 10, 6, 0xFFD4D4D4);

        if (!uiFramework.hasCustomCenterPanel()) {
            render3DView(guiGraphics, getVpX(), getVpY(), getVpWidth(), getVpHeight(), vMouseX, vMouseY);
            renderFilterDropdown(guiGraphics, vMouseX, vMouseY);
        }

        uiFramework.render(guiGraphics, vMouseX, vMouseY, partialTick, this.virtualWidth, this.virtualHeight, TOP_BAR_HEIGHT);

        guiGraphics.pose().popPose();
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (relMouseX >= 0 && relMouseX <= this.guiWidth && relMouseY >= 0 && relMouseY <= this.guiHeight) {
            this.renderTooltip(guiGraphics, mouseX, mouseY);

            if (!uiFramework.hasCustomCenterPanel()) {
                int btnX = getVpX() + getVpWidth() - 30;
                int btnY = getVpY() + 10;
                if (vMouseX >= btnX && vMouseX <= btnX + 20 && vMouseY >= btnY && vMouseY <= btnY + 20) {
                    guiGraphics.renderTooltip(this.font, Component.literal(this.isExploded ? "Collapse View" : "Explode View"), mouseX, mouseY);
                }
            }
        }
    }

    private void renderFilterDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int ddWidth = 130;
        int ddHeight = 20;
        int ddX = getVpX() + getVpWidth() - 35 - ddWidth;
        int ddY = getVpY() + 10;

        boolean isHovered = mouseX >= ddX && mouseX <= ddX + ddWidth && mouseY >= ddY && mouseY <= ddY + ddHeight;
        guiGraphics.fill(ddX, ddY, ddX + ddWidth, ddY + ddHeight, isHovered ? 0xFF555555 : 0xFF333333);
        guiGraphics.renderOutline(ddX, ddY, ddWidth, ddHeight, 0xFF6A6A6A);

        String currentName = this.filterBlock == null ? "All Blocks" : this.filterBlock.getName().getString();
        if (this.font.width(currentName) > ddWidth - 25) {
            currentName = this.font.plainSubstrByWidth(currentName, ddWidth - 30) + "...";
        }
        guiGraphics.drawString(this.font, currentName, ddX + 5, ddY + 6, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, isDropdownOpen ? "▲" : "▼", ddX + ddWidth - 12, ddY + 6, 0xFFAAAAAA);

        if (isDropdownOpen) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 500);

            int listY = ddY + ddHeight;
            int itemHeight = 16;
            int listHeight = (availableBlocksForFilter.size() + 1) * itemHeight;

            guiGraphics.fill(ddX, listY, ddX + ddWidth, listY + listHeight, 0xFF2A2A2A);
            guiGraphics.renderOutline(ddX, listY, ddWidth, listHeight, 0xFF6A6A6A);

            boolean hoverAll = mouseX >= ddX && mouseX <= ddX + ddWidth && mouseY >= listY && mouseY < listY + itemHeight;
            if (hoverAll) guiGraphics.fill(ddX + 1, listY + 1, ddX + ddWidth - 1, listY + itemHeight - 1, 0xFF555555);
            guiGraphics.drawString(this.font, "All Blocks", ddX + 5, listY + 4, this.filterBlock == null ? 0xFF00E5FF : 0xFFFFFFFF);

            for (int i = 0; i < availableBlocksForFilter.size(); i++) {
                int itemY = listY + itemHeight * (i + 1);
                Block b = availableBlocksForFilter.get(i);
                boolean hoverItem = mouseX >= ddX && mouseX <= ddX + ddWidth && mouseY >= itemY && mouseY < itemY + itemHeight;
                if (hoverItem) guiGraphics.fill(ddX + 1, itemY + 1, ddX + ddWidth - 1, itemY + itemHeight - 1, 0xFF555555);

                String bName = b.getName().getString();
                if (this.font.width(bName) > ddWidth - 10) {
                    bName = this.font.plainSubstrByWidth(bName, ddWidth - 15) + "...";
                }
                guiGraphics.drawString(this.font, bName, ddX + 5, itemY + 4, this.filterBlock == b ? 0xFF00E5FF : 0xFFFFFFFF);
            }
            guiGraphics.pose().popPose();
        }
    }

    private boolean handleDropdownClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int ddWidth = 130;
        int ddHeight = 20;
        int ddX = getVpX() + getVpWidth() - 35 - ddWidth;
        int ddY = getVpY() + 10;

        if (isDropdownOpen) {
            int listY = ddY + ddHeight;
            int itemHeight = 16;
            int listHeight = (availableBlocksForFilter.size() + 1) * itemHeight;

            if (mouseX >= ddX && mouseX <= ddX + ddWidth && mouseY >= listY && mouseY <= listY + listHeight) {
                int clickedIndex = (int) ((mouseY - listY) / itemHeight);
                if (clickedIndex == 0) {
                    setFilterBlock(null);
                } else if (clickedIndex - 1 < availableBlocksForFilter.size()) {
                    setFilterBlock(availableBlocksForFilter.get(clickedIndex - 1));
                }
                isDropdownOpen = false;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        if (mouseX >= ddX && mouseX <= ddX + ddWidth && mouseY >= ddY && mouseY <= ddY + ddHeight) {
            isDropdownOpen = !isDropdownOpen;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        return false;
    }

    // ==========================================
    // ▼▼▼ 3D View 組み込み処理 ▼▼▼
    // ==========================================

    private void save3DState() {
        savedRenderScale = this.renderScale;
        savedYaw = this.yaw;
        savedPitch = this.pitch;
        savedIsExploded = this.isExploded;
        savedScaleInitialized = this.scaleInitialized;
    }

    private List<BlockPos> get3DParts() {
        List<BlockPos> parts = new ArrayList<>();
        BlockEntity masterBe = this.menu.getLevel().getBlockEntity(this.menu.getMasterPos());
        if (masterBe instanceof SimpleMachineBlockEntity master && !master.mainframeParts.isEmpty()) {
            parts.addAll(master.mainframeParts);
        }
        return parts;
    }

    private void init3DScale(int vpWidth, int vpHeight) {
        if (this.scaleInitialized) return;
        List<BlockPos> parts = get3DParts();
        if (parts.isEmpty()) return;
        float cx = 0, cy = 0, cz = 0, maxDist = 1.0f;
        for (BlockPos p : parts) { cx += p.getX(); cy += p.getY(); cz += p.getZ(); }
        cx /= parts.size(); cy /= parts.size(); cz /= parts.size();
        for (BlockPos p : parts) {
            maxDist = Math.max(maxDist, Math.abs(p.getX() - cx));
            maxDist = Math.max(maxDist, Math.abs(p.getY() - cy));
            maxDist = Math.max(maxDist, Math.abs(p.getZ() - cz));
        }
        maxDist = Math.max(1.0f, maxDist);
        int minDimension = Math.min(vpWidth, vpHeight);
        this.renderScale = Mth.clamp(minDimension / (maxDist * 2.5f), 5f, 100f);
        this.scaleInitialized = true;
        save3DState();
    }

    private void render3DView(GuiGraphics guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        List<BlockPos> parts = get3DParts();
        if (parts.isEmpty()) return;

        float cx = 0, cy = 0, cz = 0;
        for (BlockPos p : parts) { cx += p.getX(); cy += p.getY(); cz += p.getZ(); }
        cx /= parts.size(); cy /= parts.size(); cz /= parts.size();

        int absX = this.startX + x;
        int absY = this.startY + y;
        guiGraphics.enableScissor(absX, absY, absX + width, absY + height);

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(x + (width / 2.0f), y + (height / 2.0f), 200);
        poseStack.scale(renderScale, -renderScale, renderScale);
        poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw)));

        BlockRenderDispatcher renderer = Minecraft.getInstance().getBlockRenderer();

        // ------------------------------------------------------------------
        // パス1: 対象のブロック（または全て）を通常通り描画
        // ------------------------------------------------------------------
        for (BlockPos p : parts) {
            BlockState state = this.menu.getLevel().getBlockState(p);
            if (state.isAir()) continue;

            BlockEntity be = this.menu.getLevel().getBlockEntity(p);
            if (be instanceof MainframeAdapterBlockEntity adapter && adapter.getOriginalState() != null) {
                state = adapter.getOriginalState();
            }

            // 絞り込み中であり、かつ対象外のブロックならスキップ (パス2で半透明描画する)
            boolean isFiltered = this.filterBlock != null && state.getBlock() != this.filterBlock;
            if (isFiltered) continue;

            float dx = p.getX() - cx;
            float dy = p.getY() - cy;
            float dz = p.getZ() - cz;
            float renderX = dx - 0.5f + (dx * currentExplodeOffset * 0.8f);
            float renderY = dy - 0.5f + (dy * currentExplodeOffset * 0.8f);
            float renderZ = dz - 0.5f + (dz * currentExplodeOffset * 0.8f);

            poseStack.pushPose();
            poseStack.translate(renderX, renderY, renderZ);
            renderer.renderSingleBlock(state, poseStack, guiGraphics.bufferSource(), 15728880, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
            poseStack.popPose();
        }
        guiGraphics.flush();

        // ------------------------------------------------------------------
        // パス2: 対象外のブロックを強制的に半透明として描画
        // ------------------------------------------------------------------
        if (this.filterBlock != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // 透過度設定（1.0f, 1.0f, 1.0f = 色味変更なし、0.25f = 25%の不透明度）
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.25f);
            // 透過ブロック同士で描画順による欠けを防ぐため深度マスクを無効化
            RenderSystem.depthMask(false);

            // 強制的に半透明レンダータイプを返すラッパーを用意し、アルファブレンディングを有効にする
            MultiBufferSource translucentSource = rt -> guiGraphics.bufferSource().getBuffer(RenderType.translucent());

            for (BlockPos p : parts) {
                BlockState state = this.menu.getLevel().getBlockState(p);
                if (state.isAir()) continue;

                BlockEntity be = this.menu.getLevel().getBlockEntity(p);
                if (be instanceof MainframeAdapterBlockEntity adapter && adapter.getOriginalState() != null) {
                    state = adapter.getOriginalState();
                }

                boolean isFiltered = state.getBlock() != this.filterBlock;
                if (!isFiltered) continue;

                float dx = p.getX() - cx;
                float dy = p.getY() - cy;
                float dz = p.getZ() - cz;
                float renderX = dx - 0.5f + (dx * currentExplodeOffset * 0.8f);
                float renderY = dy - 0.5f + (dy * currentExplodeOffset * 0.8f);
                float renderZ = dz - 0.5f + (dz * currentExplodeOffset * 0.8f);

                poseStack.pushPose();
                poseStack.translate(renderX, renderY, renderZ);
                renderer.renderSingleBlock(state, poseStack, translucentSource, 15728880, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
                poseStack.popPose();
            }

            // バッチをフラッシュして状態を元に戻す
            guiGraphics.bufferSource().endBatch(RenderType.translucent());
            RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }

        // ------------------------------------------------------------------
        // パス3: 選択中ブロックのハイライト枠を描画 (常に最前面へ描画)
        // ------------------------------------------------------------------
        if (this.selectedPos != null && parts.contains(this.selectedPos)) {
            float dx = this.selectedPos.getX() - cx;
            float dy = this.selectedPos.getY() - cy;
            float dz = this.selectedPos.getZ() - cz;
            float renderX = dx - 0.5f + (dx * currentExplodeOffset * 0.8f);
            float renderY = dy - 0.5f + (dy * currentExplodeOffset * 0.8f);
            float renderZ = dz - 0.5f + (dz * currentExplodeOffset * 0.8f);

            poseStack.pushPose();
            poseStack.translate(renderX, renderY, renderZ);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            VertexConsumer highlight = guiGraphics.bufferSource().getBuffer(RenderType.gui());
            for (Direction dir : Direction.values()) {
                drawFaceHighlightColor(poseStack, highlight, dir, 50, 255, 50, 100);
            }
            guiGraphics.flush();
            RenderSystem.disableBlend();

            poseStack.popPose();
        }

        poseStack.popPose();

        // ------------------------------------------------------------------
        // 3Dビュー内ボタンの描画
        // ------------------------------------------------------------------
        int btnX = x + width - 30;
        int btnY = y + 10;
        boolean isHovered = mouseX >= btnX && mouseX <= btnX + 20 && mouseY >= btnY && mouseY <= btnY + 20;

        guiGraphics.fill(btnX, btnY, btnX + 20, btnY + 20, isHovered ? 0xFF555555 : 0xFF333333);
        guiGraphics.renderOutline(btnX, btnY, 20, 20, 0xFF6A6A6A);

        int color = isExploded ? 0xFF00E5FF : 0xFFFFFFFF;
        if (isExploded) {
            guiGraphics.fill(btnX + 4, btnY + 4, btnX + 8, btnY + 8, color);
            guiGraphics.fill(btnX + 12, btnY + 4, btnX + 16, btnY + 8, color);
            guiGraphics.fill(btnX + 4, btnY + 12, btnX + 8, btnY + 16, color);
            guiGraphics.fill(btnX + 12, btnY + 12, btnX + 16, btnY + 16, color);
        } else {
            guiGraphics.fill(btnX + 6, btnY + 6, btnX + 14, btnY + 14, color);
        }

        guiGraphics.flush();
        guiGraphics.disableScissor();
    }

    private BlockPos pick3DBlock(int x, int y, int width, int height, double mouseX, double mouseY) {
        List<BlockPos> parts = get3DParts();
        if (parts.isEmpty()) return null;

        float cx = 0, cy = 0, cz = 0;
        for (BlockPos p : parts) { cx += p.getX(); cy += p.getY(); cz += p.getZ(); }
        cx /= parts.size(); cy /= parts.size(); cz /= parts.size();

        BlockPos hitPos = null;
        float maxZ = -Float.MAX_VALUE;

        for (BlockPos p : parts) {
            BlockState state = this.menu.getLevel().getBlockState(p);
            if (state.isAir()) continue;

            BlockEntity be = this.menu.getLevel().getBlockEntity(p);
            if (be instanceof MainframeAdapterBlockEntity adapter && adapter.getOriginalState() != null) {
                state = adapter.getOriginalState();
            }

            if (this.filterBlock != null && state.getBlock() != this.filterBlock) {
                continue;
            }

            Matrix4f matrix = new Matrix4f();
            matrix.translate(x + (width / 2.0f), y + (height / 2.0f), 200);
            matrix.scale(renderScale, -renderScale, renderScale);
            Quaternionf rotation = new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
            matrix.rotate(rotation);

            float dx = p.getX() - cx;
            float dy = p.getY() - cy;
            float dz = p.getZ() - cz;
            float renderX = dx - 0.5f + (dx * currentExplodeOffset * 0.8f);
            float renderY = dy - 0.5f + (dy * currentExplodeOffset * 0.8f);
            float renderZ = dz - 0.5f + (dz * currentExplodeOffset * 0.8f);

            matrix.translate(renderX, renderY, renderZ);

            for (Direction dir : Direction.values()) {
                Vector3f[] corners = getFaceCorners(dir);
                Vector3f[] projected = new Vector3f[4];
                float avgZ = 0;
                for (int i = 0; i < 4; i++) {
                    Vector4f vec = new Vector4f(corners[i].x, corners[i].y, corners[i].z, 1.0f);
                    matrix.transform(vec);
                    projected[i] = new Vector3f(vec.x, vec.y, vec.z);
                    avgZ += vec.z;
                }
                avgZ /= 4f;

                if (isPointInQuad(mouseX, mouseY, projected) && avgZ > maxZ) {
                    maxZ = avgZ;
                    hitPos = p;
                }
            }
        }
        return hitPos;
    }

    private void drawFaceHighlightColor(PoseStack poseStack, VertexConsumer buffer, Direction face, int r, int g, int b, int a) {
        Matrix4f pose = poseStack.last().pose();
        float offset = 0.005f;
        switch (face) {
            case NORTH -> { addVertex(buffer, pose, 1, 1, -offset, r, g, b, a); addVertex(buffer, pose, 1, 0, -offset, r, g, b, a); addVertex(buffer, pose, 0, 0, -offset, r, g, b, a); addVertex(buffer, pose, 0, 1, -offset, r, g, b, a); }
            case SOUTH -> { addVertex(buffer, pose, 0, 1, 1 + offset, r, g, b, a); addVertex(buffer, pose, 0, 0, 1 + offset, r, g, b, a); addVertex(buffer, pose, 1, 0, 1 + offset, r, g, b, a); addVertex(buffer, pose, 1, 1, 1 + offset, r, g, b, a); }
            case WEST -> { addVertex(buffer, pose, -offset, 1, 0, r, g, b, a); addVertex(buffer, pose, -offset, 0, 0, r, g, b, a); addVertex(buffer, pose, -offset, 0, 1, r, g, b, a); addVertex(buffer, pose, -offset, 1, 1, r, g, b, a); }
            case EAST -> { addVertex(buffer, pose, 1 + offset, 1, 1, r, g, b, a); addVertex(buffer, pose, 1 + offset, 0, 1, r, g, b, a); addVertex(buffer, pose, 1 + offset, 0, 0, r, g, b, a); addVertex(buffer, pose, 1 + offset, 1, 0, r, g, b, a); }
            case UP -> { addVertex(buffer, pose, 0, 1 + offset, 0, r, g, b, a); addVertex(buffer, pose, 0, 1 + offset, 1, r, g, b, a); addVertex(buffer, pose, 1, 1 + offset, 1, r, g, b, a); addVertex(buffer, pose, 1, 1 + offset, 0, r, g, b, a); }
            case DOWN -> { addVertex(buffer, pose, 1, -offset, 0, r, g, b, a); addVertex(buffer, pose, 1, -offset, 1, r, g, b, a); addVertex(buffer, pose, 0, -offset, 1, r, g, b, a); addVertex(buffer, pose, 0, -offset, 0, r, g, b, a); }
        }
    }

    private void addVertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, int r, int g, int b, int a) { buffer.addVertex(pose, x, y, z).setColor(r, g, b, a); }

    private Vector3f[] getFaceCorners(Direction dir) {
        return switch (dir) {
            case UP -> new Vector3f[]{new Vector3f(0, 1, 0), new Vector3f(1, 1, 0), new Vector3f(1, 1, 1), new Vector3f(0, 1, 1)};
            case DOWN -> new Vector3f[]{new Vector3f(0, 0, 0), new Vector3f(0, 0, 1), new Vector3f(1, 0, 1), new Vector3f(1, 0, 0)};
            case NORTH -> new Vector3f[]{new Vector3f(0, 0, 0), new Vector3f(0, 1, 0), new Vector3f(1, 1, 0), new Vector3f(1, 0, 0)};
            case SOUTH -> new Vector3f[]{new Vector3f(1, 0, 1), new Vector3f(1, 1, 1), new Vector3f(0, 1, 1), new Vector3f(0, 0, 1)};
            case WEST -> new Vector3f[]{new Vector3f(0, 0, 0), new Vector3f(0, 0, 1), new Vector3f(0, 1, 1), new Vector3f(0, 1, 0)};
            case EAST -> new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(1, 1, 0), new Vector3f(1, 1, 1), new Vector3f(1, 0, 1)};
        };
    }

    private boolean isPointInQuad(double px, double py, Vector3f[] corners) {
        boolean hasPos = false, hasNeg = false;
        for (int i = 0; i < 4; i++) {
            Vector3f p1 = corners[i], p2 = corners[(i + 1) % 4];
            double cross = (px - p1.x) * (p2.y - p1.y) - (py - p1.y) * (p2.x - p1.x);
            if (cross > 0.001) hasPos = true;
            if (cross < -0.001) hasNeg = true;
        }
        return !(hasPos && hasNeg);
    }

    // ==========================================
    // ▼▼▼ マウス・キー入力イベント処理 ▼▼▼
    // ==========================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double relMouseX = mouseX - this.startX;
        double relMouseY = mouseY - this.startY;

        if (relMouseX < 0 || relMouseX > this.guiWidth || relMouseY < 0 || relMouseY > this.guiHeight) {
            if (isDropdownOpen) isDropdownOpen = false;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (uiFramework.mouseClicked(relMouseX, relMouseY, button, this.virtualWidth, this.virtualHeight, TOP_BAR_HEIGHT)) {
            if (isDropdownOpen) isDropdownOpen = false;
            return true;
        }

        if (!uiFramework.hasCustomCenterPanel()) {
            if (handleDropdownClick(relMouseX, relMouseY, button)) {
                return true;
            }

            if (isDropdownOpen) {
                isDropdownOpen = false;
                return true;
            }

            if (isMouseInViewport(relMouseX, relMouseY)) {
                int btnX = getVpX() + getVpWidth() - 30;
                int btnY = getVpY() + 10;
                if (relMouseX >= btnX && relMouseX <= btnX + 20 && relMouseY >= btnY && relMouseY <= btnY + 20) {
                    if (button == 0) {
                        this.isExploded = !this.isExploded;
                        save3DState();
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                }
                if (button == 0) this.isDraggingView = true;
                if (button == 0 || button == 1) {
                    BlockPos hitPos = pick3DBlock(getVpX(), getVpY(), getVpWidth(), getVpHeight(), relMouseX, relMouseY);
                    if (hitPos != null) {
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        selectBlock(hitPos);
                        return true;
                    } else {
                        if (this.selectedPos != null) {
                            selectBlock(null);
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double relMouseX = mouseX - this.startX;
        double relMouseY = mouseY - this.startY;

        if (relMouseX >= 0 && relMouseX <= this.guiWidth && relMouseY >= 0 && relMouseY <= this.guiHeight) {
            if (uiFramework.mouseReleased(relMouseX, relMouseY, button, this.virtualWidth, this.virtualHeight, TOP_BAR_HEIGHT)) return true;
            if (!uiFramework.hasCustomCenterPanel() && button == 0) this.isDraggingView = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double relMouseX = mouseX - this.startX;
        double relMouseY = mouseY - this.startY;

        if (relMouseX >= 0 && relMouseX <= this.guiWidth && relMouseY >= 0 && relMouseY <= this.guiHeight) {
            if (uiFramework.mouseDragged(relMouseX, relMouseY, button, dragX, dragY, this.virtualWidth, this.virtualHeight, TOP_BAR_HEIGHT)) {
                rebuildUI();
                return true;
            }
            if (!uiFramework.hasCustomCenterPanel() && isMouseInViewport(relMouseX, relMouseY)) {
                if (this.isDraggingView && button == 0) {
                    this.yaw += (float) dragX;
                    this.pitch = Mth.clamp(this.pitch + (float) dragY, -90f, 90f);
                    save3DState();
                    return true;
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double relMouseX = mouseX - this.startX;
        double relMouseY = mouseY - this.startY;

        if (relMouseX >= 0 && relMouseX <= this.guiWidth && relMouseY >= 0 && relMouseY <= this.guiHeight) {
            if (uiFramework.mouseScrolled(relMouseX, relMouseY, scrollX, scrollY, this.virtualWidth, this.virtualHeight, TOP_BAR_HEIGHT)) return true;
            if (!uiFramework.hasCustomCenterPanel() && isMouseInViewport(relMouseX, relMouseY)) {
                this.renderScale = Mth.clamp(this.renderScale + (float) (scrollY * 2.0f), 5f, 100f);
                save3DState();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.uiFramework.keyPressed(keyCode, scanCode, modifiers, this.virtualWidth, this.virtualHeight, TOP_BAR_HEIGHT)) return true;
        if (this.uiFramework.getRightPanel() instanceof AbstractRightPanel rp && rp.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.uiFramework.charTyped(codePoint, modifiers, this.virtualWidth, this.virtualHeight, TOP_BAR_HEIGHT)) return true;
        if (this.uiFramework.getRightPanel() instanceof AbstractRightPanel rp && rp.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    private boolean isMouseInViewport(double mouseX, double mouseY) {
        return mouseX >= getVpX() && mouseX <= getVpX() + getVpWidth() && mouseY >= getVpY() && mouseY <= getVpY() + getVpHeight();
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float v, int i, int i1) {
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        return false;
    }
}