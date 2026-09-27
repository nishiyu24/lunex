package com.nishiyu.lunex.webrender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HtmlNode {
    public String tag;
    public String id = "";
    public List<String> classes = new ArrayList<>();
    public Map<String, String> attrs = new HashMap<>();
    public List<HtmlNode> children = new ArrayList<>();
    public String text = "";
    public HtmlNode parent = null;

    public HtmlNode(String tag) {
        this.tag = tag;
    }

    public HtmlNode getElementById(String targetId) {
        if (targetId.equals(this.id)) return this;
        for (HtmlNode child : children) {
            HtmlNode found = child.getElementById(targetId);
            if (found != null) return found;
        }
        return null;
    }

    // ★ 追加: ネイティブNeatアタッチや仮想DOM更新のためのディープコピー
    public HtmlNode cloneNode() {
        HtmlNode copy = new HtmlNode(this.tag);
        copy.id = this.id;
        copy.classes.addAll(this.classes);
        copy.attrs.putAll(this.attrs);
        copy.text = this.text;
        for (HtmlNode child : this.children) {
            HtmlNode childCopy = child.cloneNode();
            childCopy.parent = copy;
            copy.children.add(childCopy);
        }
        return copy;
    }
}