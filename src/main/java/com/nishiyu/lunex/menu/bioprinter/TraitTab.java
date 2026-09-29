package com.nishiyu.lunex.menu.bioprinter;

import com.nishiyu.lunex.Config;
import com.nishiyu.lunex.entity.BioMobGenerator;
import com.nishiyu.lunex.entity.traits.TraitCategory;
import com.nishiyu.lunex.entity.traits.TraitDef;
import com.nishiyu.lunex.entity.traits.TraitRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.*;

public class TraitTab extends AbstractPrinterTab {
    private final Map<String, Float> nodeX = new HashMap<>();
    private final Map<String, Float> nodeY = new HashMap<>();
    private final List<String> roots = new ArrayList<>();
    private float panX = 0;
    private float panY = 0;
    private float zoom = 0.6f;
    private boolean isDragging = false;
    private boolean layoutCalculated = false;
    private int currentLeafIndex = 0;

    public TraitTab(BioPrinterScreen screen, BioPrinterMenu menu) {
        super(screen, menu);
    }

    private float calculateLogicalPosition(String node, Map<String, List<String>> treeChildren, Map<String, Float> logicalY) {
        List<String> children = treeChildren.get(node);
        if (children == null || children.isEmpty()) {
            float y = currentLeafIndex++;
            logicalY.put(node, y);
            return y;
        }

        float sum = 0;
        for (String child : children) {
            sum += calculateLogicalPosition(child, treeChildren, logicalY);
        }
        float avgY = sum / children.size();
        logicalY.put(node, avgY);
        return avgY;
    }

    private void calculateLayout() {
        if (layoutCalculated) return;
        roots.clear();
        nodeX.clear();
        nodeY.clear();

        Map<String, List<String>> treeChildren = new HashMap<>();
        Map<String, Integer> depths = new HashMap<>();

        Queue<String> queue = new LinkedList<>();
        for (TraitDef def : TraitRegistry.TRAITS) {
            treeChildren.put(def.key(), new ArrayList<>());
            if (def.prerequisites().isEmpty()) {
                roots.add(def.key());
                depths.put(def.key(), 1);
                queue.add(def.key());
            }
        }

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            int currDepth = depths.get(curr);

            for (TraitDef def : TraitRegistry.TRAITS) {
                if (def.prerequisites().contains(curr)) {
                    if (!depths.containsKey(def.key())) {
                        depths.put(def.key(), currDepth + 1);
                        treeChildren.get(curr).add(def.key());
                        queue.add(def.key());
                    }
                }
            }
        }

        for (TraitDef def : TraitRegistry.TRAITS) {
            if (!depths.containsKey(def.key())) {
                roots.add(def.key());
                depths.put(def.key(), 1);
                treeChildren.putIfAbsent(def.key(), new ArrayList<>());
            }
        }

        Map<String, Float> logicalY = new HashMap<>();
        currentLeafIndex = 0;

        for (String root : roots) {
            calculateLogicalPosition(root, treeChildren, logicalY);
        }

        float BASE_X = 0f;
        float BASE_Y = 0f;
        nodeX.put("BASE", BASE_X);
        nodeY.put("BASE", BASE_Y);

        int totalLeaves = Math.max(1, currentLeafIndex);

        float MIN_NODE_SPACING = 32.0f;
        float minRadius = (MIN_NODE_SPACING * totalLeaves) / (float) (2 * Math.PI);

        float INITIAL_RADIUS = Math.max(120.0f, minRadius);
        float LAYER_DISTANCE = 60.0f;

        for (String node : depths.keySet()) {
            int depth = depths.get(node);
            float lY = logicalY.get(node);

            float angle = (lY / totalLeaves) * 2.0f * (float) Math.PI;
            float radius = INITIAL_RADIUS + (depth - 1) * LAYER_DISTANCE;

            float cx = BASE_X + radius * (float) Math.cos(angle);
            float cy = BASE_Y + radius * (float) Math.sin(angle);

            nodeX.put(node, cx);
            nodeY.put(node, cy);
        }

        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;

        for (String key : nodeX.keySet()) {
            float x = nodeX.get(key);
            float y = nodeY.get(key);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
        }

        float offsetX = -(maxX + minX) / 2.0f;
        float offsetY = -(maxY + minY) / 2.0f;
        for (String key : nodeX.keySet()) {
            nodeX.put(key, nodeX.get(key) + offsetX);
            nodeY.put(key, nodeY.get(key) + offsetY);
        }

