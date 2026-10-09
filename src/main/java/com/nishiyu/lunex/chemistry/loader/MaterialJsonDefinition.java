package com.nishiyu.lunex.chemistry.loader;

import java.util.HashMap;
import java.util.Map;

public class MaterialJsonDefinition {
    // "item", "tag", "preset"
    private String target_type;
    // ターゲット識別子 (例: "minecraft:oak_log", "minecraft:logs", "base_wood")
    private String target;
    // 継承元 (例: "preset:base_wood", "item:minecraft:stone")。親がなければ null
    private String base;
    // 追加する化合物と数量
    private Map<String, Integer> compounds = new HashMap<>();
    // 追加する単体元素と数量
    private Map<String, Integer> elements = new HashMap<>();

    public String getTargetType() { return target_type != null ? target_type.toLowerCase() : "item"; }
    public String getTarget() { return target; }
    public String getBase() { return base; }
    public Map<String, Integer> getCompounds() { return compounds; }
    public Map<String, Integer> getElements() { return elements; }
}