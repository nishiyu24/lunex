package com.nishiyu.lunex.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nishiyu.lunex.client.renderer.gecko.GeckoRendererHelper;
import com.nishiyu.lunex.entity.CustomBioMobEntity;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

public class CustomBioMobRenderer extends MobRenderer<CustomBioMobEntity, HumanoidModel<CustomBioMobEntity>> {

    private static final ResourceLocation DEFAULT_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    private static final ResourceLocation ZOMBIE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");
    private static final ResourceLocation SKELETON_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png");

    private final EntityRendererProvider.Context context;
    private final ZombieRendererDelegate zombieRenderer;
    private final SkeletonRendererDelegate skeletonRenderer;

    public CustomBioMobRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.context = context;

        this.zombieRenderer = new ZombieRendererDelegate(context);
        this.skeletonRenderer = new SkeletonRendererDelegate(context);

        this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
    }

    @Override
    public void render(@NotNull CustomBioMobEntity entity, float entityYaw, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        String app = entity.getAppearance();
        String appearance = (app != null) ? app.toLowerCase().trim() : "default";

        if (appearance.startsWith("gecko:") && ModList.get().isLoaded("geckolib")) {
            GeckoRendererHelper.renderGeckoModel(this.context, entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }

        if ("zombie".equals(appearance)) {
            this.zombieRenderer.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        } else if ("skeleton".equals(appearance)) {
            this.skeletonRenderer.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }

        // ★追加: レンダリングの直前にモデルの腕のポーズ（採掘・食事など）を更新する
        updateModelPoses(entity, this.getModel());
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    // ★追加: エンティティのアイテム使用状態からモデルのポーズを決定する処理
    public static void updateModelPoses(CustomBioMobEntity entity, HumanoidModel<?> model) {
        model.rightArmPose = getArmPose(entity, InteractionHand.MAIN_HAND);
        model.leftArmPose = getArmPose(entity, InteractionHand.OFF_HAND);
        model.crouching = entity.isCrouching();
        model.riding = entity.isPassenger();
    }

    // ★追加: 左右の手に持っているアイテムから対応するモーション(食べる、弓、クロスボウ等)を算出する
    private static HumanoidModel.ArmPose getArmPose(CustomBioMobEntity entity, InteractionHand hand) {
        ItemStack itemstack = entity.getItemInHand(hand);
        if (itemstack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }

        // アイテム使用中の場合のアニメーション処理
        if (entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0) {
            net.minecraft.world.item.UseAnim useanim = itemstack.getUseAnimation();
            if (useanim == net.minecraft.world.item.UseAnim.BLOCK) {
                return HumanoidModel.ArmPose.BLOCK;
            } else if (useanim == net.minecraft.world.item.UseAnim.BOW) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            } else if (useanim == net.minecraft.world.item.UseAnim.SPEAR) {
                return HumanoidModel.ArmPose.THROW_SPEAR;
            } else if (useanim == net.minecraft.world.item.UseAnim.CROSSBOW && hand == entity.getUsedItemHand()) {
                return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            } else if (useanim == net.minecraft.world.item.UseAnim.SPYGLASS) {
                return HumanoidModel.ArmPose.SPYGLASS;
            } else if (useanim == net.minecraft.world.item.UseAnim.TOOT_HORN) {
                return HumanoidModel.ArmPose.TOOT_HORN;
            } else if (useanim == net.minecraft.world.item.UseAnim.BRUSH) {
                return HumanoidModel.ArmPose.BRUSH;
            }
        } else if (!entity.swinging && itemstack.getItem() instanceof net.minecraft.world.item.CrossbowItem && net.minecraft.world.item.CrossbowItem.isCharged(itemstack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }

        // 通常のアイテム持ち・食事アニメーション等の標準ポーズ
        return HumanoidModel.ArmPose.ITEM;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull CustomBioMobEntity entity) {
        return com.nishiyu.lunex.util.SkinLoader.getSkinLocation(entity.getCustomSkinName());
    }

    private static class ZombieRendererDelegate extends MobRenderer<CustomBioMobEntity, HumanoidModel<CustomBioMobEntity>> {
        public ZombieRendererDelegate(EntityRendererProvider.Context context) {
            super(context, new BioZombieModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
            this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)), new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)), context.getModelManager()));
            this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
            this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        }

        @Override
        public void render(@NotNull CustomBioMobEntity entity, float entityYaw, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
            CustomBioMobRenderer.updateModelPoses(entity, this.getModel());
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }

        @Override
        public @NotNull ResourceLocation getTextureLocation(@NotNull CustomBioMobEntity entity) {
            return ZOMBIE_TEXTURE;
        }
    }

    private static class SkeletonRendererDelegate extends MobRenderer<CustomBioMobEntity, HumanoidModel<CustomBioMobEntity>> {
        public SkeletonRendererDelegate(EntityRendererProvider.Context context) {
            super(context, new BioSkeletonModel(context.bakeLayer(ModelLayers.SKELETON)), 0.5f);
            this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON_INNER_ARMOR)), new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON_OUTER_ARMOR)), context.getModelManager()));
            this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
            this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        }

        @Override
        public void render(@NotNull CustomBioMobEntity entity, float entityYaw, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
            CustomBioMobRenderer.updateModelPoses(entity, this.getModel());
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }

        @Override
        public @NotNull ResourceLocation getTextureLocation(@NotNull CustomBioMobEntity entity) {
            return SKELETON_TEXTURE;
        }
    }

    private static class BioZombieModel extends HumanoidModel<CustomBioMobEntity> {
        public BioZombieModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(@NotNull CustomBioMobEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            AnimationUtils.animateZombieArms(this.leftArm, this.rightArm, true, this.attackTime, ageInTicks);
        }
    }

    private static class BioSkeletonModel extends HumanoidModel<CustomBioMobEntity> {
        public BioSkeletonModel(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(@NotNull CustomBioMobEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }
}