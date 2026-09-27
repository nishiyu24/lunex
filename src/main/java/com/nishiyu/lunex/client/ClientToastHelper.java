package com.nishiyu.lunex.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public class ClientToastHelper {
    public static void showErrorToast(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getToasts() != null) {
            String[] lines = message.split("\n");

            Component title = Component.literal("§c[Lua] " + (lines.length > 0 ? lines[0] : "Error"));

            // ★変更: トーストの枠は2行分しか入らないため、LineとErrorを繋げて1行にする
            String descText = message;
            if (lines.length >= 3) {
                descText = lines[1] + " | " + lines[2];
            } else if (lines.length == 2) {
                descText = lines[1];
            }

            Component desc = Component.literal(descText);

            mc.getToasts().addToast(new SystemToast(
                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    title,
                    desc
            ));
        }
    }
}