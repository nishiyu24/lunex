package com.nishiyu.lunex.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScreenRenderCore {

    public static final Map<String, Integer> inputScrollOffsets = new ConcurrentHashMap<>();
    public static String activeInputId = "";

    private static void applyTransforms(PoseStack poseStack, ScreenBlockEntity.UIElement el, String hoveredId, String sessionId, Map<String, ScreenBlockEntity.UIElement> elementMap, float[] cumulativeOpacity) {
        if (el == null) return;

        if (el.events() != null && el.events().containsKey("mc-parent")) {
            String parentId = el.events().get("mc-parent");
            applyTransforms(poseStack, elementMap.get(parentId), hoveredId, sessionId, elementMap, cumulativeOpacity);
        }

        boolean isHovered = com.nishiyu.lunex.client.ClientScreenManager.getBaseNodeId(el.id()).equals(hoveredId);
        ScreenAnimator.AnimatedValues anim = ScreenAnimator.getValues(sessionId, el, isHovered);

        float originX = 0.5f, originY = 0.5f;
        if (el.events() != null && el.events().containsKey("mc-origin")) {
            String[] parts = el.events().get("mc-origin").split(",");
            if (parts.length >= 2) {
                try {
                    originX = Float.parseFloat(parts[0]);
                    originY = Float.parseFloat(parts[1]);
                } catch (Exception ignored) {
                }
            }
        }
        float cx = el.x() + el.width() * originX;
        float cy = el.y() + el.height() * originY;

        poseStack.translate(cx + anim.tx(), cy + anim.ty(), 0);
        if (anim.rot() != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(anim.rot()));
        if (anim.sx() != 1.0f || anim.sy() != 1.0f) poseStack.scale(anim.sx(), anim.sy(), 1.0f);
        poseStack.translate(-cx, -cy, 0);

        cumulativeOpacity[0] *= anim.opacity();
    }

    public static void renderElements(
            List<ScreenBlockEntity.UIElement> uiElements, int screenWidth, int screenHeight,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay,
            boolean renderItems, boolean renderText, boolean renderRounded,
            float zStep, float subZOffset, String hoveredId, int lodColor, boolean isLodMode,
            Level level, boolean playAudio, boolean is2DAudio, String sessionId,
            BlockPos audioPos,
            ICustomRenderer customRenderer,
            int renderMode // 互換性のために残すか、将来の拡張用 (現在は0で全描画)
    ) {
        float maxAllowedX = screenWidth * (float) ScreenBlockEntity.RESOLUTION;
        float maxAllowedY = screenHeight * (float) ScreenBlockEntity.RESOLUTION;
        Font font = Minecraft.getInstance().font;
        float zOffset = 0.0f;

        if (isLodMode) {
            if ((lodColor >> 24 & 0xFF) > 0) {
                VertexConsumer builder = buffer.getBuffer(RenderType.gui());
                float a = ((lodColor >> 24) & 0xFF) / 255.0f;
                float r = ((lodColor >> 16) & 0xFF) / 255.0f;
                float g = ((lodColor >> 8) & 0xFF) / 255.0f;
                float b = (lodColor & 0xFF) / 255.0f;
                addQuadRaw(builder, poseStack.last().pose(), 0, 0, maxAllowedX, maxAllowedY, r, g, b, a, 0.0f);
            }
            return;
        }

        if (sessionId != null && !sessionId.isEmpty()) {
            ScreenAnimator.cleanup(sessionId, uiElements);
        }

        Map<String, ScreenBlockEntity.UIElement> elementMap = new HashMap<>();
        for (ScreenBlockEntity.UIElement el : uiElements) {
            elementMap.put(el.id(), el);
        }

        for (ScreenBlockEntity.UIElement el : uiElements) {
            if ("node".equals(el.type())) continue;

            zOffset += zStep;

            boolean isHovered = com.nishiyu.lunex.client.ClientScreenManager.getBaseNodeId(el.id()).equals(hoveredId);
            String animSessionId = (sessionId != null && !sessionId.isEmpty()) ? sessionId : "unknown";
            ScreenAnimator.AnimatedValues anim = ScreenAnimator.getValues(animSessionId, el, isHovered);

            float clipMinX = 0, clipMinY = 0, clipMaxX = maxAllowedX, clipMaxY = maxAllowedY;
            float cRTL = 0, cRTR = 0, cRBR = 0, cRBL = 0;
            if (anim.clipData != null) {
                clipMinX = anim.clipData[0];
                clipMinY = anim.clipData[1];
                clipMaxX = anim.clipData[2];
                clipMaxY = anim.clipData[3];
                cRTL = anim.clipData[4];
                cRTR = anim.clipData[5];
                cRBR = anim.clipData[6];
                cRBL = anim.clipData[7];
            }

            // ★ 軽量化カリング: クリップ領域から完全に外れている要素の計算と描画をスキップする
            float elWorldMaxX = el.x() + el.width();
            float elWorldMaxY = el.y() + el.height();
            if (elWorldMaxX < clipMinX || el.x() > clipMaxX || elWorldMaxY < clipMinY || el.y() > clipMaxY) {
                continue;
            }

            float rTL = 0, rTR = 0, rBR = 0, rBL = 0;
            if (renderRounded && anim.radiusData != null) {
                float limit = Math.min(el.width(), el.height()) / 2.0f;
                rTL = Math.min(anim.radiusData[0], limit);
                rTR = Math.min(anim.radiusData[1], limit);
                rBR = Math.min(anim.radiusData[2], limit);
                rBL = Math.min(anim.radiusData[3], limit);
            }

            poseStack.pushPose();
            try {
                float[] cumulativeOpacity = {1.0f};

                if (el.events() != null && el.events().containsKey("mc-parent")) {
                    String parentId = el.events().get("mc-parent");
                    applyTransforms(poseStack, elementMap.get(parentId), hoveredId, animSessionId, elementMap, cumulativeOpacity);
                }

                float originX = 0.5f, originY = 0.5f;
                if (el.events() != null && el.events().containsKey("mc-origin")) {
                    String[] parts = el.events().get("mc-origin").split(",");
                    if (parts.length >= 2) {
                        try {
                            originX = Float.parseFloat(parts[0]);
                            originY = Float.parseFloat(parts[1]);
                        } catch (Exception ignored) {
                        }
                    }
                }
                float cx = el.x() + el.width() * originX;
                float cy = el.y() + el.height() * originY;

                poseStack.translate(cx + anim.tx(), cy + anim.ty(), 0);
                if (anim.rot() != 0) poseStack.mulPose(Axis.ZP.rotationDegrees(anim.rot()));
                if (anim.sx() != 1.0f || anim.sy() != 1.0f) poseStack.scale(anim.sx(), anim.sy(), 1.0f);
                poseStack.translate(-cx, -cy, 0);

                float finalOpacity = cumulativeOpacity[0] * anim.opacity();

                // ★ 軽量化カリング: 完全に透明に近い要素は描画しない
                if (finalOpacity <= 0.005f) continue;

                if (customRenderer != null && customRenderer.render(el, poseStack, buffer, anim, zOffset, subZOffset, renderRounded)) {
                    continue;
                }

                if ("circle_border".equals(el.type()) && anim.circleColors != null) {
                    float outerRadius = Math.min(el.width(), el.height()) / 2.0f;
                    int cTop = anim.circleColors[0], cRight = anim.circleColors[1], cBottom = anim.circleColors[2], cLeft = anim.circleColors[3];
                    VertexConsumer builder = buffer.getBuffer(RenderType.gui());
                    Matrix4f matrix = poseStack.last().pose();

                    float centerRealX = el.x() + el.width() / 2.0f;
                    float centerRealY = el.y() + el.height() / 2.0f;
                    if (cTop != 0)
                        drawRingSegment(builder, matrix, centerRealX, centerRealY, outerRadius, anim.circleThickness, (float) (-Math.PI * 0.75), (float) (-Math.PI * 0.25), cTop, finalOpacity, zOffset + subZOffset);
                    if (cRight != 0)
                        drawRingSegment(builder, matrix, centerRealX, centerRealY, outerRadius, anim.circleThickness, (float) (-Math.PI * 0.25), (float) (Math.PI * 0.25), cRight, finalOpacity, zOffset + subZOffset);
                    if (cBottom != 0)
                        drawRingSegment(builder, matrix, centerRealX, centerRealY, outerRadius, anim.circleThickness, (float) (Math.PI * 0.25), (float) (Math.PI * 0.75), cBottom, finalOpacity, zOffset + subZOffset);
                    if (cLeft != 0)
                        drawRingSegment(builder, matrix, centerRealX, centerRealY, outerRadius, anim.circleThickness, (float) (Math.PI * 0.75), (float) (Math.PI * 1.25), cLeft, finalOpacity, zOffset + subZOffset);
                    continue;
                }

                if (!"range".equals(el.type())) {
                    float bgA = ((anim.bgColor() >> 24) & 0xFF) / 255.0f * finalOpacity;
                    if (bgA > 0) {
                        VertexConsumer builder = buffer.getBuffer(RenderType.gui());
                        float r = ((anim.bgColor() >> 16) & 0xFF) / 255.0f, g = ((anim.bgColor() >> 8) & 0xFF) / 255.0f, b = (anim.bgColor() & 0xFF) / 255.0f;
                        if (rTL > 0 || rTR > 0 || rBR > 0 || rBL > 0)
                            drawRoundedRect(builder, poseStack.last().pose(), el.x(), el.y(), el.x() + el.width(), el.y() + el.height(), rTL, rTR, rBR, rBL, r, g, b, bgA, zOffset, clipMinX, clipMinY, clipMaxX, clipMaxY, cRTL, cRTR, cRBR, cRBL);
                        else
                            drawClippedQuad(builder, poseStack.last().pose(), el.x(), el.y(), el.x() + el.width(), el.y() + el.height(), r, g, b, bgA, zOffset, clipMinX, clipMinY, clipMaxX, clipMaxY, cRTL, cRTR, cRBR, cRBL);
                    }
                }

                if ("video".equals(el.type()) && el.text() != null) {
                    ClientMediaManager.VideoTexture videoTex = ClientMediaManager.getVideoTexture(el.text());
                    if (videoTex != null) {
                        if (playAudio && is2DAudio) videoTex.keepAlive2D(sessionId);
                        else if (playAudio && !is2DAudio && audioPos != null) videoTex.keepAlive(audioPos, sessionId);
                        else videoTex.keepAlive(null, sessionId);
                    }
                    if (renderItems && videoTex != null) {
                        videoTex.updateFrame();
                        VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(videoTex.textureLocation));
                        drawRoundedTexturedRect(builder, poseStack.last().pose(), el.x(), el.y(), el.x() + el.width(), el.y() + el.height(), 0.0f, 0.0f, 1.0f, 1.0f, rTL, rTR, rBR, rBL, 1f, 1f, 1f, finalOpacity, zOffset + subZOffset, packedLight, packedOverlay, clipMinX, clipMinY, clipMaxX, clipMaxY, cRTL, cRTR, cRBR, cRBL);
                    }

                } else if (el.type().startsWith("img")) {
                    if (renderItems && el.text() != null && !el.text().isEmpty()) {
                        String src = el.text();

                        if (src.startsWith("https://")) {
                            ClientMediaManager.ImageInfo info = ClientMediaManager.getImage(src);
                            if (info != null) {
                                ResourceLocation texLoc = info.textureId();
                                int imgW = info.width();
                                int imgH = info.height();

                                VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(texLoc));
                                float u0 = 0.0f, v0 = 0.0f, u1 = 1.0f, v1 = 1.0f;
                                float drawMinX = el.x(), drawMinY = el.y(), drawMaxX = el.x() + el.width(), drawMaxY = el.y() + el.height();
                                String fit = el.type().contains(":") ? el.type().split(":")[1] : "fill";
                                float boxW = drawMaxX - drawMinX, boxH = drawMaxY - drawMinY;

                                if (imgW > 0 && imgH > 0 && boxW > 0 && boxH > 0) {
                                    float boxAspect = boxW / boxH, imgAspect = (float) imgW / imgH;
                                    if ("contain".equalsIgnoreCase(fit)) {
                                        if (imgAspect > boxAspect) {
                                            float diff = (boxH - (boxW / imgAspect)) / 2.0f;
                                            drawMinY += diff;
                                            drawMaxY -= diff;
                                        } else {
                                            float diff = (boxW - (boxH * imgAspect)) / 2.0f;
                                            drawMinX += diff;
                                            drawMaxX -= diff;
                                        }
                                    } else if ("cover".equalsIgnoreCase(fit)) {
                                        if (imgAspect > boxAspect) {
                                            float crop = (imgW - (imgH * boxAspect)) / (2.0f * imgW);
                                            u0 = crop;
                                            u1 = 1.0f - crop;
                                        } else {
                                            float crop = (imgH - (imgW / boxAspect)) / (2.0f * imgH);
                                            v0 = crop;
                                            v1 = 1.0f - crop;
                                        }
                                    }
                                }
                                drawRoundedTexturedRect(builder, poseStack.last().pose(), drawMinX, drawMinY, drawMaxX, drawMaxY, u0, v0, u1, v1, rTL, rTR, rBR, rBL, 1f, 1f, 1f, finalOpacity, zOffset + subZOffset, packedLight, packedOverlay, clipMinX, clipMinY, clipMaxX, clipMaxY, cRTL, cRTR, cRBR, cRBL);
                            }

                        } else if (src.startsWith("item:")) {
                            String itemId = src.substring(5);
                            try {
                                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                                if (item != Items.AIR) {
                                    poseStack.pushPose();
                                    try {
                                        poseStack.translate(el.x() + (el.width() / 2.0f), el.y() + (el.height() / 2.0f), zOffset + subZOffset);
                                        poseStack.scale(el.width(), -el.height(), 1.0f);
                                        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                                        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(item), net.minecraft.world.item.ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, buffer, level, 0);
                                    } finally {
                                        poseStack.popPose();
                                    }
                                }
                            } catch (Exception ignored) {
                            }

                        } else if (src.startsWith("effect:")) {
                            String effectId = src.substring(7);
                            try {
                                java.util.Optional<net.minecraft.core.Holder.Reference<net.minecraft.world.effect.MobEffect>> effectOpt = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(effectId));
                                if (effectOpt.isPresent()) {
                                    net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(effectOpt.get());
                                    ResourceLocation atlasLoc = sprite.atlasLocation();
                                    VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(atlasLoc));
                                    drawRoundedTexturedRect(builder, poseStack.last().pose(), el.x(), el.y(), el.x() + el.width(), el.y() + el.height(), sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), rTL, rTR, rBR, rBL, 1f, 1f, 1f, finalOpacity, zOffset + subZOffset, packedLight, packedOverlay, clipMinX, clipMinY, clipMaxX, clipMaxY, cRTL, cRTR, cRBR, cRBL);
                                }
                            } catch (Exception ignored) {
                            }
                        } else {
                            try {
                                ResourceLocation texLoc = ResourceLocation.parse(src);
                                VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(texLoc));
                                drawRoundedTexturedRect(builder, poseStack.last().pose(), el.x(), el.y(), el.x() + el.width(), el.y() + el.height(), 0.0f, 0.0f, 1.0f, 1.0f, rTL, rTR, rBR, rBL, 1f, 1f, 1f, finalOpacity, zOffset + subZOffset, packedLight, packedOverlay, clipMinX, clipMinY, clipMaxX, clipMaxY, cRTL, cRTR, cRBR, cRBL);
                            } catch (Exception ignored) {
                            }
                        }
                    }

                } else if ((el.text() != null && !el.text().isEmpty()) || "input".equals(el.type()) || "range".equals(el.type())) {
                    String textToDraw = el.text() == null ? "" : el.text();
                    float textX = el.x(), textY = el.y(), textScale = el.height() / 12.0f;

                    if (textY > clipMaxY - (el.height() * 0.3f) || textY + el.height() < clipMinY + (el.height() * 0.3f)) {
                        continue;
                    }

                    if ("input".equals(el.type())) {
                        textScale = (el.height() * 0.5f) / 12.0f;
                        textX += 4.0f;
                        textY += (el.height() - (12.0f * textScale)) / 2.0f;
                    } else if ("range".equals(el.type())) {
                        float ratio = 0;
                        if (anim.rangeMax > anim.rangeMin)
                            ratio = Math.max(0, Math.min(1, (anim.rangeVal - anim.rangeMin) / (anim.rangeMax - anim.rangeMin)));

                        VertexConsumer builder = buffer.getBuffer(RenderType.gui());
                        Matrix4f mat = poseStack.last().pose();

                        float trackH = Math.max(4.0f, el.height() * 0.3f);
                        float trackY = el.y() + (el.height() - trackH) / 2.0f;
                        float trackRad = trackH / 2.0f;

                        float thumbDiameter = Math.max(10.0f, el.height() * 0.8f);
                        float thumbRadius = thumbDiameter / 2.0f;
                        float thumbCenterY = el.y() + el.height() / 2.0f;
                        float thumbMinX = el.x() + thumbRadius;
                        float thumbMaxX = el.x() + el.width() - thumbRadius;
                        float thumbCenterX = thumbMinX + ratio * (thumbMaxX - thumbMinX);

                        int progressColor = anim.color() != 0 ? anim.color() : 0xFF007BFF;
                        float prA = ((progressColor >> 24) & 0xFF) / 255.0f * finalOpacity;
                        float prR = ((progressColor >> 16) & 0xFF) / 255.0f, prG = ((progressColor >> 8) & 0xFF) / 255.0f, prB = (progressColor & 0xFF) / 255.0f;

                        if (thumbCenterX > el.x())
                            drawRoundedRect(builder, mat, el.x(), trackY, thumbCenterX, trackY + trackH, trackRad, 0, 0, trackRad, prR, prG, prB, prA, zOffset + subZOffset, clipMinX, clipMinY, clipMaxX, clipMaxY, cRTL, cRTR, cRBR, cRBL);

                        int restColor = anim.bgColor() != 0 ? anim.bgColor() : 0xFFFFFFFF;
                        float reA = ((restColor >> 24) & 0xFF) / 255.0f * finalOpacity;
                        float reR = ((restColor >> 16) & 0xFF) / 255.0f, reG = ((restColor >> 8) & 0xFF) / 255.0f, reB = (restColor & 0xFF) / 255.0f;

                        if (thumbCenterX < el.x() + el.width())
                            drawRoundedRect(builder, mat, thumbCenterX, trackY, el.x() + el.width(), trackY + trackH, 0, trackRad, trackRad, 0, reR, reG, reB, reA, zOffset + subZOffset, clipMinX, clipMinY, clipMaxX, clipMaxY, cRTL, cRTR, cRBR, cRBL);

                        drawRoundedRect(builder, mat, thumbCenterX - thumbRadius, thumbCenterY - thumbRadius, thumbCenterX + thumbRadius, thumbCenterY + thumbRadius, thumbRadius, thumbRadius, thumbRadius, thumbRadius, prR, prG, prB, prA, zOffset + subZOffset + 0.002f, clipMinX, clipMinY, clipMaxX, clipMaxY, cRTL, cRTR, cRBR, cRBL);
                        continue;
                    } else if ("toggle".equals(el.type())) {
                        if ("true".equals(textToDraw)) {
                            float a = ((anim.color() >> 24) & 0xFF) / 255.0f * finalOpacity;
                            if (a > 0.0f) {
                                float pad = el.width() * 0.25f;
                                float r = ((anim.color() >> 16) & 0xFF) / 255.0f, g = ((anim.color() >> 8) & 0xFF) / 255.0f, b = (anim.color() & 0xFF) / 255.0f;
                                drawClippedQuad(buffer.getBuffer(RenderType.gui()), poseStack.last().pose(), textX + pad, textY + pad, textX + el.width() - pad, textY + el.height() - pad, r, g, b, a, zOffset + subZOffset, clipMinX, clipMinY, clipMaxX, clipMaxY, cRTL, cRTR, cRBR, cRBL);
                            }
                        }
                        continue;
                    }

                    if (!"input".equals(el.type()) && textToDraw.isEmpty()) {
                        continue;
                    }

                    if (renderText && textX < maxAllowedX && textY < maxAllowedY) {
                        int originalA = (anim.color() >> 24) & 0xFF;
                        int newA = (int) (originalA * finalOpacity);
                        if (newA > 0) {
                            poseStack.pushPose();
                            try {
                                if ("input".equals(el.type())) {
                                    poseStack.translate(textX, textY, zOffset + subZOffset);
                                    poseStack.scale(textScale, textScale, 1.0f);
                                    int safeCursorPos = -1;
                                    if (el.id().equals(activeInputId)) {
                                        net.minecraft.client.gui.screens.Screen screen = Minecraft.getInstance().screen;
                                        if (screen != null) {
                                            if (screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox eb)
                                                safeCursorPos = eb.getCursorPosition();
                                            else {
                                                for (var child : screen.children()) {
                                                    if (child instanceof net.minecraft.client.gui.components.EditBox eb && eb.isFocused()) {
                                                        safeCursorPos = eb.getCursorPosition();
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    int scrollOffset = inputScrollOffsets.getOrDefault(el.id(), 0);
                                    float maxAvailableWidth = (clipMaxX - textX) / textScale;
                                    float availableWidth = Math.max(0, Math.min((el.width() - 8.0f) / textScale, maxAvailableWidth));

                                    if (safeCursorPos >= 0) {
                                        safeCursorPos = Math.min(textToDraw.length(), safeCursorPos);
                                        if (safeCursorPos < scrollOffset) scrollOffset = safeCursorPos;
                                        while (scrollOffset < safeCursorPos && font.width(textToDraw.substring(scrollOffset, safeCursorPos)) > availableWidth)
                                            scrollOffset++;
                                        inputScrollOffsets.put(el.id(), scrollOffset);
                                    } else {
                                        if (scrollOffset > textToDraw.length()) scrollOffset = textToDraw.length();
                                    }

                                    String subText = textToDraw.substring(scrollOffset);
                                    String visibleText = font.plainSubstrByWidth(subText, (int) availableWidth);

                                    int packedColor = (newA << 24) | (anim.color() & 0x00FFFFFF);
                                    if (!visibleText.isEmpty())
                                        font.drawInBatch(visibleText, 0, 0, packedColor, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, packedLight);

                                    if (safeCursorPos >= 0 && safeCursorPos >= scrollOffset) {
                                        int relativeCursorPos = safeCursorPos - scrollOffset;
                                        if (relativeCursorPos <= visibleText.length()) {
                                            long time = System.currentTimeMillis() / 500;
                                            if (time % 2 == 0) {
                                                String beforeCursor = visibleText.substring(0, relativeCursorPos);
                                                float cursorX = font.width(beforeCursor);
                                                float r = ((anim.color() >> 16) & 0xFF) / 255.0f, g = ((anim.color() >> 8) & 0xFF) / 255.0f, b = (anim.color() & 0xFF) / 255.0f;
                                                addQuadRaw(buffer.getBuffer(RenderType.gui()), poseStack.last().pose(), cursorX, -1.0f, cursorX + 1.5f, 10.0f, r, g, b, newA / 255.0f, 0.01f);
                                            }
                                        }
                                    }
                                } else {
                                    if (el.id().endsWith("_ph")) {
                                        poseStack.translate(textX, textY, zOffset + subZOffset);
                                        poseStack.scale(textScale, textScale, 1.0f);
                                        float maxAvailableWidth = (clipMaxX - textX) / textScale;
                                        float availableWidth = Math.max(0, Math.min((el.width() - 8.0f) / textScale, maxAvailableWidth));
                                        String visibleText = font.plainSubstrByWidth(textToDraw, (int) availableWidth);
                                        int packedColor = (newA << 24) | (anim.color() & 0x00FFFFFF);
                                        if (!visibleText.isEmpty())
                                            font.drawInBatch(visibleText, 0, 0, packedColor, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, packedLight);
                                    } else {
                                        if (!textToDraw.isEmpty()) {
                                            float maxAvailableWidth = (clipMaxX - textX) / textScale;
                                            float availableWidth = Math.max(0, Math.min(el.width() / textScale, maxAvailableWidth));

                                            int textWidth = font.width(textToDraw);
                                            String visibleText = textToDraw;

                                            if (el.width() > 0 && textWidth > availableWidth * 1.3f + 4) {
                                                int ellipsisWidth = font.width("...");
                                                if (availableWidth > ellipsisWidth)
                                                    visibleText = font.plainSubstrByWidth(textToDraw, (int) availableWidth - ellipsisWidth) + "...";
                                                else
                                                    visibleText = font.plainSubstrByWidth(textToDraw, (int) availableWidth);
                                            }

                                            float actualTextWidth = font.width(visibleText) * textScale;
                                            float alignOffsetX = 0;
                                            if ("center".equals(anim.textAlign))
                                                alignOffsetX = Math.max(0, (el.width() - actualTextWidth) / 2.0f);
                                            else if ("right".equals(anim.textAlign))
                                                alignOffsetX = Math.max(0, el.width() - actualTextWidth);

                                            float yOffset = (16.0f - 9.0f) * textScale / 2.0f;
                                            poseStack.translate(textX + alignOffsetX, textY + yOffset, zOffset + subZOffset);
                                            poseStack.scale(textScale, textScale, 1.0f);

                                            int packedColor = (newA << 24) | (anim.color() & 0x00FFFFFF);
                                            font.drawInBatch(visibleText, 0, 0, packedColor, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, packedLight);
                                        }
                                    }
                                }
                            } finally {
                                poseStack.popPose();
                            }
                        }
                    }
                }
            } finally {
                poseStack.popPose();
            }
        }
    }

    public static void drawRoundedRect(VertexConsumer builder, Matrix4f matrix, float minX, float minY, float maxX, float maxY, float rTL, float rTR, float rBR, float rBL, float r, float g, float b, float a, float zOffset, float cx0, float cy0, float cx1, float cy1, float cRTL, float cRTR, float cRBR, float cRBL) {
        float maxR = Math.max(Math.max(rTL, rTR), Math.max(rBR, rBL));
        if (maxR <= 0) {
            drawClippedQuad(builder, matrix, minX, minY, maxX, maxY, r, g, b, a, zOffset, cx0, cy0, cx1, cy1, cRTL, cRTR, cRBR, cRBL);
            return;
        }

        float maxR_Top = Math.max(rTL, rTR);
        float maxR_Bottom = Math.max(rBL, rBR);

        drawClippedQuad(builder, matrix, minX, minY + maxR_Top, maxX, maxY - maxR_Bottom, r, g, b, a, zOffset, cx0, cy0, cx1, cy1, cRTL, cRTR, cRBR, cRBL);

        for (int i = 0; i < maxR_Top; i++) {
            float insetL = 0;
            if (i < rTL) {
                float dy = rTL - i - 0.5f;
                if (dy < 0) dy = 0;
                float dx = (float) Math.sqrt(Math.max(0, rTL * rTL - dy * dy));
                insetL = rTL - dx;
            }
            float insetR = 0;
            if (i < rTR) {
                float dy = rTR - i - 0.5f;
                if (dy < 0) dy = 0;
                float dx = (float) Math.sqrt(Math.max(0, rTR * rTR - dy * dy));
                insetR = rTR - dx;
            }
            drawClippedQuad(builder, matrix, minX + insetL, minY + i, maxX - insetR, minY + i + 1, r, g, b, a, zOffset, cx0, cy0, cx1, cy1, cRTL, cRTR, cRBR, cRBL);
        }

        for (int i = 0; i < maxR_Bottom; i++) {
            float insetL = 0;
            if (i < rBL) {
                float dy = rBL - i - 0.5f;
                if (dy < 0) dy = 0;
                float dx = (float) Math.sqrt(Math.max(0, rBL * rBL - dy * dy));
                insetL = rBL - dx;
            }
            float insetR = 0;
            if (i < rBR) {
                float dy = rBR - i - 0.5f;
                if (dy < 0) dy = 0;
                float dx = (float) Math.sqrt(Math.max(0, rBR * rBR - dy * dy));
                insetR = rBR - dx;
            }
            drawClippedQuad(builder, matrix, minX + insetL, maxY - i - 1, maxX - insetR, maxY - i, r, g, b, a, zOffset, cx0, cy0, cx1, cy1, cRTL, cRTR, cRBR, cRBL);
        }
    }

    public static void drawClippedQuad(VertexConsumer builder, Matrix4f matrix, float minX, float minY, float maxX, float maxY, float rCol, float gCol, float bCol, float aCol, float zOffset, float cx0, float cy0, float cx1, float cy1, float cRTL, float cRTR, float cRBR, float cRBL) {
        float startY = Math.max(minY, cy0);
        float endY = Math.min(maxY, cy1);
        if (startY >= endY) return;

        float maxR_Top = Math.max(cRTL, cRTR);
        float maxR_Bottom = Math.max(cRBL, cRBR);

        float topZoneEnd = Math.min(endY, cy0 + maxR_Top);
        if (startY < topZoneEnd) {
            for (float y = startY; y < topZoneEnd; y++) {
                float nextY = Math.min(y + 1.0f, topZoneEnd);
                float midY = (y + nextY) / 2.0f;
                float dyL = (cy0 + cRTL) - midY;
                float insetL = 0;
                if (dyL > 0 && cRTL > 0) {
                    float dx = (float) Math.sqrt(Math.max(0, cRTL * cRTL - dyL * dyL));
                    insetL = cRTL - dx;
                }
                float dyR = (cy0 + cRTR) - midY;
                float insetR = 0;
                if (dyR > 0 && cRTR > 0) {
                    float dx = (float) Math.sqrt(Math.max(0, cRTR * cRTR - dyR * dyR));
                    insetR = cRTR - dx;
                }
                float drawX0 = Math.max(minX, cx0 + insetL);
                float drawX1 = Math.min(maxX, cx1 - insetR);
                if (drawX0 < drawX1)
                    addQuadRaw(builder, matrix, drawX0, y, drawX1, nextY, rCol, gCol, bCol, aCol, zOffset);
            }
        }

        float midZoneStart = Math.max(startY, cy0 + maxR_Top);
        float midZoneEnd = Math.min(endY, cy1 - maxR_Bottom);
        if (midZoneStart < midZoneEnd) {
            float drawX0 = Math.max(minX, cx0);
            float drawX1 = Math.min(maxX, cx1);
            if (drawX0 < drawX1)
                addQuadRaw(builder, matrix, drawX0, midZoneStart, drawX1, midZoneEnd, rCol, gCol, bCol, aCol, zOffset);
        }

        float bottomZoneStart = Math.max(midZoneStart, Math.max(startY, cy1 - maxR_Bottom));
        if (bottomZoneStart < endY) {
            for (float y = bottomZoneStart; y < endY; y++) {
                float nextY = Math.min(y + 1.0f, endY);
                float midY = (y + nextY) / 2.0f;
                float dyL = midY - (cy1 - cRBL);
                float insetL = 0;
                if (dyL > 0 && cRBL > 0) {
                    float dx = (float) Math.sqrt(Math.max(0, cRBL * cRBL - dyL * dyL));
                    insetL = cRBL - dx;
                }
                float dyR = midY - (cy1 - cRBR);
                float insetR = 0;
                if (dyR > 0 && cRBR > 0) {
                    float dx = (float) Math.sqrt(Math.max(0, cRBR * cRBR - dyR * dyR));
                    insetR = cRBR - dx;
                }
                float drawX0 = Math.max(minX, cx0 + insetL);
                float drawX1 = Math.min(maxX, cx1 - insetR);
                if (drawX0 < drawX1)
                    addQuadRaw(builder, matrix, drawX0, y, drawX1, nextY, rCol, gCol, bCol, aCol, zOffset);
            }
        }
    }

    public static void drawRoundedTexturedRect(VertexConsumer builder, Matrix4f matrix, float minX, float minY, float maxX, float maxY, float u0, float v0, float u1, float v1, float rTL, float rTR, float rBR, float rBL, float r, float g, float b, float a, float zOffset, int packedLight, int packedOverlay, float cx0, float cy0, float cx1, float cy1, float cRTL, float cRTR, float cRBR, float cRBL) {
        float maxR = Math.max(Math.max(rTL, rTR), Math.max(rBR, rBL));
        if (maxR <= 0) {
            drawClippedTexturedQuad(builder, matrix, minX, minY, maxX, maxY, u0, v0, u1, v1, r, g, b, a, zOffset, packedLight, packedOverlay, cx0, cy0, cx1, cy1, cRTL, cRTR, cRBR, cRBL);
            return;
        }

        float maxR_Top = Math.max(rTL, rTR);
        float maxR_Bottom = Math.max(rBL, rBR);
        float totalW = maxX - minX, totalH = maxY - minY;

        float cy0_t = minY + maxR_Top, cy1_t = maxY - maxR_Bottom;
        float cv0 = v0 + ((cy0_t - minY) / totalH) * (v1 - v0), cv1 = v0 + ((cy1_t - minY) / totalH) * (v1 - v0);

        if (cy0_t < cy1_t) {
            drawClippedTexturedQuad(builder, matrix, minX, cy0_t, maxX, cy1_t, u0, cv0, u1, cv1, r, g, b, a, zOffset, packedLight, packedOverlay, cx0, cy0, cx1, cy1, cRTL, cRTR, cRBR, cRBL);
        }

        for (int i = 0; i < maxR_Top; i++) {
            float insetL = 0;
            if (i < rTL) {
                float dy = rTL - i - 0.5f;
                if (dy < 0) dy = 0;
                float dx = (float) Math.sqrt(Math.max(0, rTL * rTL - dy * dy));
                insetL = rTL - dx;
            }
            float insetR = 0;
            if (i < rTR) {
                float dy = rTR - i - 0.5f;
                if (dy < 0) dy = 0;
                float dx = (float) Math.sqrt(Math.max(0, rTR * rTR - dy * dy));
                insetR = rTR - dx;
            }

            float x0 = minX + insetL, x1 = maxX - insetR;
            if (x0 < x1) {
                float cu0 = u0 + ((x0 - minX) / totalW) * (u1 - u0), cu1 = u0 + ((x1 - minX) / totalW) * (u1 - u0);
                float y0_t = minY + i, y1_t = minY + i + 1;
                float v0_t = v0 + ((y0_t - minY) / totalH) * (v1 - v0), v1_t = v0 + ((y1_t - minY) / totalH) * (v1 - v0);
                drawClippedTexturedQuad(builder, matrix, x0, y0_t, x1, y1_t, cu0, v0_t, cu1, v1_t, r, g, b, a, zOffset, packedLight, packedOverlay, cx0, cy0, cx1, cy1, cRTL, cRTR, cRBR, cRBL);
            }
        }

        for (int i = 0; i < maxR_Bottom; i++) {
            float insetL = 0;
            if (i < rBL) {
                float dy = rBL - i - 0.5f;
                if (dy < 0) dy = 0;
                float dx = (float) Math.sqrt(Math.max(0, rBL * rBL - dy * dy));
                insetL = rBL - dx;
            }
            float insetR = 0;
            if (i < rBR) {
                float dy = rBR - i - 0.5f;
                if (dy < 0) dy = 0;
                float dx = (float) Math.sqrt(Math.max(0, rBR * rBR - dy * dy));
                insetR = rBR - dx;
            }

            float x0 = minX + insetL, x1 = maxX - insetR;
            if (x0 < x1) {
                float cu0 = u0 + ((x0 - minX) / totalW) * (u1 - u0), cu1 = u0 + ((x1 - minX) / totalW) * (u1 - u0);
                float y0_b = maxY - i - 1, y1_b = maxY - i;
                float v0_b = v0 + ((y0_b - minY) / totalH) * (v1 - v0), v1_b = v0 + ((y1_b - minY) / totalH) * (v1 - v0);
                drawClippedTexturedQuad(builder, matrix, x0, y0_b, x1, y1_b, cu0, v0_b, cu1, v1_b, r, g, b, a, zOffset, packedLight, packedOverlay, cx0, cy0, cx1, cy1, cRTL, cRTR, cRBR, cRBL);
            }
        }
    }

    public static void drawClippedTexturedQuad(VertexConsumer builder, Matrix4f matrix, float minX, float minY, float maxX, float maxY, float u0, float v0, float u1, float v1, float rCol, float gCol, float bCol, float aCol, float zOffset, int packedLight, int packedOverlay, float cx0, float cy0, float cx1, float cy1, float cRTL, float cRTR, float cRBR, float cRBL) {
        float startY = Math.max(minY, cy0);
        float endY = Math.min(maxY, cy1);
        if (startY >= endY) return;
        float totalW = maxX - minX, totalH = maxY - minY;
        if (totalW <= 0 || totalH <= 0) return;

        float maxR_Top = Math.max(cRTL, cRTR);
        float maxR_Bottom = Math.max(cRBL, cRBR);

        float topZoneEnd = Math.min(endY, cy0 + maxR_Top);
        if (startY < topZoneEnd) {
            for (float y = startY; y < topZoneEnd; y++) {
                float nextY = Math.min(y + 1.0f, topZoneEnd);
                float midY = (y + nextY) / 2.0f;
                float dyL = (cy0 + cRTL) - midY;
                float insetL = 0;
                if (dyL > 0 && cRTL > 0) {
                    float dx = (float) Math.sqrt(Math.max(0, cRTL * cRTL - dyL * dyL));
                    insetL = cRTL - dx;
                }
                float dyR = (cy0 + cRTR) - midY;
                float insetR = 0;
                if (dyR > 0 && cRTR > 0) {
                    float dx = (float) Math.sqrt(Math.max(0, cRTR * cRTR - dyR * dyR));
                    insetR = cRTR - dx;
                }

                float drawX0 = Math.max(minX, cx0 + insetL);
                float drawX1 = Math.min(maxX, cx1 - insetR);
                if (drawX0 < drawX1) {
                    float cu0 = u0 + ((drawX0 - minX) / totalW) * (u1 - u0), cu1 = u0 + ((drawX1 - minX) / totalW) * (u1 - u0);
                    float cv0 = v0 + ((y - minY) / totalH) * (v1 - v0), cv1 = v0 + ((nextY - minY) / totalH) * (v1 - v0);
                    addTexturedQuadRaw(builder, matrix, drawX0, y, drawX1, nextY, cu0, cv0, cu1, cv1, rCol, gCol, bCol, aCol, zOffset, packedLight, packedOverlay);
                }
            }
        }

        float midZoneStart = Math.max(startY, cy0 + maxR_Top);
        float midZoneEnd = Math.min(endY, cy1 - maxR_Bottom);
        if (midZoneStart < midZoneEnd) {
            float drawX0 = Math.max(minX, cx0);
            float drawX1 = Math.min(maxX, cx1);
            if (drawX0 < drawX1) {
                float cu0 = u0 + ((drawX0 - minX) / totalW) * (u1 - u0), cu1 = u0 + ((drawX1 - minX) / totalW) * (u1 - u0);
                float cv0 = v0 + ((midZoneStart - minY) / totalH) * (v1 - v0), cv1 = v0 + ((midZoneEnd - minY) / totalH) * (v1 - v0);
                addTexturedQuadRaw(builder, matrix, drawX0, midZoneStart, drawX1, midZoneEnd, cu0, cv0, cu1, cv1, rCol, gCol, bCol, aCol, zOffset, packedLight, packedOverlay);
            }
        }

        float bottomZoneStart = Math.max(midZoneStart, Math.max(startY, cy1 - maxR_Bottom));
        if (bottomZoneStart < endY) {
            for (float y = bottomZoneStart; y < endY; y++) {
                float nextY = Math.min(y + 1.0f, endY);
                float midY = (y + nextY) / 2.0f;
                float dyL = midY - (cy1 - cRBL);
                float insetL = 0;
                if (dyL > 0 && cRBL > 0) {
                    float dx = (float) Math.sqrt(Math.max(0, cRBL * cRBL - dyL * dyL));
                    insetL = cRBL - dx;
                }
                float dyR = midY - (cy1 - cRBR);
                float insetR = 0;
                if (dyR > 0 && cRBR > 0) {
                    float dx = (float) Math.sqrt(Math.max(0, cRBR * cRBR - dyR * dyR));
                    insetR = cRBR - dx;
                }

                float drawX0 = Math.max(minX, cx0 + insetL);
                float drawX1 = Math.min(maxX, cx1 - insetR);
                if (drawX0 < drawX1) {
                    float cu0 = u0 + ((drawX0 - minX) / totalW) * (u1 - u0), cu1 = u0 + ((drawX1 - minX) / totalW) * (u1 - u0);
                    float cv0 = v0 + ((y - minY) / totalH) * (v1 - v0), cv1 = v0 + ((nextY - minY) / totalH) * (v1 - v0);
                    addTexturedQuadRaw(builder, matrix, drawX0, y, drawX1, nextY, cu0, cv0, cu1, cv1, rCol, gCol, bCol, aCol, zOffset, packedLight, packedOverlay);
                }
            }
        }
    }

    public static void addQuadRaw(VertexConsumer builder, Matrix4f matrix, float minX, float minY, float maxX, float maxY, float r, float g, float b, float a, float zOffset) {
        builder.addVertex(matrix, minX, maxY, zOffset).setColor(r, g, b, a);
        builder.addVertex(matrix, maxX, maxY, zOffset).setColor(r, g, b, a);
        builder.addVertex(matrix, maxX, minY, zOffset).setColor(r, g, b, a);
        builder.addVertex(matrix, minX, minY, zOffset).setColor(r, g, b, a);
    }

    public static void addTexturedQuadRaw(VertexConsumer builder, Matrix4f matrix, float minX, float minY, float maxX, float maxY, float u0, float v0, float u1, float v1, float r, float g, float b, float a, float zOffset, int packedLight, int packedOverlay) {
        builder.addVertex(matrix, minX, maxY, zOffset).setColor(r, g, b, a).setUv(u0, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
        builder.addVertex(matrix, maxX, maxY, zOffset).setColor(r, g, b, a).setUv(u1, v1).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
        builder.addVertex(matrix, maxX, minY, zOffset).setColor(r, g, b, a).setUv(u1, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
        builder.addVertex(matrix, minX, minY, zOffset).setColor(r, g, b, a).setUv(u0, v0).setOverlay(packedOverlay).setLight(packedLight).setNormal(0, 0, 1);
    }

    public static void drawRingSegment(VertexConsumer builder, Matrix4f matrix, float cx, float cy, float radius, float thickness, float startAngle, float endAngle, int color, float opacity, float zOffset) {
        float a = ((color >> 24) & 0xFF) / 255.0f * opacity;
        if (a <= 0) return;
        float r = ((color >> 16) & 0xFF) / 255.0f, g = ((color >> 8) & 0xFF) / 255.0f, b = (color & 0xFF) / 255.0f;
        int segments = 16;
        float angleStep = (endAngle - startAngle) / segments;
        for (int i = 0; i < segments; i++) {
            float theta1 = startAngle + i * angleStep, theta2 = startAngle + (i + 1) * angleStep;
            float cos1 = (float) Math.cos(theta1), sin1 = (float) Math.sin(theta1);
            float cos2 = (float) Math.cos(theta2), sin2 = (float) Math.sin(theta2);
            float x1_out = cx + cos1 * radius, y1_out = cy + sin1 * radius;
            float x1_in = cx + cos1 * (radius - thickness), y1_in = cy + sin1 * (radius - thickness);
            float x2_out = cx + cos2 * radius, y2_out = cy + sin2 * radius;
            float x2_in = cx + cos2 * (radius - thickness), y2_in = cy + sin2 * (radius - thickness);
            builder.addVertex(matrix, x1_out, y1_out, zOffset).setColor(r, g, b, a);
            builder.addVertex(matrix, x2_out, y2_out, zOffset).setColor(r, g, b, a);
            builder.addVertex(matrix, x2_in, y2_in, zOffset).setColor(r, g, b, a);
            builder.addVertex(matrix, x1_in, y1_in, zOffset).setColor(r, g, b, a);
            builder.addVertex(matrix, x1_out, y1_out, zOffset).setColor(r, g, b, a);
            builder.addVertex(matrix, x1_in, y1_in, zOffset).setColor(r, g, b, a);
            builder.addVertex(matrix, x2_in, y2_in, zOffset).setColor(r, g, b, a);
            builder.addVertex(matrix, x2_out, y2_out, zOffset).setColor(r, g, b, a);
        }
    }

    public interface ICustomRenderer {
        boolean render(ScreenBlockEntity.UIElement el, PoseStack poseStack, MultiBufferSource buffer, ScreenAnimator.AnimatedValues anim, float zOffset, float subZOffset, boolean renderRounded);
    }
}