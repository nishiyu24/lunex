package com.nishiyu.lunex.client.renderer;

import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

public class MainframeScreenUIBuilder {

    public static void buildElements(ScreenBlockEntity screenBe, SimpleMachineBlockEntity master, List<ScreenBlockEntity.UIElement> elements) {
        String mode = screenBe.getPersistentData().getString("DisplayMode");
        if (mode.isEmpty()) mode = "CAPACITY";

        float w = screenBe.screenWidth * (float) ScreenBlockEntity.RESOLUTION;
        float h = screenBe.screenHeight * (float) ScreenBlockEntity.RESOLUTION;

        // 共通背景 (青黒いネイビー)
        elements.add(new ScreenBlockEntity.UIElement(
                "mf_bg", "div", 0, 0, (int) w, (int) h, "", 0, 0xEE001122, null, 0, 1.0f, 0, 0, 1, 1, 0, 0, null
        ));

        // モードごとに表示内容を切り替える (今後の拡張が容易に)
        switch (mode) {
            case "CAPACITY":
                buildCapacityUI(master, elements, w, h);
                break;
            case "ITEM":
                buildItemUI(screenBe, master, elements, w, h);
                break;
            // 今後 "FLUID" や "STATUS" などをここに追加できます
            default:
                buildCapacityUI(master, elements, w, h);
                break;
        }
    }

    private static void buildCapacityUI(SimpleMachineBlockEntity master, List<ScreenBlockEntity.UIElement> elements, float w, float h) {
        double maxMB = master.mainframeTotalCapacityBytes / 1048576.0;
        double itemMB = master.mainframeUsedItemBytes / 1048576.0;
        double fluidMB = master.mainframeUsedFluidBytes / 1048576.0;
        double dataMB = master.mainframeUsedProgramBytes / 1048576.0;
        double usedMB = itemMB + fluidMB + dataMB;
        double ratio = master.mainframeTotalCapacityBytes > 0 ? usedMB / maxMB : 0.0;

        elements.add(new ScreenBlockEntity.UIElement(
                "mf_title", "text", 10, 10, (int) (w - 20), 20, "MAINFRAME STORAGE", 0xFF00E5FF, 0, null, 0, 1.0f, 0, 0, 1, 1, 0, 0, null
        ));

        String capText = String.format(java.util.Locale.US, "Total: %.2f / %.2f MB", usedMB, maxMB);
        elements.add(new ScreenBlockEntity.UIElement(
                "mf_cap", "text", 10, 35, (int) (w - 20), 15, capText, 0xFFFFFFFF, 0, null, 0, 1.0f, 0, 0, 1, 1, 0, 0, null
        ));

        elements.add(new ScreenBlockEntity.UIElement(
                "mf_bar_bg", "div", 10, 55, (int) (w - 20), 10, "", 0, 0xFF003366, null, 2, 1.0f, 0, 0, 1, 1, 0, 0, null
        ));

        float barW = (float) ((w - 20) * ratio);
        int barColor = ratio > 0.9 ? 0xFFFF3333 : 0xFF00E5FF;
        if (barW > 0) {
            elements.add(new ScreenBlockEntity.UIElement(
                    "mf_bar_fg", "div", 10, 55, (int) barW, 10, "", 0, barColor, null, 2, 1.0f, 0, 0, 1, 1, 0, 0, null
            ));
        }

        elements.add(new ScreenBlockEntity.UIElement(
                "mf_item", "text", 10, 75, (int) (w - 20), 12, String.format(java.util.Locale.US, "Item:  %.2f MB", itemMB), 0xFFAAAAFF, 0, null, 0, 1.0f, 0, 0, 1, 1, 0, 0, null
        ));
        elements.add(new ScreenBlockEntity.UIElement(
                "mf_fluid", "text", 10, 90, (int) (w - 20), 12, String.format(java.util.Locale.US, "Fluid: %.2f MB", fluidMB), 0xFFAAAAFF, 0, null, 0, 1.0f, 0, 0, 1, 1, 0, 0, null
        ));
        elements.add(new ScreenBlockEntity.UIElement(
                "mf_data", "text", 10, 105, (int) (w - 20), 12, String.format(java.util.Locale.US, "Data:  %.2f MB", dataMB), 0xFFAAAAFF, 0, null, 0, 1.0f, 0, 0, 1, 1, 0, 0, null
        ));
    }

    private static void buildItemUI(ScreenBlockEntity screenBe, SimpleMachineBlockEntity master, List<ScreenBlockEntity.UIElement> elements, float w, float h) {
        int itemCount = 0;
        String filter = screenBe.getPersistentData().getString("ScreenFilter");
        boolean isNbtFilter = filter.startsWith("{") && filter.endsWith("}");
        String searchStr = isNbtFilter ? filter.substring(1, filter.length() - 1) : filter;

        for (int i = 0; i < master.mainframeStorage.getSlots(); i++) {
            ItemStack stack = master.mainframeStorage.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (filter.isEmpty()) {
                    itemCount += stack.getCount();
                } else if (isNbtFilter) {
                    net.minecraft.world.item.component.CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
                    String nbtStr = customData.copyTag().toString();
                    if (nbtStr.contains(searchStr)) {
                        itemCount += stack.getCount();
                    }
                } else {
                    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (id.contains(searchStr) || stack.getHoverName().getString().contains(searchStr)) {
                        itemCount += stack.getCount();
                    }
                }
            }
        }

        elements.add(new ScreenBlockEntity.UIElement(
                "mf_title", "text", 10, 10, (int) (w - 20), 20, "ITEM MONITOR", 0xFF00E5FF, 0, null, 0, 1.0f, 0, 0, 1, 1, 0, 0, null
        ));
        elements.add(new ScreenBlockEntity.UIElement(
                "mf_filter", "text", 10, 35, (int) (w - 20), 15, "Filter: " + (filter.isEmpty() ? "Any" : filter), 0xFFAAAAFF, 0, null, 0, 1.0f, 0, 0, 1, 1, 0, 0, null
        ));
        elements.add(new ScreenBlockEntity.UIElement(
                "mf_count", "text", 0, 70, (int) w, 50, String.valueOf(itemCount), 0xFF00E5FF, 0, Map.of("text-align", "center"), 0, 1.0f, 0, 0, 1, 1, 0, 0, null
        ));
    }
}