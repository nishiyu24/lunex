// 上書き: HtmlNode.java
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

    // 擬似要素(::before, ::after)などで自動生成された仮想ノードかどうか
    public boolean isPseudoNode = false;

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

    /**
     * data-* 属性のみを抽出してマップとして返します（dataset相当）
     */
    public Map<String, String> getDataset() {
        Map<String, String> dataset = new HashMap<>();
        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            if (entry.getKey().startsWith("data-")) {
                // "data-user-name" -> "userName" のようなキャメルケース変換はLua側またはAPI側で行う想定
                dataset.put(entry.getKey(), entry.getValue());
            }
        }
        return dataset;
    }

    /**
     * モダンなDOM検索API (単一要素)
     */
    public HtmlNode querySelector(String selector) {
        CssParser.StyleRule rule = new CssParser.StyleRule(selector);
        return querySelectorInternal(this, rule);
    }

    private HtmlNode querySelectorInternal(HtmlNode node, CssParser.StyleRule rule) {
        if (rule.matches(node)) return node;
        for (HtmlNode child : node.children) {
            HtmlNode found = querySelectorInternal(child, rule);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * モダンなDOM検索API (複数要素)
     */
    public List<HtmlNode> querySelectorAll(String selector) {
        CssParser.StyleRule rule = new CssParser.StyleRule(selector);
        List<HtmlNode> results = new ArrayList<>();
        querySelectorAllInternal(this, rule, results);
        return results;
    }

    private void querySelectorAllInternal(HtmlNode node, CssParser.StyleRule rule, List<HtmlNode> results) {
        if (rule.matches(node)) results.add(node);
        for (HtmlNode child : node.children) {
            querySelectorAllInternal(child, rule, results);
        }
    }

    public HtmlNode cloneNode() {
        HtmlNode copy = new HtmlNode(this.tag);
        copy.id = this.id;
        copy.classes.addAll(this.classes);
        copy.attrs.putAll(this.attrs);
        copy.text = this.text;
        copy.isPseudoNode = this.isPseudoNode;
        for (HtmlNode child : this.children) {
            HtmlNode childCopy = child.cloneNode();
            childCopy.parent = copy;
            copy.children.add(childCopy);
        }
        return copy;
    }
}