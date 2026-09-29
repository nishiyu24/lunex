package com.nishiyu.lunex.webrender;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * 仮想DOMツリー(virtualNode)の変更を、実DOMツリー(realNode)に差分適用するエンジン
 */
public class DomDiffEngine {

    /**
     * @return 実DOMに変更（パッチ）が適用された場合は true
     */
    public static boolean diffAndPatch(HtmlNode realNode, HtmlNode virtualNode) {
        if (realNode == null || virtualNode == null) return false;
        boolean changed = false;

        // 1. テキストと内部フラグのDiff
        if (!Objects.equals(realNode.text, virtualNode.text)) {
            realNode.text = virtualNode.text != null ? virtualNode.text : "";
            changed = true;
        }
        if (realNode.isPseudoNode != virtualNode.isPseudoNode) {
            realNode.isPseudoNode = virtualNode.isPseudoNode;
            changed = true;
        }

        // 2. 属性(Attributes)のDiff
        Iterator<String> it = realNode.attrs.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (!virtualNode.attrs.containsKey(key)) {
                it.remove();
                if (key.equals("class")) realNode.classes.clear();
                changed = true;
            }
        }
        for (Map.Entry<String, String> entry : virtualNode.attrs.entrySet()) {
            String key = entry.getKey();
            String vVal = entry.getValue();
            String rVal = realNode.attrs.get(key);
            if (!Objects.equals(rVal, vVal)) {
                realNode.attrs.put(key, vVal);
                if (key.equals("id")) realNode.id = vVal;
                if (key.equals("class")) {
                    realNode.classes.clear();
                    realNode.classes.addAll(Arrays.asList(vVal.split("\\s+")));
                }
                changed = true;
            }
        }

        // 3. 子要素(Children)のDiff
        // template タグは構造として維持するが、中身の差分適用は最小限にとどめる
        int minSize = Math.min(realNode.children.size(), virtualNode.children.size());

        for (int i = 0; i < minSize; i++) {
            HtmlNode rChild = realNode.children.get(i);
            HtmlNode vChild = virtualNode.children.get(i);

            if (!rChild.tag.equals(vChild.tag)) {
                HtmlNode newRealChild = vChild.cloneNode();
                newRealChild.parent = realNode;
                realNode.children.set(i, newRealChild);
                changed = true;
            } else {
                if (diffAndPatch(rChild, vChild)) {
                    changed = true;
                }
            }
        }

        // 超過分の削除
        if (realNode.children.size() > virtualNode.children.size()) {
            while (realNode.children.size() > virtualNode.children.size()) {
                realNode.children.remove(realNode.children.size() - 1);
                changed = true;
            }
        }
        // 不足分の追加
        else if (realNode.children.size() < virtualNode.children.size()) {
            for (int i = minSize; i < virtualNode.children.size(); i++) {
                HtmlNode newRealChild = virtualNode.children.get(i).cloneNode();
                newRealChild.parent = realNode;
                realNode.children.add(newRealChild);
                changed = true;
            }
        }

        return changed;
    }
}