// 上書き: ClientDOMAPI.java
package com.nishiyu.lunex.program.client.api;

import com.nishiyu.lunex.client.ClientScreenManager;
import com.nishiyu.lunex.program.client.ClientScriptManager;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.webrender.HtmlNode;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.Iterator;
import java.util.Map;

public class ClientDOMAPI {
    private final String sessionId;

    public ClientDOMAPI(String sessionId) {
        this.sessionId = sessionId;
    }

    @LuaFunction(
            value = "指定したセレクタに一致する最初のDOM要素を取得します。",
            en = "Gets the first DOM element matching the specified selector.",
            args = {"str:selector"},
            rets = {"table:element"}
    )
    public LuaTable querySelector(String selector) {
        HtmlNode vRoot = ClientScreenManager.getVirtualRoot(sessionId);
        if (vRoot == null) return null;
        HtmlNode found = vRoot.querySelector(selector);
        if (found != null && !found.id.isEmpty()) {
            return createLuaElement(found.id);
        }
        return null;
    }

    @LuaFunction(
            value = "指定したセレクタに一致するすべてのDOM要素を取得します。",
            en = "Gets all DOM elements matching the specified selector.",
            args = {"str:selector"},
            rets = {"table:elements"}
    )
    public LuaTable querySelectorAll(String selector) {
        HtmlNode vRoot = ClientScreenManager.getVirtualRoot(sessionId);
        if (vRoot == null) return new LuaTable();

        LuaTable results = new LuaTable();
        int index = 1;
        for (HtmlNode node : vRoot.querySelectorAll(selector)) {
            if (!node.id.isEmpty()) {
                results.set(index++, createLuaElement(node.id));
            }
        }
        return results;
    }

    @LuaFunction(
            value = "指定したIDのDOM要素を取得します。",
            en = "Gets the DOM element with the specified ID.",
            args = {"str:id"},
            rets = {"table:element"}
    )
    public LuaTable getElementById(String id) {
        return createLuaElement(id);
    }

