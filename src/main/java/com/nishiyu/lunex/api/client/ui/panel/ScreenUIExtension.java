package com.nishiyu.lunex.api.client.ui.panel;

import com.nishiyu.lunex.api.client.MainframeUIRegistry;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

public class ScreenUIExtension extends AbstractRightPanel {

    private final EditBox filterBox;

    public ScreenUIExtension(BlockPos pos, BlockEntity be) {
        super(pos, be);
        this.maxScroll = 160;

        String mode = be.getPersistentData().getString("DisplayMode");
        if (mode.isEmpty()) mode = "CAPACITY";

        String filter = be.getPersistentData().getString("ScreenFilter");
        this.filterBox = new EditBox(Minecraft.getInstance().font, 0, 0, 130, 16, Component.literal("Item/NBT Filter"));
        this.filterBox.setValue(filter);
        this.filterBox.setResponder(val -> {
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_screen_filter", val));
        });
        this.filterBox.visible = "ITEM".equals(mode);
        this.filterBox.active = "ITEM".equals(mode);
        addWidget(this.filterBox, 5, 105);

        Button modeBtn = Button.builder(Component.literal("Mode: " + mode), btn -> {
            String current = be.getPersistentData().getString("DisplayMode");
            if (current.isEmpty()) current = "CAPACITY";
            String next = "CAPACITY".equals(current) ? "ITEM" : "CAPACITY";
            be.getPersistentData().putString("DisplayMode", next);
            btn.setMessage(Component.literal("Mode: " + next));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_screen_mode", next));

            this.filterBox.visible = "ITEM".equals(next);
            this.filterBox.active = "ITEM".equals(next);
        }).bounds(0, 0, 130, 20).build();
        addWidget(modeBtn, 5, 65);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int startX, int startY, int width, int height, int mouseX, int mouseY, float partialTick) {
        if (!(be instanceof ScreenBlockEntity screen)) return;
        Level level = screen.getLevel();
        if (level == null) return;

        var font = Minecraft.getInstance().font;
        Component displayName = MainframeUIRegistry.getDisplayBlockName(be);

        graphics.drawString(font, "Target: " + displayName.getString(), startX + 10, startY + 10, 0xFFD4D4D4);
        graphics.drawString(font, "Pos: " + pos.toShortString(), startX + 10, startY + 22, 0xFF4EC9B0);

        graphics.drawString(font, "Display Settings:", startX + 5, startY + 50, 0x00E5FF);

        String mode = screen.getPersistentData().getString("DisplayMode");
        if (mode.isEmpty()) mode = "CAPACITY";

        if ("ITEM".equals(mode)) {
            graphics.drawString(font, "Filter:", startX + 5, startY + 92, 0xFFFFFF);
            String filter = screen.getPersistentData().getString("ScreenFilter");
            int count = 0;

            if (screen.mainframeMasterPos != null && level.getBlockEntity(screen.mainframeMasterPos) instanceof SimpleMachineBlockEntity master) {
                boolean isNbtFilter = filter.startsWith("{") && filter.endsWith("}");
                String searchStr = isNbtFilter ? filter.substring(1, filter.length() - 1) : filter;
                IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, master.getBlockPos(), null);

                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            if (filter.isEmpty()) count += stack.getCount();
                            else if (isNbtFilter) {
                                net.minecraft.world.item.component.CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
                                if (customData.copyTag().toString().contains(searchStr)) count += stack.getCount();
                            } else {
                                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                                if (id.contains(searchStr) || stack.getHoverName().getString().contains(searchStr)) count += stack.getCount();
                            }
                        }
                    }
                }
            }
            graphics.drawString(font, "Found: " + count, startX + 5, startY + 125, 0x00E5FF);
        } else {
            if (screen.mainframeMasterPos != null && level.getBlockEntity(screen.mainframeMasterPos) instanceof SimpleMachineBlockEntity master) {
                long maxItem = master.resourceCapacities.getOrDefault("item", 0L);
                long usedItem = master.resourceUsages.getOrDefault("item", 0L);
                graphics.drawString(font, "Items: " + usedItem + " / " + maxItem, startX + 5, startY + 95, 0x00E5FF);
            } else {
                graphics.drawString(font, "Items: 0 / 0", startX + 5, startY + 95, 0x00E5FF);
            }
        }
    }
}