package com.nishiyu.lunex.chemistry.loader;

import java.util.ArrayList;
import java.util.List;

public class MaterialJsonDefinition {
    private String target_type;
    private String target;
    private String base;

    // JSONからは "METALLIC:SOLID" や "NONE" といった文字列のリストとして受け取る
    private List<String> dna = new ArrayList<>();

    public String getTargetType() { return target_type != null ? target_type.toLowerCase() : "item"; }
    public String getTarget() { return target; }
    public String getBase() { return base; }
    public List<String> getDna() { return dna; }
}