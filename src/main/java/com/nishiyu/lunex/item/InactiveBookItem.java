package com.nishiyu.lunex.item;

import com.nishiyu.lunex.datagen.AutoLanguageProvider;
import com.nishiyu.lunex.datagen.ITranslationGatherer;
import com.nishiyu.lunex.datagen.Translatable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.List;

public class InactiveBookItem extends Item {

    @Translatable(en = "Not enough experience. Required: %s XP", ja = "経験値が足りません。必要: %s XP")
    public static final String MSG_NOT_ENOUGH_XP = "message.lunex.inactive_book.not_enough_xp";

    @Translatable(en = "Required XP for activation: %s XP", ja = "活性化に必要な経験値: %s XP")
    public static final String TOOLTIP_REQUIRED_XP = "tooltip.lunex.inactive_book.required_xp";

    @Translatable(en = "Right-click to consume XP and activate", ja = "右クリックで経験値を消費して活性化")
    public static final String TOOLTIP_ACTIVATE = "tooltip.lunex.inactive_book.activate";

    public InactiveBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }

        int requiredXp = calculateRequiredXp(enchantments);

        if (!player.isCreative() && player.totalExperience < requiredXp) {
            player.displayClientMessage(Component.translatable(MSG_NOT_ENOUGH_XP, requiredXp), true);
            return InteractionResultHolder.fail(stack);
        }

        if (!player.isCreative()) {
            consumeExperiencePoints(player, requiredXp);
        }

        ItemStack activeBook = new ItemStack(Items.ENCHANTED_BOOK);
        activeBook.set(DataComponents.STORED_ENCHANTMENTS, enchantments);

        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);

        stack.shrink(1);
        if (stack.isEmpty()) {
            return InteractionResultHolder.consume(activeBook);
        } else {
            if (!player.getInventory().add(activeBook)) {
                player.drop(activeBook, false);
            }
            return InteractionResultHolder.consume(stack);
        }
    }

    private int calculateRequiredXp(ItemEnchantments enchantments) {
        int totalXp = 0;

        for (var entry : enchantments.entrySet()) {
            int lvl = entry.getIntValue();
            totalXp += lvl * 5;

            var keyOpt = entry.getKey().unwrapKey();
            if (keyOpt.isPresent()) {
                ResourceLocation id = keyOpt.get().location();
                String path = id.getPath();

                if (path.equals("mending") || path.equals("infinity") || path.equals("fortune") || path.equals("silk_touch")) {
                    totalXp += 50;
                }
            }
        }
        return totalXp;
    }

    private void consumeExperiencePoints(Player player, int amount) {
        int remaining = player.totalExperience - amount;
        player.totalExperience = 0;
        player.experienceLevel = 0;
        player.experienceProgress = 0.0F;
        if (remaining > 0) {
            player.giveExperiencePoints(remaining);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!enchantments.isEmpty()) {
            int requiredXp = calculateRequiredXp(enchantments);
            tooltipComponents.add(Component.translatable(TOOLTIP_REQUIRED_XP, requiredXp).withStyle(ChatFormatting.YELLOW));
            tooltipComponents.add(Component.translatable(TOOLTIP_ACTIVATE).withStyle(ChatFormatting.GRAY));
        }
    }

    public static class Translations implements ITranslationGatherer {
        @Override
        public void gatherTranslations(AutoLanguageProvider provider, String locale) {
            provider.addTranslation(MSG_NOT_ENOUGH_XP, "Not enough experience. Required: %s XP", "経験値が足りません。必要: %s XP");
            provider.addTranslation(TOOLTIP_REQUIRED_XP, "Required XP for activation: %s XP", "活性化に必要な経験値: %s XP");
            provider.addTranslation(TOOLTIP_ACTIVATE, "Right-click to consume XP and activate", "右クリックで経験値を消費して活性化");
        }
    }
}