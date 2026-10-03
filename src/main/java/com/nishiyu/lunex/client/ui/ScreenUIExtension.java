package com.nishiyu.lunex.client.ui;

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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.function.Consumer;

public class ScreenUIExtension implements IMainframeUIExtension<ScreenBlockEntity> {

    @Override
    public int getPanelHeight(ScreenBlockEntity be) {
        return 160;
    }

    @Override
    public void buildWidgets(MainframeOverviewScreen screen, BlockPos pos, ScreenBlockEntity be, int panelX, int textY, Consumer<AbstractWidget> addWidget) {
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

            // ★ モード変更時にUIを再構築するため画面のクリック処理等で再描画をトリガーする必要があります
            // （元の MainframeOverviewScreen.buildDynamicUI 相当の呼び出し）
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
    public void renderDetails(GuiGraphics guiGraphics, Font font, BlockPos pos, ScreenBlockEntity be, int panelX, int textY) {
        Level level = be.getLevel();
        if (level == null) return;

        guiGraphics.drawString(font, "Display Settings:", panelX + 5, textY, 0x00E5FF);
        String mode = be.getPersistentData().getString("DisplayMode");
        if (mode.isEmpty()) mode = "CAPACITY";

        if ("ITEM".equals(mode)) {
            guiGraphics.drawString(font, "Filter:", panelX + 5, textY + 42, 0xFFFFFF);
            String filter = be.getPersistentData().getString("ScreenFilter");
            int count = 0;

            if (be.mainframeMasterPos != null && level.getBlockEntity(be.mainframeMasterPos) instanceof SimpleMachineBlockEntity master) {
                boolean isNbtFilter = filter.startsWith("{") && filter.endsWith("}");
                String searchStr = isNbtFilter ? filter.substring(1, filter.length() - 1) : filter;
                IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, master.getBlockPos(), null);

                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (!stack.isEmpty()) {
                            if (filter.isEmpty()) {
                                count += stack.getCount();
                            } else if (isNbtFilter) {
                                net.minecraft.world.item.component.CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
                                if (customData.copyTag().toString().contains(searchStr)) {
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
            guiGraphics.drawString(font, "Found: " + count, panelX + 5, textY + 75, 0x00E5FF);

        } else {
            if (be.mainframeMasterPos != null && level.getBlockEntity(be.mainframeMasterPos) instanceof SimpleMachineBlockEntity master) {
                double maxMB = master.mainframeTotalCapacityBytes / 1048576.0;
                double usedMB = master.mainframeUsedItemBytes / 1048576.0;
                guiGraphics.drawString(font, String.format(Locale.US, "Capacity: %.2f / %.2f MB", usedMB, maxMB), panelX + 5, textY + 45, 0x00E5FF);
            } else {
                guiGraphics.drawString(font, "Capacity: 0.00 / 0.00 MB", panelX + 5, textY + 45, 0x00E5FF);
            }
        }
    }

    @Override
    public boolean handleAction(String action, String value, ScreenBlockEntity screen, Level level) {
        if ("set_screen_mode".equals(action)) {
            screen.getPersistentData().putString("DisplayMode", value);
            screen.setChanged();
            level.sendBlockUpdated(screen.getBlockPos(), screen.getBlockState(), screen.getBlockState(), 3);
            return true;
        } else if ("set_screen_filter".equals(action)) {
            screen.getPersistentData().putString("ScreenFilter", value);
            screen.setChanged();
            level.sendBlockUpdated(screen.getBlockPos(), screen.getBlockState(), screen.getBlockState(), 3);
            return true;
        }
        return false;
    }
}