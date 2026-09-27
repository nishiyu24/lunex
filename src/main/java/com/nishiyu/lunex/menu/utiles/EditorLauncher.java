package com.nishiyu.lunex.menu.utiles;

import com.nishiyu.lunex.Lunex;
import net.minecraft.Util;

import java.net.URI;

public class EditorLauncher {

    // ★ Javaで立てた LocalWebServer のポートを指定
    private static final String EDITOR_URL = "http://localhost:14320";

    public static void launchEditor() {
        try {
            Lunex.LOGGER.info("Opening Web Editor at: " + EDITOR_URL);
            Util.getPlatform().openUri(new URI(EDITOR_URL));
        } catch (Exception e) {
            Lunex.LOGGER.error("[EditorLauncher] エラー: Webエディタの起動に失敗しました", e);
        }
    }
}