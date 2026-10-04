package com.nishiyu.lunex.api.client.ui.extensions;

import com.nishiyu.lunex.api.client.IMainframeUIExtension;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.menu.MainframeOverviewScreen;
import com.nishiyu.lunex.network.packet.c2s.MainframeOverviewActionC2SPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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

import java.util.function.Consumer;

public class ScreenUIExtension implements IMainframeUIExtension {

    @Override
    public int getPanelHeight(BlockEntity be) {
        return 160;
    }

    @Override
    public void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, BlockEntity be, int panelX, int textY, Consumer<AbstractWidget> addWidget) {
        String mode = be.getPersistentData().getString("DisplayMode");
        if (mode.isEmpty()) mode = "CAPACITY";

        Button modeBtn = Button.builder(Component.literal("Mode: " + mode), btn -> {
            String current = be.getPersistentData().getString("DisplayMode");
            if (current.isEmpty()) current = "CAPACITY";
            String[] modes = {"CAPACITY", "ITEM"};
            String next = modes[0];
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equals(current)) {
                    next = modes[(i + 1) % modes.length];
                    break;
                }
            }
            be.getPersistentData().putString("DisplayMode", next);
            btn.setMessage(Component.literal("Mode: " + next));
            PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_screen_mode", next));
            screen.rebuildUI();
        }).bounds(panelX + 5, textY + 15, 130, 20).build();
        addWidget.accept(modeBtn);

        if ("ITEM".equals(mode)) {
            String filter = be.getPersistentData().getString("ScreenFilter");
            EditBox filterBox = new EditBox(screen.getMinecraft().font, panelX + 5, textY + 55, 130, 16, Component.literal("Item/NBT Filter"));
            filterBox.setValue(filter);
            filterBox.setResponder(val -> {
                PacketDistributor.sendToServer(new MainframeOverviewActionC2SPacket(pos, "set_screen_filter", val));
            });
            addWidget.accept(filterBox);
        }
    }

    @Override
    public void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, BlockEntity be, int panelX, int textY) {
        if (!(be instanceof ScreenBlockEntity screen)) return;

        Level level = screen.getLevel();
        if (level == null) return;

        guiGraphics.drawString(font, "Display Settings:", panelX + 5, textY, 0x00E5FF);
        String mode = screen.getPersistentData().getString("DisplayMode");
        if (mode.isEmpty()) mode = "CAPACITY";

        if ("ITEM".equals(mode)) {
            guiGraphics.drawString(font, "Filter:", panelX + 5, textY + 42, 0xFFFFFF);
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
            guiGraphics.drawString(font, "Found: " + count, panelX + 5, textY + 75, 0x00E5FF);
        } else {
            if (screen.mainframeMasterPos != null && level.getBlockEntity(screen.mainframeMasterPos) instanceof SimpleMachineBlockEntity master) {
                long maxItem = master.resourceCapacities.getOrDefault("item", 0L);
                long usedItem = master.resourceUsages.getOrDefault("item", 0L);
                guiGraphics.drawString(font, "Items: " + usedItem + " / " + maxItem, panelX + 5, textY + 45, 0x00E5FF);
            } else {
                guiGraphics.drawString(font, "Items: 0 / 0", panelX + 5, textY + 45, 0x00E5FF);
            }
        }
    }
}