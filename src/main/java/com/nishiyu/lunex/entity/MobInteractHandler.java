package com.nishiyu.lunex.entity;

import com.nishiyu.lunex.item.TabletItem;
import com.nishiyu.lunex.menu.BioEntity.BioEntitySettingsMenu;
import com.nishiyu.lunex.util.WorkspaceManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUtils;

public class MobInteractHandler {

    public static InteractionResult handleInteract(CustomBioMobEntity mob, Player player, InteractionHand hand) {
        ItemStack stackInHand = player.getItemInHand(hand);

        if (stackInHand.getItem() instanceof TabletItem) {
            if (!player.isShiftKeyDown()) {
                if (!mob.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                    if (mob.workspaceId == null || mob.workspaceId.isEmpty()) {
                        mob.workspaceId = "biomob_" + mob.getUUID().toString().substring(0, 8);
                        WorkspaceManager.initializeWorkspace(mob.level().getServer(), mob.workspaceId);
                    }
                    serverPlayer.openMenu(new SimpleMenuProvider(
                            (id, inventory, p) -> new BioEntitySettingsMenu(id, inventory, mob.getId()),
                            Component.literal("Bio Mob Settings")
                    ), buf -> buf.writeInt(mob.getId()));
                }
                return InteractionResult.sidedSuccess(mob.level().isClientSide);
            }
        }

        if (mob.isMechanical()) {
            if (mob.vm != null && mob.vm.isRunning && !player.isShiftKeyDown() && stackInHand.isEmpty()) {
                if (hand == InteractionHand.MAIN_HAND) {
                    if (!mob.level().isClientSide) {
                        mob.vm.triggerEvent("on_click");
                    }
                    return InteractionResult.sidedSuccess(mob.level().isClientSide);
                }
            }
        }

        if (mob.traitManager.isMount) {
            if (stackInHand.is(Items.SADDLE) && !mob.isSaddled) {
                if (!mob.level().isClientSide) {
                    mob.isSaddled = true;
                    mob.playSound(SoundEvents.HORSE_SADDLE, 1.0F, 1.0F);
                    if (!player.isCreative()) stackInHand.shrink(1);
                }
                return InteractionResult.sidedSuccess(mob.level().isClientSide);
            } else if (mob.isSaddled && stackInHand.isEmpty() && !player.isShiftKeyDown()) {
                if (!mob.level().isClientSide) {
                    player.startRiding(mob);
                }
                return InteractionResult.sidedSuccess(mob.level().isClientSide);
            }
        }

        if (mob.traitManager.isMerchant) {
            if (stackInHand.isEmpty() && !player.isShiftKeyDown() && mob.getTradingPlayer() == null) {
                if (hand == InteractionHand.MAIN_HAND) {
                    if (!mob.level().isClientSide) {
                        mob.setTradingPlayer(player);
                        mob.openTradingScreen(player, mob.getDisplayName(), 1);
                    }
                    return InteractionResult.sidedSuccess(mob.level().isClientSide);
                }
            }
        }

        if (mob.traitManager.isEquipable) {
            if (player.isShiftKeyDown() && stackInHand.isEmpty()) {
                if (hand == InteractionHand.MAIN_HAND) {
                    boolean removedAny = false;
                    if (!mob.level().isClientSide) {
                        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                            if (slot.isArmor() && !mob.getItemBySlot(slot).isEmpty()) {
                                mob.spawnAtLocation(mob.getItemBySlot(slot));
                                mob.setItemSlot(slot, ItemStack.EMPTY);
                                removedAny = true;
                            }
                        }
                    } else {
                        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                            if (slot.isArmor() && !mob.getItemBySlot(slot).isEmpty()) {
                                removedAny = true;
                                break;
                            }
                        }
                    }
                    if (removedAny) return InteractionResult.sidedSuccess(mob.level().isClientSide);
                }
            }
            if (!stackInHand.isEmpty() && stackInHand.getItem() instanceof net.minecraft.world.item.Equipable equipable) {
                net.minecraft.world.entity.EquipmentSlot slot = equipable.getEquipmentSlot();
                if (slot.isArmor()) {
                    if (!mob.level().isClientSide) {
                        ItemStack currentArmor = mob.getItemBySlot(slot);
                        mob.setItemSlot(slot, stackInHand.copyWithCount(1));
                        mob.setDropChance(slot, 1.0F);
                        if (!player.isCreative()) stackInHand.shrink(1);
                        if (!currentArmor.isEmpty()) mob.spawnAtLocation(currentArmor);
                    }
                    return InteractionResult.sidedSuccess(mob.level().isClientSide);
                }
            }
        }

        if (mob.traitManager.isMilkable && stackInHand.is(Items.BUCKET)) {
            if (!mob.level().isClientSide) {
                player.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
                ItemStack milk = ItemUtils.createFilledResult(stackInHand, player, Items.MILK_BUCKET.getDefaultInstance());
                player.setItemInHand(hand, milk);
            }
            return InteractionResult.sidedSuccess(mob.level().isClientSide);
        }

        if (mob.traitManager.isShearable && stackInHand.is(Items.SHEARS) && mob.shearCooldown == 0) {
            if (!mob.level().isClientSide) {
                player.playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
                mob.spawnAtLocation(Items.WHITE_WOOL, 1);
                stackInHand.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                mob.shearCooldown = 6000;
            }
            return InteractionResult.sidedSuccess(mob.level().isClientSide);
        }

        if (mob.traitManager.isPackMule && mob.inventorySize > 0 && player.isShiftKeyDown()) {
            if (hand == InteractionHand.MAIN_HAND) {
                if (!mob.level().isClientSide) {
                    player.openMenu(new SimpleMenuProvider(
                            (id, playerInv, p) -> switch (mob.inventorySize) {
                                case 18 -> new ChestMenu(MenuType.GENERIC_9x2, id, playerInv, mob.inventory, 2);
                                case 27 -> new ChestMenu(MenuType.GENERIC_9x3, id, playerInv, mob.inventory, 3);
                                case 36 -> new ChestMenu(MenuType.GENERIC_9x4, id, playerInv, mob.inventory, 4);
                                case 45 -> new ChestMenu(MenuType.GENERIC_9x5, id, playerInv, mob.inventory, 5);
                                case 54 -> new ChestMenu(MenuType.GENERIC_9x6, id, playerInv, mob.inventory, 6);
                                default -> new ChestMenu(MenuType.GENERIC_9x1, id, playerInv, mob.inventory, 1);
                            },
                            Component.literal("Bio Mob Inventory")
                    ));
                }
                return InteractionResult.sidedSuccess(mob.level().isClientSide);
            }
        }

        return InteractionResult.PASS; // 元の処理へパス
    }
}