package com.nishiyu.lunex.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
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

        if (be instanceof com.nishiyu.lunex.blockentity.DatabaseBlockEntity db) {
            int currentPriority = db.getPersistentData().getInt("Priority");
            if (currentPriority < 1 || currentPriority > 10) currentPriority = 1;
            int finalPriority = currentPriority;

            Button priorityBtn = Button.builder(Component.literal("Priority: " + finalPriority), btn -> {
                int current = db.getPersistentData().getInt("Priority");
                if (current < 1 || current > 10) current = 1;
                int next = current >= 10 ? 1 : current + 1;
                db.getPersistentData().putInt("Priority", next);
                btn.setMessage(Component.literal("Priority: " + next));
                PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_priority", String.valueOf(next)));
            }).bounds(panelX + 5, textY + 70, 130, 20).build();
            this.addRenderableWidget(priorityBtn);
            dynamicWidgets.add(priorityBtn);

        } else if (be instanceof com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity machine) {
            String mTag = machine.persistentData.getString("MainframeNetworkTag");
            EditBox tagBox = new EditBox(this.font, panelX + 5, textY + 155, 130, 16, Component.literal("Mainframe Tag"));
            tagBox.setValue(mTag);
            tagBox.setMaxLength(30);
            tagBox.setResponder(val -> {
                machine.persistentData.putString("MainframeNetworkTag", val);
                PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_mainframe_tag", val));
            });
            this.addRenderableWidget(tagBox);
            dynamicWidgets.add(tagBox);

        } else if (be instanceof com.nishiyu.lunex.blockentity.ScreenBlockEntity screen) {
            String mode = screen.getPersistentData().getString("DisplayMode");
            if (mode.isEmpty()) mode = "CAPACITY";

            Button modeBtn = Button.builder(Component.literal("Mode: " + mode), btn -> {
                String current = screen.getPersistentData().getString("DisplayMode");
                if (current.isEmpty()) current = "CAPACITY";

                String[] modes = {"CAPACITY", "ITEM"};
                String next = modes[0];
                for (int i = 0; i < modes.length; i++) {
                    if (modes[i].equals(current)) {
                        next = modes[(i + 1) % modes.length];
                        break;
                    }
                }

                screen.getPersistentData().putString("DisplayMode", next);
                btn.setMessage(Component.literal("Mode: " + next));
                PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_screen_mode", next));
                buildDynamicUI(pos, be);
            }).bounds(panelX + 5, textY + 15, 130, 20).build();
            this.addRenderableWidget(modeBtn);
            dynamicWidgets.add(modeBtn);

            if ("ITEM".equals(mode)) {
                String filter = screen.getPersistentData().getString("ScreenFilter");
                EditBox filterBox = new EditBox(this.font, panelX + 5, textY + 55, 130, 16, Component.literal("Item/NBT Filter"));
                filterBox.setValue(filter);
                filterBox.setResponder(val -> {
                    PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_screen_filter", val));
                });
                this.addRenderableWidget(filterBox);
                dynamicWidgets.add(filterBox);
            }

        } else if (be instanceof com.nishiyu.lunex.blockentity.ProbeBlockEntity probe) {
            Button activeBtn = Button.builder(Component.literal("Active: " + probe.isDetected), btn -> {
                probe.isDetected = !probe.isDetected;
                btn.setMessage(Component.literal("Active: " + probe.isDetected));
                PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_active", ""));
            }).bounds(panelX + 5, textY + 15, 130, 20).build();
            this.addRenderableWidget(activeBtn);
            dynamicWidgets.add(activeBtn);

            String mode = probe.getPersistentData().getString("IOMode");
            if (mode.isEmpty()) mode = "IN";
            Button ioBtn = Button.builder(Component.literal("Mode: " + mode), btn -> {
                String current = probe.getPersistentData().getString("IOMode");
                String next = "OUT".equals(current) ? "IN" : "OUT";
                if(current.isEmpty()) next = "OUT";
                probe.getPersistentData().putString("IOMode", next);
                btn.setMessage(Component.literal("Mode: " + next));
                PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "toggle_mode", ""));
            }).bounds(panelX + 5, textY + 40, 130, 20).build();
            this.addRenderableWidget(ioBtn);
            dynamicWidgets.add(ioBtn);

            String filter = probe.getPersistentData().getString("NBTFilter");
            EditBox nbtBox = new EditBox(this.font, panelX + 5, textY + 75, 130, 16, Component.literal("NBT Filter"));
            nbtBox.setValue(filter);
            nbtBox.setMaxLength(256);
            nbtBox.setResponder(val -> {
                PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_nbt_filter", val));
            });
            this.addRenderableWidget(nbtBox);
            dynamicWidgets.add(nbtBox);
        }
    }

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
            if (widget instanceof EditBox editBox && editBox.isFocused()) {
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

            // ★修正: アダプターの場合、描画対象を元のブロック状態（originalState）に差し替える
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

            // ★修正: UIのテキスト表示時にも、アダプターの場合は元のブロック情報に差し替える
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

            int boxHeight = 160;
            if (be instanceof com.nishiyu.lunex.blockentity.ProbeBlockEntity) boxHeight = 145;
            if (be instanceof com.nishiyu.lunex.blockentity.DatabaseBlockEntity) boxHeight = 140;
            if (be instanceof com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity) boxHeight = 220;

            guiGraphics.fill(panelX - 15, panelY - 15, panelX + panelWidth + 5, panelY + boxHeight + 5, 0xDD001530);
            guiGraphics.fill(panelX - 15, panelY - 15, panelX + panelWidth + 5, panelY - 14, 0xFF00E5FF);
            guiGraphics.fill(panelX - 15, panelY + boxHeight + 4, panelX + panelWidth + 5, panelY + boxHeight + 5, 0xFF00E5FF);
            guiGraphics.fill(panelX - 15, panelY - 15, panelX - 14, panelY + boxHeight + 5, 0xFF00E5FF);
            guiGraphics.fill(panelX + panelWidth + 4, panelY - 15, panelX + panelWidth + 5, panelY + boxHeight + 5, 0xFF00E5FF);

            guiGraphics.drawString(this.font, name, panelX, panelY, 0xFFFFFF);
            guiGraphics.drawString(this.font, "Pos: " + this.selectedPos.toShortString(), panelX, panelY + 15, 0x88CCFF);

            int textY = panelY + 40;

            if (be instanceof com.nishiyu.lunex.blockentity.DatabaseBlockEntity db) {
                if (db.getMasterPos() != null && level.getBlockEntity(db.getMasterPos()) instanceof SimpleMachineBlockEntity master) {
                    double maxMB = master.mainframeTotalCapacityBytes / 1048576.0;
                    double usedMB = master.mainframeUsedItemBytes / 1048576.0;

                    guiGraphics.drawString(this.font, String.format(Locale.US, "Usage: %.2f MB", usedMB), panelX + 5, textY, 0x00E5FF);
                    guiGraphics.drawString(this.font, String.format(Locale.US, "/ %.2f MB", maxMB), panelX + 5, textY + 15, 0x00E5FF);
                }
            } else if (be instanceof com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity machine) {
                double maxMB = machine.mainframeTotalCapacityBytes / 1048576.0;
                double usedMB = machine.mainframeUsedItemBytes / 1048576.0;

                guiGraphics.drawString(this.font, "Machines: " + machine.mainframeMachines, panelX + 5, textY, 0x00E5FF);
                guiGraphics.drawString(this.font, String.format(Locale.US, "Capacity: %.2f / %.2f MB", usedMB, maxMB), panelX + 5, textY + 15, 0x00E5FF);

                int screenCount = 0;
                int probeCount = 0;
                for (BlockPos p : machine.mainframeParts) {
                    Block b = level.getBlockState(p).getBlock();
                    if (b instanceof com.nishiyu.lunex.block.ScreenBlock) screenCount++;
                    if (b instanceof com.nishiyu.lunex.block.ProbeBlock) probeCount++;
                }
                guiGraphics.drawString(this.font, "Total Parts: " + machine.mainframeParts.size(), panelX + 5, textY + 70, 0xFFFFFF);
                guiGraphics.drawString(this.font, "- Screens: " + screenCount, panelX + 5, textY + 85, 0x88CCFF);
                guiGraphics.drawString(this.font, "- Probes: " + probeCount, panelX + 5, textY + 100, 0x88CCFF);

                guiGraphics.drawString(this.font, "Mainframe Tag:", panelX + 5, textY + 125, 0xFFFFFF);
            } else if (be instanceof com.nishiyu.lunex.blockentity.ScreenBlockEntity screen) {
                guiGraphics.drawString(this.font, "Display Settings:", panelX + 5, textY, 0x00E5FF);
                String mode = screen.getPersistentData().getString("DisplayMode");
                if (mode.isEmpty()) mode = "CAPACITY";

                if ("ITEM".equals(mode)) {
                    guiGraphics.drawString(this.font, "Filter:", panelX + 5, textY + 42, 0xFFFFFF);

                    String filter = screen.getPersistentData().getString("ScreenFilter");
                    int count = 0;
                    if (screen.mainframeMasterPos != null && level.getBlockEntity(screen.mainframeMasterPos) instanceof SimpleMachineBlockEntity master) {
                        boolean isNbtFilter = filter.startsWith("{") && filter.endsWith("}");
                        String searchStr = isNbtFilter ? filter.substring(1, filter.length() - 1) : filter;

                        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, master.getBlockPos(), null);
                        if(handler != null) {
                            for (int i = 0; i < handler.getSlots(); i++) {
                                ItemStack stack = handler.getStackInSlot(i);
                                if (!stack.isEmpty()) {
                                    if (filter.isEmpty()) {
                                        count += stack.getCount();
                                    } else if (isNbtFilter) {
                                        net.minecraft.world.item.component.CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
                                        String nbtStr = customData.copyTag().toString();
                                        if (nbtStr.contains(searchStr)) {
                                            count += stack.getCount();
                                        }
                                    } else {
                                        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                                        if (id.contains(searchStr) || stack.getHoverName().getString().contains(searchStr)) {
                                            count += stack.getCount();
                                        }
                                    }
                                }
                            }
                        }
                    }
                    guiGraphics.drawString(this.font, "Found: " + count, panelX + 5, textY + 75, 0x00E5FF);
                } else {
                    if (screen.mainframeMasterPos != null && level.getBlockEntity(screen.mainframeMasterPos) instanceof SimpleMachineBlockEntity master) {
                        double maxMB = master.mainframeTotalCapacityBytes / 1048576.0;
                        double usedMB = master.mainframeUsedItemBytes / 1048576.0;
                        guiGraphics.drawString(this.font, String.format(Locale.US, "Capacity: %.2f / %.2f MB", usedMB, maxMB), panelX + 5, textY + 45, 0x00E5FF);
                    } else {
                        guiGraphics.drawString(this.font, "Capacity: 0.00 / 0.00 MB", panelX + 5, textY + 45, 0x00E5FF);
                    }
                }
            } else if (be instanceof com.nishiyu.lunex.blockentity.ProbeBlockEntity) {
                guiGraphics.drawString(this.font, "Probe Configuration:", panelX + 5, textY, 0x00E5FF);
                guiGraphics.drawString(this.font, "Filter:", panelX + 5, textY + 65, 0xFFFFFF);
            }
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private BlockPos pickBlock(double mouseX, double mouseY) {
        List<BlockPos> parts = getParts();
        if (parts.isEmpty()) return null;

        float cx = 0, cy = 0, cz = 0;
        for (BlockPos p : parts) {
            cx += p.getX(); cy += p.getY(); cz += p.getZ();
        }
        cx /= parts.size(); cy /= parts.size(); cz /= parts.size();

        float drawCenterX = this.width / 2.5f;
        float drawCenterY = this.height / 2f;

        BlockPos hitPos = null;
        float maxZ = -Float.MAX_VALUE;

        for (BlockPos p : parts) {
            Matrix4f matrix = new Matrix4f();
            matrix.translate(drawCenterX, drawCenterY, 200);
            matrix.scale(renderScale, -renderScale, renderScale);
            matrix.rotateX((float) Math.toRadians(pitch));
            matrix.rotateY((float) Math.toRadians(yaw));
            matrix.translate(p.getX() - cx - 0.5f, p.getY() - cy - 0.5f, p.getZ() - cz - 0.5f);

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

                if (isPointInQuad(mouseX, mouseY, projected)) {
                    if (avgZ > maxZ) {
                        maxZ = avgZ;
                        hitPos = p;
                    }
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

    private void addVertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, int r, int g, int b, int a) {
        buffer.addVertex(pose, x, y, z).setColor(r, g, b, a);
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
}