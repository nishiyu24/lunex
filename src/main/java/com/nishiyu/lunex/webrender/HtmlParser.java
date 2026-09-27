package com.nishiyu.lunex.webrender;

import java.util.Arrays;
import java.util.Stack;

public class HtmlParser {

    private static final int MAX_NODES = 2000;
    private static final int MAX_DEPTH = 50;

    // ★ 追加: パース時にすべての要素へ固定のIDを割り振るためのカウンター
    private static int globalAutoId = 0;

    public static HtmlNode parse(String html) {
        HtmlNode root = new HtmlNode("root");
        root.id = "html_root"; // ルートの固定ID
        Stack<HtmlNode> stack = new Stack<>();
        stack.push(root);

        int nodeCount = 0;

        for (HtmlTokenizer.Token token : HtmlTokenizer.tokenize(html)) {
            if (++nodeCount > MAX_NODES) break;

            if (token.type == HtmlTokenizer.Token.Type.START_TAG) {
                if (stack.size() > MAX_DEPTH) continue;

                HtmlNode node = new HtmlNode(token.name);
                node.parent = stack.peek();

                for (var entry : token.attrs.entrySet()) {
                    String key = entry.getKey();
                    String val = entry.getValue();
                    if (val != null && val.length() > 1024) val = val.substring(0, 1024);

                    node.attrs.put(key, val);
                    if (key.equals("id")) node.id = val;
                    else if (key.equals("class")) node.classes.addAll(Arrays.asList(val.split("\\s+")));
                }

                // ★ 追加: IDがない要素に一意のIDをパース段階で確定させる
                if (node.id.isEmpty()) {
                    node.id = "html_gen_" + (++globalAutoId);
                }

                stack.peek().children.add(node);

                boolean isSelfClosing = token.isSelfClosing ||
                        Arrays.asList("br", "hr", "img", "input", "meta", "link").contains(token.name);

                if (!isSelfClosing) {
                    stack.push(node);
                }

            } else if (token.type == HtmlTokenizer.Token.Type.END_TAG) {
                int matchIndex = -1;
                for (int i = stack.size() - 1; i > 0; i--) {
                    if (stack.get(i).tag.equals(token.name)) {
                        matchIndex = i;
                        break;
                    }
                }
                if (matchIndex != -1) {
                    while (stack.size() > matchIndex) stack.pop();
                }

            } else if (token.type == HtmlTokenizer.Token.Type.TEXT) {
                String text = token.text.trim();
                if (text.length() > 5000) text = text.substring(0, 5000);

                if (!text.isEmpty()) {
                    HtmlNode textNode = new HtmlNode("#text");
                    textNode.text = text;
                    textNode.id = "html_txt_" + (++globalAutoId); // テキストにも固定ID
                    textNode.parent = stack.peek();
                    stack.peek().children.add(textNode);
                }
            }
        }
        return root;
    }
}