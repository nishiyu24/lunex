package com.nishiyu.lunex.api;

public final class MainframeConstants {
    // 既存の FEATURE_, API_ 定数はそのまま残してください
    public static final String FEATURE_DATABASE = "lunex:database";
    public static final String FEATURE_ROUTER   = "lunex:router";
    public static final String FEATURE_PRINTER  = "lunex:printer";
    public static final String FEATURE_PROBE    = "lunex:probe";
    public static final String FEATURE_SCREEN   = "lunex:screen";
    public static final String FEATURE_SPEAKER  = "lunex:speaker";
    public static final String FEATURE_COMMAND  = "lunex:command";

    public static final String API_FS        = "fs";
    public static final String API_NET       = "net";
    public static final String API_ROUTER    = "router";
    public static final String API_PRINTER   = "printer";
    public static final String API_DATABASE  = "database";
    public static final String API_SCREEN    = "screen";
    public static final String API_SPEAKER   = "speaker";
    public static final String API_INVENTORY = "inventory";
    public static final String API_STORAGE   = "storage";
    public static final String API_RS        = "rs";
    public static final String API_DEVICE    = "device";
    public static final String API_MACHINE   = "machine";
    public static final String API_COMMANDS  = "commands";
    public static final String API_HTTP      = "http";
    public static final String API_LAN       = "lan";

    // =========================================
    // ★追加: 構成パーツ配置ルール (Placements)
    // =========================================
    public static final String PLACEMENT_EDGE   = "edge";   // 辺および角（フレームなど）
    public static final String PLACEMENT_FACE   = "face";   // 外面（プローブ、スクリーンなど）
    public static final String PLACEMENT_INSIDE = "inside"; // 内部（ルーター、データベースなど）
}