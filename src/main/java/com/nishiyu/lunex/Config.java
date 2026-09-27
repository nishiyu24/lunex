package com.nishiyu.lunex;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLE_WEB_SERVER = BUILDER
            .comment("Set to false to disable the local web server for the Lunex editor.")
            .define("enableWebServer", true);
    public static final ModConfigSpec.BooleanValue ENABLE_YOUTUBE_SUPPORT = BUILDER
            .comment("Enable YouTube video playback support (will download yt-dlp automatically in the background).")
            .define("enableYouTubeSupport", false);
    public static final ModConfigSpec.BooleanValue ENABLE_HTTP_API = BUILDER
            .comment("Enable or disable the HTTP API for Lua scripts.")
            .define("enableHttpApi", true);
    public static final ModConfigSpec.BooleanValue HTTP_ALLOW_LOCAL_IP = BUILDER
            .comment("Allow HTTP requests to local/private IP addresses (e.g. localhost, 192.168.x.x).",
                    "Set to false to prevent SSRF (Server-Side Request Forgery) attacks on multiplayer servers.")
            .define("httpAllowLocalIp", false);
    public static final ModConfigSpec.ConfigValue<String> WORKSPACE_DIR = BUILDER
            .comment("The directory where Lua scripts, UI files, and JSON data are stored.",
                    "Relative to the 'run' directory (the game's root folder).")
            .define("workspaceDirectory", "lunex_programs");
    // -----------------------------------------------------------------
    // 新規追加: Traitの表示設定
    // -----------------------------------------------------------------
    public static final ModConfigSpec.EnumValue<TraitVisibility> TRAIT_VISIBILITY = BUILDER
            .comment("Set the visibility level of locked traits in the Bio Printer.",
                    "HIDDEN: Completely hidden.",
                    "SILHOUETTE: Only shows existence (???).",
                    "NAME_AND_MATERIALS: Shows name and required materials.",
                    "FULL: Shows all information including stats and abilities.")
            .defineEnum("traitVisibility", TraitVisibility.SILHOUETTE);
    // -----------------------------------------------------------------
    public static final ModConfigSpec SPEC = BUILDER.build();

    // -----------------------------------------------------------------
    // General Settings (一般的な設定)
    // -----------------------------------------------------------------
    static {
        BUILDER.push("General");
    }

    static {
        BUILDER.pop();
    }

    // -----------------------------------------------------------------
    // HTTP API Settings (HTTP通信に関する設定)
    // -----------------------------------------------------------------
    static {
        BUILDER.push("HTTP API");
    }

    static {
        BUILDER.pop();
    }

    // -----------------------------------------------------------------
    // Workspace Settings (作業スペースに関する設定)
    // -----------------------------------------------------------------
    static {
        BUILDER.push("Workspace");
    }

    static {
        BUILDER.pop();
    }

    // -----------------------------------------------------------------
    // Enum定義: 未解放Traitの表示レベル
    // -----------------------------------------------------------------
    public enum TraitVisibility {
        HIDDEN,             // 全非表示（ツリーに存在すらしないように見せる）
        SILHOUETTE,         // 存在表示（ノードは表示するが「???」にする）
        NAME_AND_MATERIALS, // 名前＆材料表示（効果や説明は隠す）
        FULL                // 性能まで全表示（すべて丸見え）
    }
}