package com.nishiyu.lunex.webrender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DomUtils {

    // --- DOM検索・更新系 ---

    public static HtmlNode findNodeById(HtmlNode node, String id) {
        if (node == null || id == null) return null;
        if (id.equals(node.id)) return node;
        for (HtmlNode child : node.children) {
            HtmlNode found = findNodeById(child, id);
            if (found != null) return found;
        }
        return null;
    }

    public static void updateAttribute(HtmlNode target, String attrName, String attrValue, boolean remove) {
        if (target == null) return;
        if (remove) {
            target.attrs.remove(attrName);
            if ("class".equals(attrName)) target.classes.clear();
        } else {
            String val = attrValue != null ? attrValue : "true";
            target.attrs.put(attrName, val);
            if ("class".equals(attrName)) {
                target.classes.clear();
                target.classes.addAll(Arrays.asList(val.split("\\s+")));
            }
        }
    }

    public static void toggleAttribute(HtmlNode target, String attrName) {
        if (target == null) return;
        if (target.attrs.containsKey(attrName)) {
            target.attrs.remove(attrName);
        } else {
            target.attrs.put(attrName, "true");
        }
    }

    public static void setTextNodeValue(HtmlNode node, String text) {
        if (node == null) return;
        for (HtmlNode child : node.children) {
            if ("#text".equals(child.tag)) {
                child.text = text;
                return;
            }
        }
        HtmlNode txtNode = new HtmlNode("#text");
        txtNode.text = text;
        txtNode.parent = node;
        node.children.add(txtNode);
    }

    // --- テンプレート（プレースホルダー置換）系 ---

    public static void applyTemplateData(HtmlNode node, Map<String, String> data) {
        if (node == null || data == null || data.isEmpty()) return;

        if (node.text != null && node.text.contains("{{")) {
            node.text = replacePlaceholders(node.text, data);
        }

        for (String key : new ArrayList<>(node.attrs.keySet())) {
            String val = node.attrs.get(key);
            if (val != null && val.contains("{{")) {
                node.attrs.put(key, replacePlaceholders(val, data));
            }
        }

        if (node.attrs.containsKey("id")) {
            node.id = node.attrs.get("id");
        }

        for (HtmlNode child : node.children) {
            applyTemplateData(child, data);
        }
    }

    private static String replacePlaceholders(String text, Map<String, String> data) {
        Matcher m = Pattern.compile("\\{\\{([a-zA-Z0-9_.-]+)\\}\\}").matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement = data.getOrDefault(key, "");
            // 置換対象がない場合は元のテキストを保持
            if (!data.containsKey(key)) replacement = "{{" + key + "}}";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}