    private LuaTable createLuaElement(String id) {
        HtmlNode targetNode = findNodeLocally(id);
        if (targetNode == null) {
            throw new org.luaj.vm2.LuaError("ClientDOM Error: Element not found in session " + sessionId);
        }

        LuaTable element = new LuaTable();
        element.set("id", LuaValue.valueOf(id));

        element.set("getDataset", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                HtmlNode node = findNodeLocally(id);
                if (node == null) return LuaValue.NIL;
                LuaTable dataset = new LuaTable();
                for (Map.Entry<String, String> entry : node.getDataset().entrySet()) {
                    String camelKey = toCamelCase(entry.getKey().substring(5));
                    dataset.set(camelKey, LuaValue.valueOf(entry.getValue()));
                }
                return dataset;
            }
        });

        element.set("querySelector", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue selector) {
                HtmlNode node = findNodeLocally(id);
                if (node == null) return LuaValue.NIL;
                HtmlNode found = node.querySelector(selector.tojstring());
                return (found != null && !found.id.isEmpty()) ? createLuaElement(found.id) : LuaValue.NIL;
            }
        });

        element.set("querySelectorAll", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue selector) {
                HtmlNode node = findNodeLocally(id);
                if (node == null) return new LuaTable();
                LuaTable results = new LuaTable();
                int index = 1;
                for (HtmlNode found : node.querySelectorAll(selector.tojstring())) {
                    if (!found.id.isEmpty()) results.set(index++, createLuaElement(found.id));
                }
                return results;
            }
        });

        element.set("setText", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue text) {
                String newText = text.tojstring();
                HtmlNode node = findNodeLocally(id);
                if (node != null) {
                    boolean changed = false;
                    HtmlNode mainTextNode = null;
                    Iterator<HtmlNode> iterator = node.children.iterator();
                    while (iterator.hasNext()) {
                        HtmlNode child = iterator.next();
                        if ("#text".equals(child.tag)) {
                            if (mainTextNode == null) mainTextNode = child;
                            else { iterator.remove(); changed = true; }
                        }
                    }
                    if (mainTextNode != null) {
                        if (!mainTextNode.text.equals(newText)) {
                            mainTextNode.text = newText;
                            changed = true;
                        }
                    } else {
                        HtmlNode txtNode = new HtmlNode("#text");
                        txtNode.text = newText;
                        txtNode.parent = node;
                        node.children.add(txtNode);
                        changed = true;
                    }
                    if (changed) triggerRecompute();
                }
                return element;
            }
        });

        element.set("setAttribute", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue attr, LuaValue value) {
                ClientScreenManager.updateNodeAttributeLocally(sessionId, id, attr.tojstring(), value.tojstring(), false);
                return element;
            }
        });

        element.set("removeAttribute", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue attr) {
                ClientScreenManager.updateNodeAttributeLocally(sessionId, id, attr.tojstring(), null, true);
                return element;
            }
        });

        element.set("addEventListener", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue ev, LuaValue action) {
                String eventName = ev.tojstring();
                String tempAttrName = eventName.toLowerCase().trim();
                if (!tempAttrName.startsWith("on")) tempAttrName = "on" + tempAttrName;

                String actionName;
                if (action.isfunction()) {
                    actionName = "__dom_cb_" + id.replace("-", "_") + "_" + eventName + "_" + System.currentTimeMillis();
                    LuaTable sessionEnv = ClientScriptManager.getEnv(sessionId);
                    if (sessionEnv != null) sessionEnv.set(actionName, action);
                } else {
                    actionName = action.tojstring();
                }
                ClientScreenManager.updateNodeAttributeLocally(sessionId, id, tempAttrName, actionName, false);
                return element;
            }
        });

        element.set("getAttribute", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue attr) {
                HtmlNode node = findNodeLocally(id);
                if (node != null && node.attrs.containsKey(attr.tojstring())) {
                    return LuaValue.valueOf(node.attrs.get(attr.tojstring()));
                }
                return LuaValue.NIL;
            }
        });

        element.set("getText", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                HtmlNode node = findNodeLocally(id);
                if (node != null) {
                    for (HtmlNode child : node.children) {
                        if ("#text".equals(child.tag)) return LuaValue.valueOf(child.text);
                    }
                }
                return LuaValue.valueOf("");
            }
        });

        element.set("appendElement", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue tag, LuaValue newId) {
                HtmlNode parent = findNodeLocally(id);
                if (parent != null) {
                    HtmlNode newNode = new HtmlNode(tag.tojstring());
                    if (!newId.isnil()) {
                        String nid = newId.tojstring();
                        newNode.id = nid;
                        newNode.attrs.put("id", nid);
                    } else {
                        newNode.id = "html_gen_" + System.currentTimeMillis();
                    }
                    newNode.parent = parent;
                    parent.children.add(newNode);
                    triggerRecompute();
                    return createLuaElement(newNode.id);
                }
                return LuaValue.NIL;
            }
        });

        element.set("remove", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                HtmlNode node = findNodeLocally(id);
                if (node != null && node.parent != null) {
                    node.parent.children.remove(node);
                    triggerRecompute();
                    return LuaValue.TRUE;
                }
                return LuaValue.FALSE;
            }
        });

        element.set("clear", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                HtmlNode node = findNodeLocally(id);
                if (node != null) {
                    node.children.clear();
                    triggerRecompute();
                    return LuaValue.TRUE;
                }
                return LuaValue.FALSE;
            }
        });

        return element;
    }

    private HtmlNode findNodeLocally(String id) {
        HtmlNode vRoot = ClientScreenManager.getVirtualRoot(sessionId);
        return vRoot != null ? vRoot.getElementById(id) : null;
    }

    private void triggerRecompute() {
        ClientScreenManager.requestRender(sessionId);
    }

    private String toCamelCase(String s) {
        String[] parts = s.split("-");
        StringBuilder camelCaseString = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            camelCaseString.append(parts[i].substring(0, 1).toUpperCase()).append(parts[i].substring(1).toLowerCase());
        }
        return camelCaseString.toString();
    }
}