package com.nishiyu.lunex.menu.utiles;

import com.nishiyu.lunex.Lunex;
import net.minecraft.Util;

import java.net.URI;

public class EditorLauncher {

    // ★ Javaで立てた LocalWebServer のベースURL
    private static final String BASE_URL = "http://localhost:14320/";

    // 引数としてページ名（"machine.html" や "entity.html"）を受け取るように変更
    public static void launchEditor(String pageName) {
        String targetUrl = BASE_URL + pageName;
        try {
            Lunex.LOGGER.info("Opening Web Editor at: " + targetUrl);
            Util.getPlatform().openUri(new URI(targetUrl));
        } catch (Exception e) {
            Lunex.LOGGER.error("[EditorLauncher] エラー: Webエディタの起動に失敗しました", e);
        }
    }
}