package com.nishiyu.lunex.item;

import com.nishiyu.lunex.machine.IDisguisable;
import com.nishiyu.lunex.datagen.AutoLanguageProvider;
import com.nishiyu.lunex.datagen.ITranslationGatherer;
import com.nishiyu.lunex.datagen.Translatable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class PaintballItem extends Item {

    // 汎用的なメッセージに統合
    @Translatable(en = "Disguise removed!", ja = "偽装を解除しました！")
    public static final String MSG_DISGUISE_REMOVED = "message.lunex.paintball.disguise_removed";

    @Translatable(en = "Texture disguised!", ja = "テクスチャを偽装しました！")
    public static final String MSG_DISGUISED = "message.lunex.paintball.disguised";

    @Translatable(en = "Copied texture of %s!", ja = "%s のテクスチャをコピーしました！")
    public static final String MSG_TEXTURE_COPIED = "message.lunex.paintball.texture_copied";

    @Translatable(en = "Only full blocks can be copied!", ja = "完全にブロックの形の物のみコピーできます！")
    public static final String MSG_ONLY_FULL_BLOCKS = "message.lunex.paintball.only_full_blocks";

    @Translatable(en = "Copied: %s", ja = "コピー済み: %s")
    public static final String TOOLTIP_COPIED = "tooltip.lunex.paintball.copied";

    @Translatable(en = "Uncopied", ja = "未コピー")
    public static final String TOOLTIP_UNCOPIED = "tooltip.lunex.paintball.uncopied";

    public PaintballItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).contains("CopiedState") || super.isFoil(stack);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);

        // インターフェースを利用して処理を抽象化
        if (be instanceof IDisguisable disguisable) {
            if (disguisable.getDisguiseState() != null) {
                if (!level.isClientSide) {
                    disguisable.setDisguiseState(null);
                    disguisable.applyDisguiseState(false);
                    Objects.requireNonNull(player).displayClientMessage(Component.translatable(MSG_DISGUISE_REMOVED), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            } else {
                CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                if (tag.contains("CopiedState")) {
                    if (!level.isClientSide) {
                        BlockState copiedState = NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("CopiedState"));
                        disguisable.setDisguiseState(copiedState);
                        disguisable.applyDisguiseState(true);
                        Objects.requireNonNull(player).displayClientMessage(Component.translatable(MSG_DISGUISED), true);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        } else {
            if (Block.isShapeFullBlock(state.getShape(level, pos))) {
                if (!level.isClientSide) {
                    CompoundTag stateTag = NbtUtils.writeBlockState(state);
                    CompoundTag tag = new CompoundTag();
                    tag.put("CopiedState", stateTag);
                    net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, t -> t.merge(tag));
                    Objects.requireNonNull(player).displayClientMessage(Component.translatable(MSG_TEXTURE_COPIED, state.getBlock().getName().getString()), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            } else {
                if (!level.isClientSide) {
                    Objects.requireNonNull(player).displayClientMessage(Component.translatable(MSG_ONLY_FULL_BLOCKS).withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.useOn(context);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (tag.contains("CopiedState")) {
            BlockState copied = NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("CopiedState"));
            tooltipComponents.add(Component.translatable(TOOLTIP_COPIED, copied.getBlock().getName().getString()).withStyle(ChatFormatting.GREEN));
        } else {
            tooltipComponents.add(Component.translatable(TOOLTIP_UNCOPIED).withStyle(ChatFormatting.GRAY));
        }
    }

    public static class Translations implements ITranslationGatherer {
        @Override
        public void gatherTranslations(AutoLanguageProvider provider, String locale) {
            provider.addTranslation(MSG_DISGUISE_REMOVED, "Disguise removed!", "偽装を解除しました！");
            provider.addTranslation(MSG_DISGUISED, "Texture disguised!", "テクスチャを偽装しました！");
            provider.addTranslation(MSG_TEXTURE_COPIED, "Copied texture of %s!", "%s のテクスチャをコピーしました！");
            provider.addTranslation(MSG_ONLY_FULL_BLOCKS, "Only full blocks can be copied!", "完全にブロックの形の物のみコピーできます！");
            provider.addTranslation(TOOLTIP_COPIED, "Copied: %s", "コピー済み: %s");
            provider.addTranslation(TOOLTIP_UNCOPIED, "Uncopied", "未コピー");
        }
    }
}