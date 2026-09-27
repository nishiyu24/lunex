package com.nishiyu.lunex.item;

import com.nishiyu.lunex.blockentity.RouterBlockEntity;
import com.nishiyu.lunex.datagen.AutoLanguageProvider;
import com.nishiyu.lunex.datagen.ITranslationGatherer;
import com.nishiyu.lunex.datagen.Translatable;
import com.nishiyu.lunex.menu.PortableScreenMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class PortableScreenItem extends Item {

    @Translatable(en = "[PortableScreen] Registration complete - IP: %s", ja = "[PortableScreen] 登録完了 - IP: %s")
    public static final String MSG_REGISTERED = "message.lunex.portablescreen.registered";

    @Translatable(en = "[PortableScreen] Registration failed due to IP address depletion.", ja = "[PortableScreen] IPアドレスの完全枯渇により登録に失敗しました。")
    public static final String MSG_FAILED_IP = "message.lunex.portablescreen.failed_ip";

    @Translatable(en = "[PortableScreen] Target router's DHCP server is disabled.", ja = "[PortableScreen] 対象ルーターのDHCPサーバーが無効です。")
    public static final String MSG_DHCP_DISABLED = "message.lunex.portablescreen.dhcp_disabled";

    @Translatable(en = "[PortableScreen] Please right-click a router to register an IP address.", ja = "[PortableScreen] ルーターを右クリックしてIPアドレスを登録してください。")
    public static final String MSG_PLEASE_REGISTER = "message.lunex.portablescreen.please_register";

    @Translatable(en = "IP: %s", ja = "IP: %s")
    public static final String TOOLTIP_IP = "tooltip.lunex.portablescreen.ip";

    @Translatable(en = "Unregistered (Right-click router to register)", ja = "未登録 (ルーターを右クリックで登録)")
    public static final String TOOLTIP_UNREGISTERED = "tooltip.lunex.portablescreen.unregistered";

    @Translatable(en = "Portable Screen", ja = "Portable Screen")
    public static final String MENU_TITLE = "menu.lunex.portablescreen.title";

    public PortableScreenItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        if (be instanceof RouterBlockEntity router) {
            ItemStack stack = context.getItemInHand();
            Player player = context.getPlayer();

            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = customData.copyTag();

            String deviceId = tag.contains("DeviceId") ? tag.getString("DeviceId") : UUID.randomUUID().toString();
            tag.putString("DeviceId", deviceId);

            if (router.machineId == null) {
                router.machineId = UUID.randomUUID();
                router.setChanged();
            }
            tag.putUUID("NetworkId", router.machineId);

            // ★ 追加: ルーターの位置、ディメンション、アップグレードによる最大距離を記録
            tag.putLong("RouterPos", router.getBlockPos().asLong());
            tag.putString("RouterDim", level.dimension().location().toString());
            int upgradeLevel = router.getDistanceUpgradeLevel();
            double maxDist = upgradeLevel == 1 ? 256.0 : (upgradeLevel == 2 ? 1024.0 : (upgradeLevel >= 3 ? Double.MAX_VALUE : 64.0));
            tag.putDouble("RouterRange", maxDist);

            CompoundTag rData = router.persistentData;
            if (rData != null && rData.getBoolean("DHCPServerEnabled")) {
                CompoundTag leases = rData.contains("DHCPLeases") ? rData.getCompound("DHCPLeases") : new CompoundTag();
                String assignedIp = "";

                if (leases.contains(deviceId)) {
                    assignedIp = leases.getString(deviceId);
                } else {
                    String baseIp = rData.getString("DHCPBaseIP");
                    int start = rData.getInt("DHCPStartOctet");
                    int size = rData.getInt("DHCPPoolSize");

                    for (int i = 0; i < size; i++) {
                        String testIp = baseIp + "." + (start + i);
                        boolean used = false;
                        for (String key : leases.getAllKeys()) {
                            if (leases.getString(key).equals(testIp)) {
                                used = true;
                                break;
                            }
                        }
                        if (!used) {
                            assignedIp = testIp;
                            break;
                        }
                    }

                    if (assignedIp.isEmpty()) {
                        for (String key : leases.getAllKeys()) {
                            if (key.length() == 36 && key.split("-").length == 5) {
                                assignedIp = leases.getString(key);
                                leases.remove(key);
                                break;
                            }
                        }
                    }

                    if (!assignedIp.isEmpty()) {
                        leases.putString(deviceId, assignedIp);
                        rData.put("DHCPLeases", leases);
                        router.setChanged();
                        router.sync();
                    }
                }

                if (!assignedIp.isEmpty()) {
                    tag.putString("IPAddress", assignedIp);
                    if (player != null) {
                        player.displayClientMessage(Component.translatable(MSG_REGISTERED, assignedIp).withStyle(ChatFormatting.GREEN), true);
                    }
                } else {
                    if (player != null) {
                        player.displayClientMessage(Component.translatable(MSG_FAILED_IP).withStyle(ChatFormatting.RED), true);
                    }
                }
            } else {
                if (player != null) {
                    player.displayClientMessage(Component.translatable(MSG_DHCP_DISABLED).withStyle(ChatFormatting.RED), true);
                }
            }

            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = customData.copyTag();

            if (!tag.contains("IPAddress") || !tag.contains("NetworkId")) {
                serverPlayer.displayClientMessage(Component.translatable(MSG_PLEASE_REGISTER).withStyle(ChatFormatting.YELLOW), true);
                return InteractionResultHolder.pass(stack);
            }

            String ip = tag.getString("IPAddress");
            String networkId = tag.getUUID("NetworkId").toString();
            String screenKey = networkId + ":" + ip;

            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new PortableScreenMenu(id, inventory, screenKey),
                    Component.translatable(MENU_TITLE)
            ), buf -> buf.writeUtf(screenKey));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, net.minecraft.world.item.@NotNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (tag.contains("IPAddress")) {
            tooltipComponents.add(Component.translatable(TOOLTIP_IP, tag.getString("IPAddress")).withStyle(ChatFormatting.GREEN));
        } else {
            tooltipComponents.add(Component.translatable(TOOLTIP_UNREGISTERED).withStyle(ChatFormatting.GRAY));
        }
    }

    public static class Translations implements ITranslationGatherer {
        @Override
        public void gatherTranslations(AutoLanguageProvider provider, String locale) {
            provider.addTranslation(MSG_REGISTERED, "[PortableScreen] Registration complete - IP: %s", "[PortableScreen] 登録完了 - IP: %s");
            provider.addTranslation(MSG_FAILED_IP, "[PortableScreen] Registration failed due to IP address depletion.", "[PortableScreen] IPアドレスの完全枯渇により登録に失敗しました。");
            provider.addTranslation(MSG_DHCP_DISABLED, "[PortableScreen] Target router's DHCP server is disabled.", "[PortableScreen] 対象ルーターのDHCPサーバーが無効です。");
            provider.addTranslation(MSG_PLEASE_REGISTER, "[PortableScreen] Please right-click a router to register an IP address.", "[PortableScreen] ルーターを右クリックしてIPアドレスを登録してください。");
            provider.addTranslation(TOOLTIP_IP, "IP: %s", "IP: %s");
            provider.addTranslation(TOOLTIP_UNREGISTERED, "Unregistered (Right-click router to register)", "未登録 (ルーターを右クリックで登録)");
            provider.addTranslation(MENU_TITLE, "Portable Screen", "Portable Screen");
        }
    }
}