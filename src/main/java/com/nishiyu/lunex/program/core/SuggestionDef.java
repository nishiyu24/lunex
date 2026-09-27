package com.nishiyu.lunex.program.core;

import java.util.List;

public class SuggestionDef {
    public String type;
    public String module;
    public String name;
    public List<String> args;
    public List<String> rets;
    public String description;
    public String descriptionEn;    // ★追加
    public boolean isAsync;
    public boolean hasExecIn;
    public List<String> execOuts;

    public SuggestionDef(String type, String module, String name, List<String> args, List<String> rets, String description, String descriptionEn, boolean isAsync, boolean hasExecIn, List<String> execOuts) {
        this.type = type;
        this.module = module;
        this.name = name;
        this.args = args;
        this.rets = rets;
        this.description = description;
        this.descriptionEn = descriptionEn; // ★追加
        this.isAsync = isAsync;
        this.hasExecIn = hasExecIn;
        this.execOuts = execOuts;
    }
}