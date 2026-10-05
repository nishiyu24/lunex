// SimpleMachineActionProvider.java
package com.nishiyu.lunex.api.mainframe.action;

import com.nishiyu.lunex.api.mainframe.IMainframeActionProvider;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SimpleMachineActionProvider implements IMainframeActionProvider<SimpleMachineBlockEntity> {

    @Override
    public boolean handleAction(String action, String payload, SimpleMachineBlockEntity machine, Level level, ServerPlayer player) {

        // 既存アイテムへのクリック操作（出庫・追加）
        if ("storage_click".equals(action)) {
            String[] parts = payload.split(":");
            if (parts.length == 2) {
                try {
                    int index = Integer.parseInt(parts[0]);
                    int button = Integer.parseInt(parts[1]);
                    ItemStack carried = player.containerMenu.getCarried();
                    com.nishiyu.lunex.machine.MainframeItemHandler handler = machine.mainframeStorage;

                    if (index >= 0 && index < handler.getStacks().size()) {
                        ItemStack target = handler.getStacks().get(index);
                        if (carried.isEmpty()) {
                            int extractAmount = (button == 0) ? target.getMaxStackSize() : (target.getCount() + 1) / 2;
                            ItemStack extracted = handler.extractItem(index, extractAmount, false);
                            player.containerMenu.setCarried(extracted);
                        } else {
                            if (ItemStack.isSameItemSameComponents(carried, target)) {
                                int insertAmount = (button == 0) ? carried.getCount() : 1;
                                ItemStack toInsert = carried.copyWithCount(insertAmount);
                                ItemStack remainder = handler.insertItem(0, toInsert, false);
                                carried.shrink(insertAmount - remainder.getCount());
                                player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                            }
                        }
                    }
                    player.containerMenu.broadcastChanges();
                    machine.setChanged();
                } catch (Exception ignored) {}
            }
            return true;
        }

        // 空き領域へのクリック操作（新規入庫）
        if ("storage_insert".equals(action)) {
            try {
                int button = Integer.parseInt(payload);
                ItemStack carried = player.containerMenu.getCarried();
                com.nishiyu.lunex.machine.MainframeItemHandler handler = machine.mainframeStorage;

                if (!carried.isEmpty()) {
                    int insertAmount = (button == 0) ? carried.getCount() : 1;
                    ItemStack toInsert = carried.copyWithCount(insertAmount);
                    ItemStack remainder = handler.insertItem(0, toInsert, false);
                    carried.shrink(insertAmount - remainder.getCount());
                    player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                    player.containerMenu.broadcastChanges();
                    machine.setChanged();
                }
            } catch (Exception ignored) {}
            return true;
        }

        // タグ名の変更
        if ("set_mainframe_tag".equals(action)) {
            // ★修正: getCore() 経由に変更
            machine.getCore().persistentData.putString("MainframeNetworkTag", payload);
            machine.setChanged();
            level.sendBlockUpdated(machine.getBlockPos(), machine.getBlockState(), machine.getBlockState(), 3);
            com.nishiyu.lunex.mcnet.MCNetUtil.triggerNetworkUpdate(level, machine.getBlockPos());
            return true;
        }

        return false;
    }
}