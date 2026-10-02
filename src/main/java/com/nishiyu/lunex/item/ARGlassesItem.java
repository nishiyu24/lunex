package com.nishiyu.lunex.item;

import com.nishiyu.lunex.api.mainframe.MainframeConstants;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.datagen.AutoLanguageProvider;
import com.nishiyu.lunex.datagen.ITranslationGatherer;
import com.nishiyu.lunex.datagen.Translatable;
import com.nishiyu.lunex.menu.PortableScreenMenu;
import com.nishiyu.lunex.mcnet.IMCNetDevice;
import com.nishiyu.lunex.mcnet.MCNetUtil;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class ARGlassesItem extends Item implements net.minecraft.world.item.Equipable, IMCNetDevice {

    @Translatable(en = "[ARGlasses] Registration complete - IP: %s", ja = "[ARGlasses] 登録完了 - IP: %s")
    public static final String MSG_REGISTERED = "message.lunex.arglasses.registered";

    @Translatable(en = "[ARGlasses] Registration failed due to IP address depletion.", ja = "[ARGlasses] IPアドレスの完全枯渇により登録に失敗しました。")
    public static final String MSG_FAILED_IP = "message.lunex.arglasses.failed_ip";

    @Translatable(en = "[ARGlasses] Target router's DHCP server is disabled.", ja = "[ARGlasses] 対象ルーターのDHCPサーバーが無効です。")
    public static final String MSG_DHCP_DISABLED = "message.lunex.arglasses.dhcp_disabled";

    @Translatable(en = "[ARGlasses] Please right-click a router to register an IP address.", ja = "[ARGlasses] ルーターを右クリックしてIPアドレスを登録してください。")
    public static final String MSG_PLEASE_REGISTER = "message.lunex.arglasses.please_register";

    @Translatable(en = "IP: %s", ja = "IP: %s")
    public static final String TOOLTIP_IP = "tooltip.lunex.arglasses.ip";

    @Translatable(en = "Unregistered (Right-click router to register)", ja = "未登録 (ルーターを右クリックで登録)")
    public static final String TOOLTIP_UNREGISTERED = "tooltip.lunex.arglasses.unregistered";

    @Translatable(en = "AR Glasses", ja = "AR Glasses")
    public static final String MENU_TITLE = "menu.lunex.arglasses.title";

    public ARGlassesItem(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.entity.EquipmentSlot getEquipmentSlot() {
        return net.minecraft.world.entity.EquipmentSlot.HEAD;
    }

    @Override
    public String getDeviceType() {
        return "ar_glasses";
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        // ★修正: RouterBlockEntity ではなく、ルーター機能を持つ SimpleMachineBlockEntity か判定
        if (be instanceof SimpleMachineBlockEntity sm && sm.isMainframeMaster && sm.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
            MCNetUtil.registerPortableDevice(
                    level, sm, context.getItemInHand(), context.getPlayer(),
                    this.getDeviceType(), MSG_REGISTERED, MSG_FAILED_IP, MSG_DHCP_DISABLED
            );
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
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

        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
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
            provider.addTranslation(MSG_REGISTERED, "[ARGlasses] Registration complete - IP: %s", "[ARGlasses] 登録完了 - IP: %s");
            provider.addTranslation(MSG_FAILED_IP, "[ARGlasses] Registration failed due to IP address depletion.", "[ARGlasses] IPアドレスの完全枯渇により登録に失敗しました。");
            provider.addTranslation(MSG_DHCP_DISABLED, "[ARGlasses] Target router's DHCP server is disabled.", "[ARGlasses] 対象ルーターのDHCPサーバーが無効です。");
            provider.addTranslation(MSG_PLEASE_REGISTER, "[ARGlasses] Please right-click a router to register an IP address.", "[ARGlasses] ルーターを右クリックしてIPアドレスを登録してください。");
            provider.addTranslation(TOOLTIP_IP, "IP: %s", "IP: %s");
            provider.addTranslation(TOOLTIP_UNREGISTERED, "Unregistered (Right-click router to register)", "未登録 (ルーターを右クリックで登録)");
            provider.addTranslation(MENU_TITLE, "AR Glasses", "AR Glasses");
        }
    }
}