        layoutCalculated = true;
    }

    private void drawDirectLine(GuiGraphics guiGraphics, float x1, float y1, float x2, float y2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = (float) Math.atan2(dy, dx);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x1, y1, 0);
        guiGraphics.pose().mulPose(new org.joml.Quaternionf().rotateZ(angle));
        guiGraphics.fill(0, -1, (int) length, 1, color);
        guiGraphics.pose().popPose();
    }

    private int getCategoryColor(TraitCategory category) {
        return switch (category) {
            case BASE_ENHANCEMENT -> 0xFF89B4FA;
            case COMBAT_ABILITY -> 0xFFCBA6F7;
            case ELEMENTAL_CORE -> 0xFFA6E3A1;
            case MORPHOLOGY -> 0xFFF9E2AF;
            case ENVIRONMENTAL -> 0xFF89DCEB;
            case UTILITY -> 0xFFFAB387;
            case WEAKNESS_STAT, WEAKNESS_TRAIT -> 0xFFF38BA8;
        };
    }

    private int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int leftPos, int topPos) {
        if (!layoutCalculated) calculateLayout();

        int viewX = leftPos + 10;
        int viewY = topPos + 30;
        int viewW = 265;
        int viewH = 183;

        guiGraphics.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xFF050510);
        guiGraphics.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
        guiGraphics.pose().pushPose();

        float centerX = viewX + viewW / 2.0f;
        float centerY = viewY + viewH / 2.0f;
        guiGraphics.pose().translate(centerX + panX, centerY + panY, 0);
        guiGraphics.pose().scale(zoom, zoom, 1);

        Map<String, Integer> mats = this.menu.blockEntity.getMaterialCounts();
        List<Integer> selectedTraits = this.menu.blockEntity.getSelectedTraits();

        int currentPoints = TraitRegistry.getConsumedPoints(selectedTraits);
        int maxTraits = TraitRegistry.getMaxPoints(mats, selectedTraits);

        boolean[] unlocked = new boolean[TraitRegistry.TRAITS.size()];
        for (int i = 0; i < TraitRegistry.TRAITS.size(); i++) {
            unlocked[i] = TraitRegistry.isTraitUnlocked(i, mats);
        }

        Config.TraitVisibility visibility = Config.TRAIT_VISIBILITY.get();

        int[] nodeState = new int[TraitRegistry.TRAITS.size()];
        Arrays.fill(nodeState, 2);

        for (int i = 0; i < TraitRegistry.TRAITS.size(); i++) {
            if (unlocked[i]) {
                nodeState[i] = 0;
            } else {
                TraitDef def = TraitRegistry.TRAITS.get(i);
                if (def.prerequisites().isEmpty()) {
                    nodeState[i] = 1;
                } else {
                    for (String preKey : def.prerequisites()) {
                        int preIdx = TraitRegistry.getTraitIndex(preKey);
                        if (preIdx != -1 && unlocked[preIdx]) {
                            nodeState[i] = 1;
                            break;
                        }
                    }
                }
            }
        }

        if (visibility == Config.TraitVisibility.HIDDEN) {
            for (int i = 0; i < TraitRegistry.TRAITS.size(); i++) {
                if (nodeState[i] == 2) {
                    nodeState[i] = 3;
                }
            }
        }

        float baseX = nodeX.get("BASE");
        float baseY = nodeY.get("BASE");
        for (String rootKey : roots) {
            int rootIdx = TraitRegistry.getTraitIndex(rootKey);
            int lineColor = (rootIdx != -1 && nodeState[rootIdx] == 0) ? 0xFF89B4FA : 0xFF313244;
            drawDirectLine(guiGraphics, baseX, baseY, nodeX.get(rootKey), nodeY.get(rootKey), lineColor);
        }

        for (int i = 0; i < TraitRegistry.TRAITS.size(); i++) {
            if (nodeState[i] == 3) continue;
            TraitDef childDef = TraitRegistry.TRAITS.get(i);
            float childX = nodeX.get(childDef.key());
            float childY = nodeY.get(childDef.key());

            for (String preKey : childDef.prerequisites()) {
                int preIdx = TraitRegistry.getTraitIndex(preKey);
                if (preIdx != -1 && nodeState[preIdx] != 3) {
                    TraitDef parentDef = TraitRegistry.TRAITS.get(preIdx);
                    float parentX = nodeX.get(parentDef.key());
                    float parentY = nodeY.get(parentDef.key());

                    int lineColor = (nodeState[i] == 0 && nodeState[preIdx] == 0) ? 0xFF89B4FA : 0xFF313244;
                    drawDirectLine(guiGraphics, parentX, parentY, childX, childY, lineColor);
                }
            }
        }

        float localMouseX = (mouseX - (centerX + panX)) / zoom;
        float localMouseY = (mouseY - (centerY + panY)) / zoom;
        float nodeW = 16;
        float nodeH = 16;

        TraitDef hoveredDef = null;
        int hoveredNodeState = -1;
        boolean hoveredBase = false;

        float bnx = baseX - 10;
        float bny = baseY - 10;
        if (localMouseX >= bnx && localMouseX <= bnx + 20 && localMouseY >= bny && localMouseY <= bny + 20) {
            hoveredBase = true;
        }
        guiGraphics.fill((int) bnx, (int) bny, (int) (bnx + 20), (int) (bny + 20), 0xFFCBA6F7);
        guiGraphics.renderOutline((int) bnx - 2, (int) bny - 2, 24, 24, 0xFFFFFFFF);

        for (int i = 0; i < TraitRegistry.TRAITS.size(); i++) {
            if (nodeState[i] == 3) continue;

            TraitDef def = TraitRegistry.TRAITS.get(i);
            int state = nodeState[i];

            boolean isUnlocked = (state == 0);
            boolean isChecked = selectedTraits.contains(i);
            boolean canToggle;
            if (isChecked) {
                if (def.isNegative()) {
                    List<Integer> temp = new ArrayList<>(selectedTraits);
                    temp.remove(Integer.valueOf(i));
                    canToggle = TraitRegistry.getConsumedPoints(temp) <= TraitRegistry.getMaxPoints(mats, temp);
                } else {
                    canToggle = true;
                }
            } else {
                canToggle = isUnlocked && (def.isNegative() || currentPoints < maxTraits);
            }

            float nx = nodeX.get(def.key()) - nodeW / 2;
            float ny = nodeY.get(def.key()) - nodeH / 2;
            boolean hover = localMouseX >= nx && localMouseX <= nx + nodeW && localMouseY >= ny && localMouseY <= ny + nodeH;

            if (hover) {
                hoveredDef = def;
                hoveredNodeState = state;
            }

            int catColor = getCategoryColor(def.category());

            int bgColor = 0xFF1E1E2E;
            if (state == 2) bgColor = 0xFF11111B;
            else if (state == 1) bgColor = 0xFF181825;
            else if (isChecked) bgColor = catColor;
            else if (!canToggle) bgColor = 0xFF11111B;
            else bgColor = darkenColor(catColor, 0.25f);

            if (hover && canToggle && !isChecked) bgColor = darkenColor(catColor, 0.45f);

            int outlineColor = 0xFF45475A;
            if (state >= 1) outlineColor = 0xFF313244;
            else if (isChecked) outlineColor = 0xFFFFFFFF;
            else if (!canToggle) outlineColor = 0xFF313244;
            else outlineColor = catColor;

            guiGraphics.fill((int) nx, (int) ny, (int) (nx + nodeW), (int) (ny + nodeH), bgColor);
            guiGraphics.renderOutline((int) nx, (int) ny, (int) nodeW, (int) nodeH, outlineColor);
        }

        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
        guiGraphics.renderOutline(viewX, viewY, viewW, viewH, 0xFF45475A);

        // ★ ハードコードの言語判定を削除し、Translatableに統一
        guiGraphics.drawString(screen.getFont(), Component.translatable(BioPrinterTranslations.TRAIT_TITLE).getString(), leftPos + 15, topPos + 35, 0xFF89DCEB, false);
        guiGraphics.drawString(screen.getFont(), Component.translatable(BioPrinterTranslations.TRAIT_POINTS, currentPoints, maxTraits).getString(), leftPos + 15, topPos + 50, 0xFFA6ADC8, false);

        if (hoveredBase && screen.isHovered(mouseX, mouseY, viewX, viewY, viewW, viewH)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable(BioPrinterTranslations.TRAIT_BASE_CORE).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable(BioPrinterTranslations.TRAIT_BASE_DESC).withStyle(net.minecraft.ChatFormatting.GRAY));
            guiGraphics.renderComponentTooltip(screen.getFont(), tooltip, mouseX, mouseY);

        } else if (hoveredDef != null && screen.isHovered(mouseX, mouseY, viewX, viewY, viewW, viewH)) {
            List<Component> tooltip = new ArrayList<>();

            // Trait自体の名前・説明は既存のロジックを保持 (もしこれらもComponent化できているなら.translatable()を推奨します)
            boolean isJapanese = Minecraft.getInstance().getLanguageManager().getSelected().equals("ja_jp");
            String traitName = isJapanese ? hoveredDef.japaneseName() : hoveredDef.englishName();
            String traitDesc = isJapanese ? hoveredDef.japaneseDescription() : hoveredDef.englishDescription();

            if (hoveredNodeState == 0) {
                tooltip.add(Component.literal(traitName).withStyle(net.minecraft.ChatFormatting.GOLD));
                tooltip.add(Component.literal("[" + hoveredDef.category().getDisplayName() + "]").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
                tooltip.add(Component.literal(traitDesc).withStyle(net.minecraft.ChatFormatting.GRAY));

                tooltip.add(Component.literal(" "));
                if (hoveredDef.isNegative()) {
                    tooltip.add(Component.translatable(BioPrinterTranslations.TRAIT_EFFECT_POS).withStyle(net.minecraft.ChatFormatting.RED));
                } else {
                    tooltip.add(Component.translatable(BioPrinterTranslations.TRAIT_EFFECT_NEG).withStyle(net.minecraft.ChatFormatting.GREEN));
                }

                if (selectedTraits.contains(TraitRegistry.getTraitIndex(hoveredDef.key()))) {
                    tooltip.add(Component.translatable(BioPrinterTranslations.TRAIT_STATUS_EQUIPPED).withStyle(net.minecraft.ChatFormatting.AQUA));
                } else {
                    tooltip.add(Component.translatable(BioPrinterTranslations.TRAIT_STATUS_UNLOCKED).withStyle(net.minecraft.ChatFormatting.YELLOW));
                }

            } else {
                boolean isAvailable = (hoveredNodeState == 1);
                boolean showDesc = (visibility == Config.TraitVisibility.FULL);
                boolean isSilhouette = (hoveredNodeState == 2 && visibility == Config.TraitVisibility.SILHOUETTE);

                if (isSilhouette) {
                    tooltip.add(Component.literal("???").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                    tooltip.add(Component.translatable(BioPrinterTranslations.TRAIT_LOCKED).withStyle(net.minecraft.ChatFormatting.GRAY));
                } else {
                    tooltip.add(Component.literal(traitName).withStyle(net.minecraft.ChatFormatting.GRAY));
                    tooltip.add(Component.literal("[" + hoveredDef.category().getDisplayName() + "]").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));

                    if (showDesc) {
                        tooltip.add(Component.literal(traitDesc).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                        tooltip.add(Component.literal(" "));
                    }

                    if (hoveredDef.isNegative()) {
                        tooltip.add(Component.translatable(BioPrinterTranslations.TRAIT_UNLOCK_COND).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                    } else {
                        tooltip.add(Component.translatable(BioPrinterTranslations.TRAIT_REQ_MATS).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                        if (hoveredDef.requirements() != null) {
                            for (TraitDef.ItemRequirement req : hoveredDef.requirements()) {
                                int currentCount = BioMobGenerator.getCount(mats, req.item());
                                tooltip.add(Component.literal("- ")
                                        .append(Component.translatable(req.item().getDescriptionId()))
                                        .append(Component.literal(String.format(": %d / %d", currentCount, req.count())))
                                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                            }
                        }
                    }
                }
            }
            guiGraphics.renderComponentTooltip(screen.getFont(), tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int leftPos, int topPos) {
        int viewX = leftPos + 10;
        int viewY = topPos + 30;
        int viewW = 265;
        int viewH = 183;

        if (screen.isHovered(mouseX, mouseY, viewX, viewY, viewW, viewH)) {
            if (button == 0) {
                float centerX = viewX + viewW / 2.0f;
                float centerY = viewY + viewH / 2.0f;
                float localMouseX = (float) ((mouseX - (centerX + panX)) / zoom);
                float localMouseY = (float) ((mouseY - (centerY + panY)) / zoom);

                Map<String, Integer> mats = this.menu.blockEntity.getMaterialCounts();
                boolean clickedNode = false;

                if (!layoutCalculated) calculateLayout();

                for (int i = 0; i < TraitRegistry.TRAITS.size(); i++) {
                    TraitDef def = TraitRegistry.TRAITS.get(i);
                    float nx = nodeX.get(def.key()) - 8;
                    float ny = nodeY.get(def.key()) - 8;

                    if (localMouseX >= nx && localMouseX <= nx + 16 && localMouseY >= ny && localMouseY <= ny + 16) {
                        if (TraitRegistry.isTraitUnlocked(i, mats)) {
                            screen.handleButtonClick(200 + i);
                            clickedNode = true;
                            break;
                        }
                    }
                }
                if (!clickedNode) this.isDragging = true;
                return true;
            } else if (button == 1 || button == 2) {
                this.isDragging = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int leftPos = (screen.width - 320) / 2;
        int topPos = (screen.height - 220) / 2;
        if (screen.isHovered(mouseX, mouseY, leftPos + 10, topPos + 30, 265, 183)) {
            if (scrollY > 0) zoom *= 1.15f;
            else if (scrollY < 0) zoom /= 1.15f;
            zoom = Mth.clamp(zoom, 0.1f, 2.5f);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int leftPos, int topPos) {
        if (this.isDragging) {
            this.panX = (float) (this.panX + dragX);
            this.panY = (float) (this.panY + dragY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isDragging) {
            this.isDragging = false;
            return true;
        }
        return false;
    }
}