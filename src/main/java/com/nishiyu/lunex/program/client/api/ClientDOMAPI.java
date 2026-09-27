// 上書き: ClientDOMAPI.java
package com.nishiyu.lunex.program.client.api;

import com.nishiyu.lunex.client.ClientPubSubManager;
import com.nishiyu.lunex.client.ClientScreenManager;
import com.nishiyu.lunex.client.renderer.ClientMediaManager;
import com.nishiyu.lunex.program.client.ClientScriptManager;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.webrender.HtmlNode;
import com.nishiyu.lunex.webrender.HtmlParser;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ClientDOMAPI {
    private final String sessionId;

    public ClientDOMAPI(String sessionId) {
        this.sessionId = sessionId;
    }

    @LuaFunction(
            value = "指定したIDのDOM要素を取得し、操作用オブジェクト(コンポーネント)を返します。",
            en = "Gets the DOM element with the specified ID and returns an operation object (component).",
            args = {"str:id"},
            rets = {"table:element"}
    )
    public LuaTable getElementById(String id) {
        HtmlNode targetNode = findNodeLocally(id);
        if (targetNode == null) {
            throw new org.luaj.vm2.LuaError("ClientDOM Error: Element with ID '" + id + "' not found in session " + sessionId);
        }

        LuaTable element = new LuaTable();
        element.set("id", LuaValue.valueOf(id));

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
                            if (mainTextNode == null) {
                                mainTextNode = child;
                            } else {
                                iterator.remove();
                                changed = true;
                            }
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
                String attrName = attr.tojstring();
                String attrValue = value.tojstring();
                HtmlNode node = findNodeLocally(id);
                if (node != null && attrValue.equals(node.attrs.get(attrName))) {
                    return element;
                }
                ClientScreenManager.updateNodeAttributeLocally(sessionId, id, attrName, attrValue, false);
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
                if (!tempAttrName.startsWith("on")) {
                    tempAttrName = "on" + tempAttrName;
                }
                final String finalAttrName = tempAttrName;

                String actionName;
                if (action.isfunction()) {
                    actionName = "__dom_cb_" + id.replace("-", "_") + "_" + eventName + "_" + System.currentTimeMillis();
                    LuaTable sessionEnv = ClientScriptManager.getEnv(sessionId);
                    if (sessionEnv != null) {
                        sessionEnv.set(actionName, action);
                    }
                } else {
                    actionName = action.tojstring();
                }

                ClientScreenManager.updateNodeAttributeLocally(sessionId, id, finalAttrName, actionName, false);
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
                        if ("#text".equals(child.tag)) {
                            return LuaValue.valueOf(child.text);
                        }
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
                    }
                    newNode.parent = parent;
                    parent.children.add(newNode);
                    triggerRecompute();
                    return LuaValue.TRUE;
                }
                return LuaValue.FALSE;
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

        element.set("bindData", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue options) {
                if (options.istable()) {
                    LuaTable opts = options.checktable();
                    String channel = opts.get("channel").tojstring();
                    String path = opts.get("path").tojstring();
                    String targetAttr = opts.get("target").isnil() ? "text" : opts.get("target").tojstring();

                    ClientPubSubManager.registerDataBinding(sessionId, id, channel, path, targetAttr);
                    ClientPubSubManager.requestSubscribe(sessionId, channel);
                }
                return element;
            }
        });

        element.set("bindVirtualList", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue options) {
                if (options.istable()) {
                    LuaTable opts = options.checktable();
                    String channel = opts.get("channel").tojstring();
                    String template = opts.get("template").tojstring();
                    int itemW = opts.get("itemWidth").isnil() ? 36 : opts.get("itemWidth").toint();
                    int itemH = opts.get("itemHeight").isnil() ? 36 : opts.get("itemHeight").toint();

                    ClientPubSubManager.registerVirtualList(sessionId, id, channel, template, itemW, itemH);
                    ClientPubSubManager.requestSubscribe(sessionId, channel);
                }
                return element;
            }
        });

        element.set("enableHud", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue options) {
                if (options.istable()) {
                    LuaTable opts = options.checktable();
                    String origDisplay = opts.get("origDisplay").isnil() ? "flex" : opts.get("origDisplay").tojstring();
                    String template = opts.get("template").isnil() ? "" : opts.get("template").tojstring();

                    List<String> requireNbt = new ArrayList<>();
                    if (opts.get("requireNbt").istable()) {
                        LuaTable nbtList = opts.get("requireNbt").checktable();
                        for (int i = 1; i <= nbtList.length(); i++) {
                            requireNbt.add(nbtList.get(i).tojstring());
                        }
                    }

                    HtmlNode templateRoot = HtmlParser.parse(template);
                    com.nishiyu.lunex.client.renderer.ARGlassesHudRenderer.enableHudTemplate(sessionId, id, origDisplay, templateRoot, requireNbt);
                }
                return element;
            }
        });

        element.set("enableTracker", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue options) {
                if (options.istable()) {
                    LuaTable opts = options.checktable();
                    String template = opts.get("template").isnil() ? "" : opts.get("template").tojstring();

                    com.nishiyu.lunex.client.renderer.ARGlassesHudRenderer.TrackerTemplateConfig config = new com.nishiyu.lunex.client.renderer.ARGlassesHudRenderer.TrackerTemplateConfig();
                    if (!opts.get("radius").isnil()) config.radius = opts.get("radius").todouble();
                    if (!opts.get("yOffset").isnil()) config.yOffset = opts.get("yOffset").todouble();
                    if (!opts.get("type").isnil()) config.targetType = opts.get("type").tojstring();

                    if (opts.get("requireNbt").istable()) {
                        LuaTable nbtList = opts.get("requireNbt").checktable();
                        for (int i = 1; i <= nbtList.length(); i++) {
                            config.requireNbt.add(nbtList.get(i).tojstring());
                        }
                    }

                    HtmlNode templateRoot = HtmlParser.parse(template);
                    com.nishiyu.lunex.client.renderer.ARGlassesHudRenderer.setTrackerTemplate(sessionId, templateRoot, config);
                }
                return element;
            }
        });

        // ==========================================
        // ★ 追加: Video コンポーネント用のAPI群
        // ==========================================
        element.set("setSpeed", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue speed) {
                HtmlNode node = findNodeLocally(id);
                if (node != null && "video".equals(node.tag)) {
                    String url = node.attrs.get("src");
                    if (url != null) {
                        float spd = (float) speed.todouble();
                        ClientMediaManager.VideoTexture vt = ClientMediaManager.getVideoTexture(url);
                        if (vt != null) {
                            if (spd <= 0.0f) {
                                vt.setPaused(true);
                            } else {
                                vt.setPaused(false);
                                vt.setPlaybackSpeed(spd);
                            }
                        }
                    }
                }
                return element;
            }
        });

        element.set("seek", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue seconds) {
                HtmlNode node = findNodeLocally(id);
                if (node != null && "video".equals(node.tag)) {
                    String url = node.attrs.get("src");
                    if (url != null) {
                        ClientMediaManager.VideoTexture vt = ClientMediaManager.getVideoTexture(url);
                        if (vt != null) vt.seekTo(seconds.todouble());
                    }
                }
                return element;
            }
        });

        element.set("play", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                HtmlNode node = findNodeLocally(id);
                if (node != null && "video".equals(node.tag)) {
                    String url = node.attrs.get("src");
                    if (url != null) {
                        ClientMediaManager.VideoTexture vt = ClientMediaManager.getVideoTexture(url);
                        if (vt != null) vt.setPaused(false);
                    }
                }
                return element;
            }
        });

        element.set("pause", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                HtmlNode node = findNodeLocally(id);
                if (node != null && "video".equals(node.tag)) {
                    String url = node.attrs.get("src");
                    if (url != null) {
                        ClientMediaManager.VideoTexture vt = ClientMediaManager.getVideoTexture(url);
                        if (vt != null) vt.setPaused(true);
                    }
                }
                return element;
            }
        });

        return element;
    }

    private HtmlNode findNodeLocally(String id) {
        HtmlNode vRoot = ClientScreenManager.getVirtualRoot(sessionId);
        if (vRoot != null) {
            return vRoot.getElementById(id);
        }
        return null;
    }

    private void triggerRecompute() {
        ClientScreenManager.requestRender(sessionId);
    }
}