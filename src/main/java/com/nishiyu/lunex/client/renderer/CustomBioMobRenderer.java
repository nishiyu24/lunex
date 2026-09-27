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

        // スティーブ（デフォルト）用のレイヤー
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

        // バニラ標準モデルの切り替え（専用レンダラーに処理を委譲）
        if ("zombie".equals(appearance)) {
            this.zombieRenderer.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        } else if ("skeleton".equals(appearance)) {
            this.skeletonRenderer.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }

        // 一致しない場合はスティーブを描画
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    // ★修正: スティーブモデル（デフォルト）側の描画時に動的スキンを読み込む
    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull CustomBioMobEntity entity) {
        return com.nishiyu.lunex.util.SkinLoader.getSkinLocation(entity.getCustomSkinName());
    }

    // =====================================================================
    // キャッシュ汚染を防ぐための独立デリゲートクラス群
    // =====================================================================
    private static class ZombieRendererDelegate extends MobRenderer<CustomBioMobEntity, HumanoidModel<CustomBioMobEntity>> {
        public ZombieRendererDelegate(EntityRendererProvider.Context context) {
            super(context, new BioZombieModel(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
            this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)), new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)), context.getModelManager()));
            this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
            this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
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

        // ★修正: スケルトンは通常のテクスチャを返すように戻す
        @Override
        public @NotNull ResourceLocation getTextureLocation(@NotNull CustomBioMobEntity entity) {
            return SKELETON_TEXTURE;
        }
    }

    // =====================================================================
    // 型制約エラーを回避するカスタムモデルクラス
    // =====================================================================
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