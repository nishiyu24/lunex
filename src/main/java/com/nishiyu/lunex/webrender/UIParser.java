// 上書き: UIParser.java
package com.nishiyu.lunex.webrender;

import com.nishiyu.lunex.blockentity.ScreenBlockEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UIParser {

    public UIParser() {}

    public CssParser.StyleSheet buildStyleSheet(String authorCss, int rootW) {
        String defaultCss = """
                    body, html { background-color: #ffffff; color: #333333; margin: 0; padding: 0; width: 100%; min-height: 100%; box-sizing: border-box; overflow: auto; }
                    head, meta, title, link, style, script, template { display: none; }
                    h1 { font-size: 2rem; font-weight: bold; margin: 0.67em 0; display: block; }
                    h2 { font-size: 1.5rem; font-weight: bold; margin: 0.83em 0; display: block; }
                    h3 { font-size: 1.17rem; font-weight: bold; margin: 1em 0; display: block; }
                    p { margin: 1em 0; display: block; }
                    ul { padding-left: 40px; margin: 1em 0; display: block; }
                    li { display: block; }
                    a { color: #0000ee; text-decoration: none; }
                    b, strong { font-weight: bold; }
                    img { display: inline-block; object-fit: fill; }
                    div, section, main, header, footer, article, nav { display: block; }
                    hr { display: block; border: none; border-top: 1px solid #777777; margin: 10px 0; width: 100%; height: 0; }
                    button { display: inline-block; cursor: pointer; background-color: #f0f0f0; color: #000000; border: 1px solid #767676; padding: 2px 6px; border-radius: 2px; transition: 0.1s; }
                    button:hover { background-color: #e5e5e5; }
                    button:active { background-color: #f5f5f5; border-color: #4f4f4f; transform: scale(0.95); }
                    input { display: inline-block; background-color: #ffffff; color: #000000; border: 1px solid #777777; padding: 4px; box-sizing: border-box; border-radius: 2px; }
                    input[type="checkbox"], input[type="radio"] { width: 16px; height: 16px; padding: 0; border: 2px solid #555555; transition: 0.1s; }
                    input[type="checkbox"]:hover { border-color: #aaaaaa; }
                    input[type="range"] { background-color: #ffffff; color: #007bff; border: none; padding: 0; height: 16px; border-radius: 8px; }
                """;

        CssParser.StyleSheet sheet = CssParser.parse(defaultCss, rootW);

        if (authorCss != null && !authorCss.isEmpty()) {
            CssParser.StyleSheet authorSheet = CssParser.parse(authorCss, rootW);
            for (CssParser.StyleRule rule : authorSheet.rules) {
                rule.specificity += 10000;
            }
            sheet.rules.addAll(authorSheet.rules);
            sheet.keyframes.putAll(authorSheet.keyframes);
            sheet.variables.putAll(authorSheet.variables);
        }

        sheet.rules.sort((a, b) -> Integer.compare(a.specificity, b.specificity));
        return sheet;
    }

    public Document parseDocument(String html, String externalCss, int rootW, int rootH) {
        if (html != null && html.length() > 65536) html = html.substring(0, 65536);
        if (externalCss != null && externalCss.length() > 32768) externalCss = externalCss.substring(0, 32768);

        StringBuilder combinedCss = new StringBuilder();
        if (externalCss != null) combinedCss.append(externalCss).append("\n");

        StringBuilder extractedScript = new StringBuilder();

        if (html != null) {
            Matcher scriptM = Pattern.compile("(?is)<script\\b[^>]*>\\s*(.*?)\\s*</script>").matcher(html);
            while (scriptM.find()) {
                extractedScript.append(scriptM.group(1)).append("\n");
            }
            html = scriptM.replaceAll("");

            Matcher styleM = Pattern.compile("(?is)<style\\b[^>]*>\\s*(.*?)\\s*</style>").matcher(html);
            while (styleM.find()) combinedCss.append(styleM.group(1)).append("\n");
            html = styleM.replaceAll("");
        }

        String authorCss = combinedCss.toString();
        CssParser.StyleSheet sheet = buildStyleSheet(authorCss, rootW);
        HtmlNode htmlRoot = HtmlParser.parse(html == null ? "" : html);

        return new Document(htmlRoot, sheet, authorCss, extractedScript.toString());
    }

    public List<ScreenBlockEntity.UIElement> renderDocument(Document doc, int rootW, int rootH, Map<String, String> env) {
        Map<String, String> rootStyle = new HashMap<>();
        rootStyle.put("width", String.valueOf(rootW));
        rootStyle.put("min-height", String.valueOf(rootH));
        rootStyle.put("color", "#f5f5f0");
        rootStyle.put("box-sizing", "border-box");

        LayoutBox rootBox = buildLayoutTree(doc.root, rootStyle, doc.sheet, env);

        rootBox.computeSize(rootW, rootH, false, false, "stretch", false, rootW, rootH);
        rootBox.computeSize(rootW, rootH, false, false, "stretch", false, rootW, rootH);

        rootBox.layout(0, 0, rootW, rootH, rootW, rootH);

        UIRenderer renderer = new UIRenderer(rootW, rootH, doc.sheet.keyframes);
        return renderer.render(rootBox);
    }

    private LayoutBox buildLayoutTree(HtmlNode node, Map<String, String> inheritedStyle, CssParser.StyleSheet sheet, Map<String, String> env) {
        Map<String, String> style = CssParser.computeNodeStyle(node, inheritedStyle, sheet, env);
        LayoutBox box = new LayoutBox(node, style, env);

        // ::before 擬似要素の処理
        HtmlNode beforeNode = createPseudoElement(node, sheet, true);
        if (beforeNode != null) {
            box.children.add(buildLayoutTree(beforeNode, style, sheet, env));
        }

        for (HtmlNode child : node.children) {
            if (child.tag.equals("#text") && child.text.trim().isEmpty()) continue;
            if (child.tag.equals("template")) continue; // templateはレイアウトから除外
            box.children.add(buildLayoutTree(child, style, sheet, env));
        }

        // ::after 擬似要素の処理
        HtmlNode afterNode = createPseudoElement(node, sheet, false);
        if (afterNode != null) {
            box.children.add(buildLayoutTree(afterNode, style, sheet, env));
        }
        return box;
    }

    private HtmlNode createPseudoElement(HtmlNode parent, CssParser.StyleSheet sheet, boolean isBefore) {
        Map<String, String> pseudoStyle = new HashMap<>();
        boolean matched = false;

        for (CssParser.StyleRule rule : sheet.rules) {
            if ((isBefore && rule.isBefore) || (!isBefore && rule.isAfter)) {
                if (rule.matches(parent)) {
                    pseudoStyle.putAll(rule.properties);
                    matched = true;
                }
            }
        }
        if (!matched || !pseudoStyle.containsKey("content")) return null;

        String content = pseudoStyle.get("content");
        if (content.equals("none") || content.equals("normal") || content.equals("\"\"") || content.equals("''")) return null;

        HtmlNode pseudo = new HtmlNode("span");
        pseudo.isPseudoNode = true;
        pseudo.parent = parent;
        // 疑似要素自体には固有のIDを振っておく（イベント等の混線防止）
        pseudo.id = parent.id + (isBefore ? "_before" : "_after");

        // ★ 修正: テキストを描画エンジンに認識させるための #text ノードを子として追加する
        HtmlNode textNode = new HtmlNode("#text");
        textNode.parent = pseudo;
        textNode.id = pseudo.id + "_txt";

        if (content.startsWith("attr(") && content.endsWith(")")) {
            String attrName = content.substring(5, content.length() - 1).trim();
            textNode.text = parent.attrs.getOrDefault(attrName, "");
        } else {
            textNode.text = content.replaceAll("^[\"']|[\"']$", "");
        }

        // テキストが空なら擬似要素自体を生成しない
        if (textNode.text.isEmpty()) return null;

        pseudo.children.add(textNode);

        StringBuilder styleStr = new StringBuilder();
        for (Map.Entry<String, String> entry : pseudoStyle.entrySet()) {
            styleStr.append(entry.getKey()).append(":").append(entry.getValue()).append(";");
        }
        pseudo.attrs.put("style", styleStr.toString());

        return pseudo;
    }

    public static class Document {
        public HtmlNode root;
        public CssParser.StyleSheet sheet;
        public String authorCss;
        public String clientScript;

        public Document(HtmlNode root, CssParser.StyleSheet sheet, String authorCss, String clientScript) {
            this.root = root;
            this.sheet = sheet;
            this.authorCss = authorCss;
            this.clientScript = clientScript;
        }
    }
}