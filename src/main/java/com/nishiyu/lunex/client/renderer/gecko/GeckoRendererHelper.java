package com.nishiyu.lunex.client.renderer.gecko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.entity.CustomBioMobEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class GeckoRendererHelper {

    // エンティティごとの状態キャッシュ
    private static final Map<UUID, DummyAnimatable> ANIMATABLES = new WeakHashMap<>();

    // ファイル名は決め打ち（.geo.json と .animation.json）に戻す
    private static final GeoModel<DummyAnimatable> DYNAMIC_MODEL = new GeoModel<>() {
        @Override
        public ResourceLocation getModelResource(DummyAnimatable animatable) {
            return ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "geo/" + animatable.currentModelName + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(DummyAnimatable animatable) {
            return ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "textures/entity/" + animatable.currentModelName + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(DummyAnimatable animatable) {
            return ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "animations/" + animatable.currentModelName + ".animation.json");
        }
    };

    private static DynamicGeckoRenderer RENDERER = null;

    public static void renderGeckoModel(EntityRendererProvider.Context context, CustomBioMobEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        DummyAnimatable animatable = ANIMATABLES.computeIfAbsent(entity.getUUID(), k -> new DummyAnimatable());

        String currentApp = entity.getAppearance();
        if (currentApp != null && currentApp.startsWith("gecko:")) {
            animatable.currentModelName = currentApp.substring(6);
        } else {
            animatable.currentModelName = "default";
        }

        animatable.currentEntity = entity;

        if (RENDERER == null) {
            RENDERER = new DynamicGeckoRenderer(context, DYNAMIC_MODEL, animatable);
        }

        RENDERER.setCurrentAnimatable(animatable);
        RENDERER.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static class DynamicGeckoRenderer extends GeoReplacedEntityRenderer<CustomBioMobEntity, DummyAnimatable> {
        private DummyAnimatable currentAnimatable;

        public DynamicGeckoRenderer(EntityRendererProvider.Context renderManager, GeoModel<DummyAnimatable> model, DummyAnimatable animatable) {
            super(renderManager, model, animatable);
        }

        public void setCurrentAnimatable(DummyAnimatable animatable) {
            this.currentAnimatable = animatable;
        }

        @Override
        public DummyAnimatable getAnimatable() {
            return this.currentAnimatable;
        }
    }

    public static class DummyAnimatable implements GeoAnimatable {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        public String currentModelName = "default";
        public CustomBioMobEntity currentEntity;

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {

            // 移動・待機用コントローラー
            controllers.add(new AnimationController<>(this, "movement_controller", 5, state -> {
                if (this.currentEntity == null) {
                    return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
                }

                String prefix = this.currentModelName.equals("default") ? "" : this.currentModelName + ":";

                if (!this.currentEntity.isAlive()) {
                    return state.setAndContinue(RawAnimation.begin().thenPlayAndHold(prefix + "death"));
                }

                if (this.currentEntity.isPassenger()) {
                    return state.setAndContinue(RawAnimation.begin().thenLoop(prefix + "sit"));
                }

                if (this.currentEntity.isCrouching()) {
                    if (state.isMoving()) {
                        return state.setAndContinue(RawAnimation.begin().thenLoop(prefix + "sneak"));
                    }
                    return state.setAndContinue(RawAnimation.begin().thenLoop(prefix + "sneaking"));
                }

                if (this.currentEntity.isSprinting()) {
                    return state.setAndContinue(RawAnimation.begin().thenLoop(prefix + "run"));
                }

                if (state.isMoving()) {
                    return state.setAndContinue(RawAnimation.begin().thenLoop(prefix + "walk"));
                }

                return state.setAndContinue(RawAnimation.begin().thenLoop(prefix + "idle"));
            }));

            // アクション用コントローラー
            controllers.add(new AnimationController<>(this, "action_controller", 0, state -> {
                if (this.currentEntity != null && this.currentEntity.swinging) {
                    String prefix = this.currentModelName.equals("default") ? "" : this.currentModelName + ":";
                    return state.setAndContinue(RawAnimation.begin().thenPlay(prefix + "combo_a1"));
                }
                return state.setAndContinue(RawAnimation.begin());
            }));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return this.cache;
        }

        @Override
        public double getTick(Object entity) {
            if (entity instanceof CustomBioMobEntity mob) {
                return mob.tickCount;
            }
            return 0;
        }
    }
}