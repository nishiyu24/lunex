package com.nishiyu.lunex.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nishiyu.lunex.api.client.IMainframeUIExtension;
import com.nishiyu.lunex.api.client.MainframeUIRegistry;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.*;

public class MainframeOverviewScreen extends AbstractContainerScreen<MainframeOverviewMenu> {

    private float renderScale = 30F;
    private float yaw = 45f;
    private float pitch = 30f;
    private boolean isDragging = false;

    private BlockPos selectedPos = null;
    private final List<AbstractWidget> dynamicWidgets = new ArrayList<>();

    public MainframeOverviewScreen(MainframeOverviewMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 0;
        this.imageHeight = 0;
    }

    @Override
    protected void init() {
        super.init();
    }

    private List<BlockPos> getParts() {
        List<BlockPos> parts = new ArrayList<>();
        Level level = this.menu.getLevel();
        BlockEntity masterBe = level.getBlockEntity(this.menu.getMasterPos());

        if (masterBe instanceof SimpleMachineBlockEntity master && !master.mainframeParts.isEmpty()) {
            parts.addAll(master.mainframeParts);
        } else {
            Queue<BlockPos> queue = new LinkedList<>();
            Set<BlockPos> visited = new HashSet<>();
            BlockPos startPos = this.menu.getMasterPos();

            queue.add(startPos);
            visited.add(startPos);

            while(!queue.isEmpty()) {
                BlockPos curr = queue.poll();
                parts.add(curr);
                for(Direction dir : Direction.values()) {
                    BlockPos neighbor = curr.relative(dir);
                    if(!visited.contains(neighbor)) {
                        BlockState nState = level.getBlockState(neighbor);
                        Property<?> prop = nState.getBlock().getStateDefinition().getProperty("assembled");
                        if (prop instanceof BooleanProperty boolProp && nState.getValue(boolProp)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return parts;
    }

    private void clearDynamicWidgets() {
        for (AbstractWidget widget : dynamicWidgets) {
            this.removeWidget(widget);
        }
        dynamicWidgets.clear();
    }

    private void buildDynamicUI(BlockPos pos, BlockEntity be) {
        clearDynamicWidgets();
        if (pos == null || be == null) return;

        int panelX = (int) (this.width / 1.5f);
        int textY = this.height / 5 + 40;

        // レジストリからUI拡張を取得し、ウィジェット構築を委譲
        IMainframeUIExtension<BlockEntity> extension = MainframeUIRegistry.get(be);
        if (extension != null) {
            extension.buildWidgets(this, pos, be, panelX, textY, widget -> {
                this.addRenderableWidget(widget);
                dynamicWidgets.add(widget);
            });
        }
    }

    // (マウス操作、キーボード操作のメソッドは元のコードから変更なし)
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (AbstractWidget widget : this.dynamicWidgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(widget);
                if (button == 0) this.isDragging = false;
                return true;
            }
        }
        this.setFocused(null);

        if (button == 0) this.isDragging = true;
        if (button == 0 || button == 1) {
            BlockPos hitPos = pickBlock(mouseX, mouseY);
            if (hitPos != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                selectBlock(hitPos);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (AbstractWidget widget : this.dynamicWidgets) {
            if (widget instanceof net.minecraft.client.gui.components.EditBox editBox && editBox.isFocused()) {
                if (editBox.keyPressed(keyCode, scanCode, modifiers) || editBox.canConsumeInput()) {
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectBlock(BlockPos pos) {
        this.selectedPos = pos;
        Level level = this.menu.getLevel();
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof ScreenBlockEntity screenBe && screenBe.masterPos != null) {
            this.selectedPos = screenBe.masterPos;
            be = level.getBlockEntity(this.selectedPos);
        }

        buildDynamicUI(this.selectedPos, be);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) this.isDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDragging && button == 0) {
            this.yaw += (float) dragX;
            this.pitch += (float) dragY;
            this.pitch = Math.clamp(this.pitch, -90f, 90f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.renderScale += (float) (scrollY * 3f);
        this.renderScale = Math.clamp(this.renderScale, 10f, 100f);
        return true;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        List<BlockPos> parts = getParts();
        if (parts.isEmpty()) return;

        Level level = this.menu.getLevel();

        float cx = 0, cy = 0, cz = 0;
        for (BlockPos p : parts) {
            cx += p.getX(); cy += p.getY(); cz += p.getZ();
        }
        cx /= parts.size(); cy /= parts.size(); cz /= parts.size();

        float drawCenterX = this.width / 2.5f;
        float drawCenterY = this.height / 2f;

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        poseStack.translate(drawCenterX, drawCenterY, 200);
        poseStack.scale(renderScale, -renderScale, renderScale);
        poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw)));

        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();

        List<BlockPos> screenGroup = new ArrayList<>();
        if (this.selectedPos != null && level.getBlockEntity(this.selectedPos) instanceof ScreenBlockEntity selectedScreen) {
            BlockPos masterScreenPos = selectedScreen.isMaster ? this.selectedPos : selectedScreen.masterPos;
            for (BlockPos p : parts) {
                if (level.getBlockEntity(p) instanceof ScreenBlockEntity sbe &&
                        (p.equals(masterScreenPos) || (sbe.masterPos != null && sbe.masterPos.equals(masterScreenPos)))) {
                    screenGroup.add(p);
                }
            }
        }

        for (BlockPos p : parts) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;

            BlockEntity partBe = level.getBlockEntity(p);
            if (partBe instanceof com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity adapter) {
                BlockState original = adapter.getOriginalState();
                if (original != null) {
                    state = original;
                }
            }

            poseStack.pushPose();
            poseStack.translate(p.getX() - cx - 0.5f, p.getY() - cy - 0.5f, p.getZ() - cz - 0.5f);

            blockRenderer.renderSingleBlock(
                    state, poseStack, guiGraphics.bufferSource(),
                    15728880, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null
            );

            guiGraphics.flush();

            boolean isHighlighted = p.equals(this.selectedPos) || screenGroup.contains(p);
            if (isHighlighted) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();

                VertexConsumer highlightBuffer = guiGraphics.bufferSource().getBuffer(RenderType.gui());

                for (Direction dir : Direction.values()) {
                    drawFaceHighlightColor(poseStack, highlightBuffer, dir, 50, 255, 50, 100);
                }
                guiGraphics.flush();
                RenderSystem.disableBlend();
            }

            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (this.selectedPos != null) {
            Level level = this.menu.getLevel();
            BlockState state = level.getBlockState(this.selectedPos);
            BlockEntity be = level.getBlockEntity(this.selectedPos);

            if (be instanceof com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity adapter) {
                BlockState original = adapter.getOriginalState();
                if (original != null) {
                    state = original;
                }
            }

            String name = state.getBlock().getName().getString();

            int panelX = (int) (this.width / 1.5f);
            int panelY = this.height / 5;
            int panelWidth = 160;

            // レジストリからUI拡張を取得して高さを決定
            IMainframeUIExtension<BlockEntity> extension = MainframeUIRegistry.get(be);
            int boxHeight = extension != null ? extension.getPanelHeight(be) : 160;

            guiGraphics.fill(panelX - 15, panelY - 15, panelX + panelWidth + 5, panelY + boxHeight + 5, 0xDD001530);
            guiGraphics.fill(panelX - 15, panelY - 15, panelX + panelWidth + 5, panelY - 14, 0xFF00E5FF);
            guiGraphics.fill(panelX - 15, panelY + boxHeight + 4, panelX + panelWidth + 5, panelY + boxHeight + 5, 0xFF00E5FF);
            guiGraphics.fill(panelX - 15, panelY - 15, panelX - 14, panelY + boxHeight + 5, 0xFF00E5FF);
            guiGraphics.fill(panelX + panelWidth + 4, panelY - 15, panelX + panelWidth + 5, panelY + boxHeight + 5, 0xFF00E5FF);

            guiGraphics.drawString(this.font, name, panelX, panelY, 0xFFFFFF);
            guiGraphics.drawString(this.font, "Pos: " + this.selectedPos.toShortString(), panelX, panelY + 15, 0x88CCFF);

            int textY = panelY + 40;

            // レジストリからUI拡張を取得してテキスト描画を委譲
            if (extension != null) {
                extension.renderDetails(guiGraphics, this.font, this.selectedPos, be, panelX, textY);
            }
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    // (pickBlock 等の描画用ヘルパーメソッドは変更なしのため省略またはそのまま維持してください)
    private BlockPos pickBlock(double mouseX, double mouseY) {
        // 元の実装をそのまま残す
        return null; // ※表示簡略化のためダミーを返していますが、実際は元のコードを維持してください。
    }

    private void drawFaceHighlightColor(PoseStack poseStack, VertexConsumer buffer, Direction face, int r, int g, int b, int a) {
        // 元の実装をそのまま残す
    }

    private void addVertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, int r, int g, int b, int a) {
        // 元の実装をそのまま残す
    }

    private Vector3f[] getFaceCorners(Direction dir) {
        // 元の実装をそのまま残す
        return new Vector3f[0];
    }

    private boolean isPointInQuad(double px, double py, Vector3f[] corners) {
        // 元の実装をそのまま残す
        return false;
    }
}