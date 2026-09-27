package com.nishiyu.lunex.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.block.ProbeBlock;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import com.nishiyu.lunex.network.packet.c2s.ProbeUpdateC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class ProbeScreen extends AbstractContainerScreen<ProbeMenu> {

    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "textures/gui/container/blank_gui.png");

    private final float renderScale = 30F;
    private final float yOffset = -5f;

    private float yaw = 45f;
    private float pitch = 30f;
    private boolean isModelDragging = false;
    private Button visibleButton;
    private Boolean lastDetected = null;

    private EditBox nameInput;
    private String customName = "";

    public ProbeScreen(ProbeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        if (this.menu.getLevel().getBlockEntity(this.menu.getPos()) instanceof ProbeBlockEntity probe) {
            this.customName = probe.getNetworkTag();
        }

        int bottomY = relY + this.imageHeight - 28;

        this.visibleButton = Button.builder(Component.empty(), button -> {
            PacketDistributor.sendToServer(new ProbeUpdateC2SPacket(this.menu.getPos(), ProbeUpdateC2SPacket.Action.TOGGLE_VISIBLE, null, null));
        }).bounds(relX + 8, bottomY, 20, 20).build();

        this.nameInput = new EditBox(this.font, relX + 32, bottomY + 2, 136, 16, Component.literal("Probe Name"));
        this.nameInput.setMaxLength(30);
        this.nameInput.setValue(this.customName);

        this.addRenderableWidget(this.visibleButton);
        this.addRenderableWidget(this.nameInput);

        this.nameInput.visible = true;
    }

    private void sendSettingsToServer() {
        if (this.nameInput != null) {
            this.customName = this.nameInput.getValue();
        }
        PacketDistributor.sendToServer(new ProbeUpdateC2SPacket(this.menu.getPos(), ProbeUpdateC2SPacket.Action.SET_NAME, null, this.customName));
    }

    @Override
    public void onClose() {
        sendSettingsToServer();
        super.onClose();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(GUI_TEXTURE, relX, relY, 0, 0, this.imageWidth, this.imageHeight);

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(relX + this.imageWidth / 2f, relY + this.imageHeight / 2f + yOffset, 100);
        poseStack.scale(renderScale, -renderScale, renderScale);
        poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw)));
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        Level level = this.menu.getLevel();
        BlockPos centerPos = this.menu.getPos();
        BlockState centerState = level.getBlockState(centerPos);

        if (centerState.hasProperty(ProbeBlock.IS_DISGUISED)) {
            centerState = centerState.setValue(ProbeBlock.IS_DISGUISED, false);
        }

        ProbeBlockEntity probe = null;
        if (level.getBlockEntity(centerPos) instanceof ProbeBlockEntity p) probe = p;

        if (centerState.getBlock() instanceof ProbeBlock && probe != null) {
            blockRenderer.renderSingleBlock(
                    centerState, poseStack, guiGraphics.bufferSource(),
                    15728880, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null
            );
            guiGraphics.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            VertexConsumer buffer = guiGraphics.bufferSource().getBuffer(RenderType.gui());

            for (Direction dir : Direction.values()) {
                BooleanProperty prop = ProbeBlock.getPropertyByDirection(dir);
                if (centerState.getValue(prop)) {
                    drawFaceHighlightColor(poseStack, buffer, dir, 50, 255, 50, 100);
                }
            }
            guiGraphics.flush();
        }

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.4F);
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = centerPos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);

            if (!neighborState.isAir()) {
                poseStack.pushPose();
                poseStack.translate(dir.getStepX(), dir.getStepY(), dir.getStepZ());
                poseStack.translate(0.5f, 0.5f, 0.5f);
                poseStack.scale(0.85f, 0.85f, 0.85f);
                poseStack.translate(-0.5f, -0.5f, -0.5f);

                blockRenderer.renderSingleBlock(
                        neighborState, poseStack, guiGraphics.bufferSource(),
                        15728880, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                        net.neoforged.neoforge.client.model.data.ModelData.EMPTY, RenderType.translucent()
                );
                poseStack.popPose();
            }
        }
        guiGraphics.flush();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private void drawFaceHighlightColor(PoseStack poseStack, VertexConsumer buffer, Direction face, int r, int g, int b, int a) {
        Matrix4f pose = poseStack.last().pose();
        float offset = 0.01f;

        switch (face) {
            case NORTH -> {
                addVertex(buffer, pose, 1, 1, -offset, r, g, b, a);
                addVertex(buffer, pose, 1, 0, -offset, r, g, b, a);
                addVertex(buffer, pose, 0, 0, -offset, r, g, b, a);
                addVertex(buffer, pose, 0, 1, -offset, r, g, b, a);
            }
            case SOUTH -> {
                addVertex(buffer, pose, 0, 1, 1 + offset, r, g, b, a);
                addVertex(buffer, pose, 0, 0, 1 + offset, r, g, b, a);
                addVertex(buffer, pose, 1, 0, 1 + offset, r, g, b, a);
                addVertex(buffer, pose, 1, 1, 1 + offset, r, g, b, a);
            }
            case WEST -> {
                addVertex(buffer, pose, -offset, 1, 0, r, g, b, a);
                addVertex(buffer, pose, -offset, 0, 0, r, g, b, a);
                addVertex(buffer, pose, -offset, 0, 1, r, g, b, a);
                addVertex(buffer, pose, -offset, 1, 1, r, g, b, a);
            }
            case EAST -> {
                addVertex(buffer, pose, 1 + offset, 1, 1, r, g, b, a);
                addVertex(buffer, pose, 1 + offset, 0, 1, r, g, b, a);
                addVertex(buffer, pose, 1 + offset, 0, 0, r, g, b, a);
                addVertex(buffer, pose, 1 + offset, 1, 0, r, g, b, a);
            }
            case UP -> {
                addVertex(buffer, pose, 0, 1 + offset, 0, r, g, b, a);
                addVertex(buffer, pose, 0, 1 + offset, 1, r, g, b, a);
                addVertex(buffer, pose, 1, 1 + offset, 1, r, g, b, a);
                addVertex(buffer, pose, 1, 1 + offset, 0, r, g, b, a);
            }
            case DOWN -> {
                addVertex(buffer, pose, 1, -offset, 0, r, g, b, a);
                addVertex(buffer, pose, 1, -offset, 1, r, g, b, a);
                addVertex(buffer, pose, 0, -offset, 1, r, g, b, a);
                addVertex(buffer, pose, 0, -offset, 0, r, g, b, a);
            }
        }
    }

    private void addVertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, int r, int g, int b, int a) {
        buffer.addVertex(pose, x, y, z).setColor(r, g, b, a);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) this.isModelDragging = true;

        if (this.nameInput != null) {
            if (this.nameInput.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(this.nameInput);
                return true;
            } else {
                this.setFocused(null);
            }
        }

        if (button == 1) {
            int relX = (this.width - this.imageWidth) / 2;
            int relY = (this.height - this.imageHeight) / 2;

            Matrix4f matrix = new Matrix4f();
            matrix.translate(relX + this.imageWidth / 2f, relY + this.imageHeight / 2f + yOffset, 100);
            matrix.scale(renderScale, -renderScale, renderScale);
            matrix.rotateX((float) Math.toRadians(pitch));
            matrix.rotateY((float) Math.toRadians(yaw));
            matrix.translate(-0.5f, -0.5f, -0.5f);

            Direction hitFace = null;
            float maxZ = -Float.MAX_VALUE;

            for (Direction dir : Direction.values()) {
                Vector3f[] corners = getFaceCorners(dir);
                Vector3f[] projected = new Vector3f[4];
                float avgZ = 0;

                for (int i = 0; i < 4; i++) {
                    Vector4f p = new Vector4f(corners[i].x, corners[i].y, corners[i].z, 1.0f);
                    matrix.transform(p);
                    projected[i] = new Vector3f(p.x, p.y, p.z);
                    avgZ += p.z;
                }
                avgZ /= 4f;

                if (isPointInQuad(mouseX, mouseY, projected)) {
                    if (avgZ > maxZ) {
                        maxZ = avgZ;
                        hitFace = dir;
                    }
                }
            }

            if (hitFace != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                PacketDistributor.sendToServer(new ProbeUpdateC2SPacket(this.menu.getPos(), ProbeUpdateC2SPacket.Action.TOGGLE_FACE, hitFace, null));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameInput != null && this.nameInput.isFocused()) {
            return this.nameInput.keyPressed(keyCode, scanCode, modifiers) || this.nameInput.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

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
        boolean hasPos = false;
        boolean hasNeg = false;
        for (int i = 0; i < 4; i++) {
            Vector3f p1 = corners[i];
            Vector3f p2 = corners[(i + 1) % 4];
            double cross = (px - p1.x) * (p2.y - p1.y) - (py - p1.y) * (p2.x - p1.x);
            if (cross > 0.001) hasPos = true;
            if (cross < -0.001) hasNeg = true;
        }
        return !(hasPos && hasNeg);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) this.isModelDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isModelDragging && button == 0 && !this.visibleButton.isMouseOver(mouseX, mouseY)) {
            this.yaw += (float) dragX;
            this.pitch += (float) dragY;
            this.pitch = Math.clamp(this.pitch, -90f, 90f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (this.menu.getLevel().getBlockEntity(this.menu.getPos()) instanceof ProbeBlockEntity probe) {
            if (this.lastDetected == null || this.lastDetected != probe.isDetected) {
                this.lastDetected = probe.isDetected;
                if (probe.isDetected) {
                    this.visibleButton.setTooltip(Tooltip.create(Component.literal("Detected").withStyle(net.minecraft.ChatFormatting.GREEN)));
                } else {
                    this.visibleButton.setTooltip(Tooltip.create(Component.literal("NotDetected").withStyle(net.minecraft.ChatFormatting.RED)));
                }
            }

            int bx = this.visibleButton.getX();
            int by = this.visibleButton.getY();

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 200);

            if (probe.isDetected) {
                guiGraphics.blitSprite(ResourceLocation.withDefaultNamespace("container/beacon/confirm"), bx + 2, by + 2, 16, 16);
            } else {
                TextureAtlasSprite blindnessSprite = Minecraft.getInstance().getMobEffectTextures().get(MobEffects.BLINDNESS);
                guiGraphics.blit(bx + 2, by + 2, 0, 16, 16, blindnessSprite);
            }
            guiGraphics.pose().popPose();
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleWidth = this.font.width(this.title);
        guiGraphics.drawString(this.font, this.title, (this.imageWidth - titleWidth) / 2, 14, 4210752, false);
    }
}