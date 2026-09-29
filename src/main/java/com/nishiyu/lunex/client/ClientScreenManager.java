package com.nishiyu.lunex.client;

import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.client.renderer.ScreenAnimator;
import com.nishiyu.lunex.webrender.*;
import net.minecraft.nbt.CompoundTag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientScreenManager {
    private static final Map<String, List<ScreenBlockEntity.UIElement>> RENDER_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, UIParser.Document> DOM_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, HtmlNode> VIRTUAL_DOM_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> DIRTY_SESSIONS = ConcurrentHashMap.newKeySet();

    private static final Set<String> REQUESTED_SESSIONS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> SYNC_REQUESTS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> DOM_HASH_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, Integer> LAST_ROOT_W = new ConcurrentHashMap<>();
    private static final Map<String, Integer> LAST_ROOT_H = new ConcurrentHashMap<>();

    private static final Map<String, Map<String, String>> SESSION_ENV = new ConcurrentHashMap<>();

    static {
        TextMetrics.clientWidthFunction = (text, scale) -> {
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            if (font != null) {
                float adjustedScale = scale * (16.0f / 12.0f);
                return (int) Math.ceil(font.width(text) * adjustedScale);
            }
            return 0;
        };
    }

    public static void handleSyncPacket(String sessionId, CompoundTag tag) {
        if (tag.getBoolean("IsEmpty")) {
            clearSession(sessionId);
        } else if (tag.contains("DomTree")) {
            CompoundTag domTag = tag.getCompound("DomTree");
            String cssString = tag.getString("CssString");
            String clientScript = tag.getString("ClientScript");

            int currentHash = Objects.hash(domTag.hashCode(), cssString.hashCode(), clientScript.hashCode());
            if (DOM_HASH_CACHE.getOrDefault(sessionId, 0) == currentHash) {
                REQUESTED_SESSIONS.remove(sessionId);
                return;
            }
            DOM_HASH_CACHE.put(sessionId, currentHash);

            int rootW = tag.contains("RootW") ? tag.getInt("RootW") : 1920;
            int rootH = tag.contains("RootH") ? tag.getInt("RootH") : 1080;

            LAST_ROOT_W.put(sessionId, rootW);
            LAST_ROOT_H.put(sessionId, rootH);

            HtmlNode rootNode = HtmlNodeSerializer.deserialize(domTag, null);

            UIParser parser = new UIParser();
            CssParser.StyleSheet sheet = parser.buildStyleSheet(cssString, rootW);

            UIParser.Document doc = new UIParser.Document(rootNode, sheet, cssString, clientScript);
            DOM_CACHE.put(sessionId, doc);

            HtmlNode virtualRoot = rootNode.cloneNode();
            VIRTUAL_DOM_CACHE.put(sessionId, virtualRoot);

            // ★ 追加: HTMLが構築された瞬間に、data-* 属性（仮想リストやデータバインディング）を走査して自動購読を開始する
            DeclarativeBindingManager.initializeBindings(sessionId, virtualRoot);

            Map<String, String> currentEnv = SESSION_ENV.getOrDefault(sessionId, new HashMap<>());
            List<ScreenBlockEntity.UIElement> elements = parser.renderDocument(doc, rootW, rootH, currentEnv);
            RENDER_CACHE.put(sessionId, elements);

            com.nishiyu.lunex.program.client.ClientScriptManager.loadClientScript(sessionId, clientScript);
        }

        REQUESTED_SESSIONS.remove(sessionId);
        ClientScreenInteractionManager.applyLocalOverrides(sessionId);
    }

    public static HtmlNode getVirtualRoot(String sessionId) {
        return VIRTUAL_DOM_CACHE.get(sessionId);
    }

    public static void requestRender(String sessionId) {
        DIRTY_SESSIONS.add(sessionId);
    }

    private static boolean needsContinuousRedraw(String sessionId, long now) {
        if (ScreenAnimator.isAnimating(sessionId, now)) return true;
        List<ScreenBlockEntity.UIElement> els = getElements(sessionId);
        for (ScreenBlockEntity.UIElement el : els) {
            if ("video".equals(el.type())) return true;
        }
        return false;
    }

    public static void processRenderQueue() {
        long now = System.currentTimeMillis();

        if (DIRTY_SESSIONS.isEmpty()) return;

        for (String sessionId : DIRTY_SESSIONS) {
            UIParser.Document doc = DOM_CACHE.get(sessionId);
            HtmlNode virtualRoot = VIRTUAL_DOM_CACHE.get(sessionId);

            if (doc != null && doc.root != null && virtualRoot != null) {
                boolean changed = DomDiffEngine.diffAndPatch(doc.root, virtualRoot);
                if (changed) {
                    recomputeLayout(sessionId, LAST_ROOT_W.getOrDefault(sessionId, 1920), LAST_ROOT_H.getOrDefault(sessionId, 1080));
                }
            }
        }
        DIRTY_SESSIONS.clear();
    }

    public static void updateEnvAndRecomputeIfChanged(String sessionId, Map<String, String> newEnv) {
        Map<String, String> oldEnv = SESSION_ENV.getOrDefault(sessionId, new HashMap<>());
        boolean changed = false;

        for (Map.Entry<String, String> e : newEnv.entrySet()) {
            if (!e.getValue().equals(oldEnv.get(e.getKey()))) {
                changed = true;
                break;
            }
        }

        if (changed || oldEnv.size() != newEnv.size()) {
            SESSION_ENV.put(sessionId, new HashMap<>(newEnv));
            recomputeLayout(sessionId, LAST_ROOT_W.getOrDefault(sessionId, 1920), LAST_ROOT_H.getOrDefault(sessionId, 1080));
        }
    }

    public static void recomputeLayout(String sessionId, int newRootW, int newRootH) {
        LAST_ROOT_W.put(sessionId, newRootW);
        LAST_ROOT_H.put(sessionId, newRootH);

        UIParser.Document doc = DOM_CACHE.get(sessionId);
        if (doc != null) {
            UIParser parser = new UIParser();
            doc.sheet = parser.buildStyleSheet(doc.authorCss, newRootW);
            Map<String, String> currentEnv = SESSION_ENV.getOrDefault(sessionId, new HashMap<>());

            List<com.nishiyu.lunex.blockentity.ScreenBlockEntity.UIElement> elements = parser.renderDocument(doc, newRootW, newRootH, currentEnv);
            RENDER_CACHE.put(sessionId, elements);
        }
    }

    public static List<ScreenBlockEntity.UIElement> getElements(String sessionId) {
        return RENDER_CACHE.getOrDefault(sessionId, new ArrayList<>());
    }

    public static boolean shouldRequestSync(String sessionId) {
        long now = System.currentTimeMillis();
        long lastReq = SYNC_REQUESTS.getOrDefault(sessionId, 0L);
        if (now - lastReq > 2000) {
            SYNC_REQUESTS.put(sessionId, now);
            return true;
        }
        return false;
    }

    public static void markRequested(String sessionId) {
        REQUESTED_SESSIONS.add(sessionId);
    }

    public static String getBaseNodeId(String renderId) {
        if (renderId == null) return "";
        if (renderId.contains("_txt_")) return renderId.substring(0, renderId.indexOf("_txt_"));
        if (renderId.contains("_b_")) return renderId.substring(0, renderId.indexOf("_b_"));
        if (renderId.contains("_frag_")) return renderId.substring(0, renderId.indexOf("_frag_"));
        if (renderId.contains("_shadow")) return renderId.substring(0, renderId.indexOf("_shadow"));
        if (renderId.contains("_cb")) return renderId.substring(0, renderId.indexOf("_cb"));
        if (renderId.contains("_bbg")) return renderId.substring(0, renderId.indexOf("_bbg"));
        if (renderId.contains("_dot")) return renderId.substring(0, renderId.indexOf("_dot"));
        if (renderId.contains("_bg")) return renderId.substring(0, renderId.indexOf("_bg"));
        if (renderId.contains("_ph")) return renderId.substring(0, renderId.indexOf("_ph"));
        if (renderId.contains("_img")) return renderId.substring(0, renderId.indexOf("_img"));
        if (renderId.contains("_input")) return renderId.substring(0, renderId.indexOf("_input"));
        return renderId;
    }

    public static ScreenBlockEntity.UIElement getElementById(String sessionId, String id) {
        if (id == null) return null;
        for (ScreenBlockEntity.UIElement el : getElements(sessionId)) {
            if (id.equals(getBaseNodeId(el.id()))) return el;
        }
        return null;
    }

    public static void updateNodeAttributeLocally(String sessionId, String elementId, String attrName, String attrValue, boolean remove) {
        HtmlNode target = findNodeById(getVirtualRoot(sessionId), getBaseNodeId(elementId));
        if (target != null) {
            if (remove) {
                target.attrs.remove(attrName);
                if (attrName.equals("class")) target.classes.clear();
            } else {
                String val = attrValue != null ? attrValue : "true";
                target.attrs.put(attrName, val);
                if (attrName.equals("class")) {
                    target.classes.clear();
                    target.classes.addAll(java.util.Arrays.asList(val.split("\\s+")));
                }
            }
            requestRender(sessionId);
        }
    }

    public static void toggleNodeAttributeLocally(String sessionId, String elementId, String attrName) {
        HtmlNode target = findNodeById(getVirtualRoot(sessionId), getBaseNodeId(elementId));
        if (target != null) {
            if (target.attrs.containsKey(attrName)) target.attrs.remove(attrName);
            else target.attrs.put(attrName, "true");
            requestRender(sessionId);
        }
    }

    private static HtmlNode findNodeById(HtmlNode node, String id) {
        if (node == null || id == null) return null;
        if (id.equals(node.id)) return node;
        for (HtmlNode child : node.children) {
            HtmlNode found = findNodeById(child, id);
            if (found != null) return found;
        }
        return null;
    }

    public static boolean handleScrollLocally(String sessionId, double pixelX, double pixelY, double scrollX, double scrollY) {
        List<ScreenBlockEntity.UIElement> elements = getElements(sessionId);
        for (int i = elements.size() - 1; i >= 0; i--) {
            ScreenBlockEntity.UIElement el = elements.get(i);
            if (el.events() != null && el.events().containsKey("mc-scrollable")) {
                ScreenAnimator.AnimatedValues anim = ScreenAnimator.getCurrentValues(sessionId, el);
                float cx = el.x() + el.width() / 2.0f;
                float cy = el.y() + el.height() / 2.0f;
                double dx = pixelX - cx - anim.tx();
                double dy = pixelY - cy - anim.ty();

                double nx, ny;
                if (anim.rot() == 0.0f) {
                    nx = dx;
                    ny = dy;
                } else {
                    double rad = -Math.toRadians(anim.rot());
                    double cos = Math.cos(rad);
                    double sin = Math.sin(rad);
                    nx = dx * cos - dy * sin;
                    ny = dx * sin + dy * cos;
                }

                if (anim.sx() != 0 && anim.sx() != 1.0f) nx /= anim.sx();
                if (anim.sy() != 0 && anim.sy() != 1.0f) ny /= anim.sy();

                nx += cx;
                ny += cy;
                double localX = nx - el.x();
                double localY = ny - el.y();

                if (localX >= 0 && localX <= el.width() && localY >= 0 && localY <= el.height()) {
                    String[] maxStrs = el.events().get("mc-scrollable").split(",");
                    float maxW = Float.parseFloat(maxStrs[0]);
                    float maxH = Float.parseFloat(maxStrs[1]);

                    float curX = 0, curY = 0;
                    if (el.events().containsKey("mc-scroll-current")) {
                        String[] curStrs = el.events().get("mc-scroll-current").split(",");
                        curX = Float.parseFloat(curStrs[0]);
                        curY = Float.parseFloat(curStrs[1]);
                    }

                    float limitX = Math.max(0, maxW - el.width());
                    float limitY = Math.max(0, maxH - el.height());

                    if (limitX > 0 || limitY > 0) {
                        float nextX = net.minecraft.util.Mth.clamp(curX - (float) scrollX * 30f, 0, limitX);
                        float nextY = net.minecraft.util.Mth.clamp(curY - (float) scrollY * 30f, 0, limitY);

                        boolean changed = false;
                        if (Math.abs(nextX - curX) > 0.1f) {
                            updateNodeAttributeLocally(sessionId, el.id(), "data-scroll-x", String.valueOf(nextX), false);
                            changed = true;
                        }
                        if (Math.abs(nextY - curY) > 0.1f) {
                            updateNodeAttributeLocally(sessionId, el.id(), "data-scroll-y", String.valueOf(nextY), false);
                            changed = true;
                        }
                        if (changed) return true;
                    }
                }
            }
        }
        return false;
    }

    private static double[] inverseTransform(double px, double py, ScreenBlockEntity.UIElement el, ScreenAnimator.AnimatedValues anim) {
        float originX = 0.5f, originY = 0.5f;
        if (el.events() != null && el.events().containsKey("mc-origin")) {
            String[] parts = el.events().get("mc-origin").split(",");
            if (parts.length >= 2) {
                try {
                    originX = Float.parseFloat(parts[0]);
                    originY = Float.parseFloat(parts[1]);
                } catch (Exception ignored) {
                }
            }
        }
        float cx = el.x() + el.width() * originX;
        float cy = el.y() + el.height() * originY;

        double dx = px - cx - anim.tx();
        double dy = py - cy - anim.ty();

        if (anim.rot() != 0.0f) {
            double rad = -Math.toRadians(anim.rot());
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double nx = dx * cos - dy * sin;
            double ny = dx * sin + dy * cos;
            dx = nx;
            dy = ny;
        }

        if (anim.sx() != 0 && anim.sx() != 1.0f) dx /= anim.sx();
        if (anim.sy() != 0 && anim.sy() != 1.0f) dy /= anim.sy();

        return new double[]{dx + cx, dy + cy};
    }

    private static double[] getLocalPointRecursive(String sessionId, ScreenBlockEntity.UIElement el, double px, double py, Map<String, ScreenBlockEntity.UIElement> elementMap, String hoveredId) {
        if (el.events() != null && el.events().containsKey("mc-parent")) {
            ScreenBlockEntity.UIElement parent = elementMap.get(el.events().get("mc-parent"));
            if (parent != null) {
                double[] p = getLocalPointRecursive(sessionId, parent, px, py, elementMap, hoveredId);
                px = p[0];
                py = p[1];
            }
        }
        boolean isHovered = getBaseNodeId(el.id()).equals(hoveredId);
        ScreenAnimator.AnimatedValues anim = ScreenAnimator.getCurrentValues(sessionId, el);
        return inverseTransform(px, py, el, anim);
    }

    public static UIHitResult getHitElement(String sessionId, double pixelX, double pixelY) {
        List<ScreenBlockEntity.UIElement> elements = getElements(sessionId);
        Map<String, ScreenBlockEntity.UIElement> elementMap = new HashMap<>();
        for (ScreenBlockEntity.UIElement el : elements) elementMap.put(el.id(), el);

        for (int i = elements.size() - 1; i >= 0; i--) {
            ScreenBlockEntity.UIElement el = elements.get(i);
            if ("node".equals(el.type()) || "text".equals(el.type()) || el.id().endsWith("_shadow")) {
                continue;
            }

            boolean isInput = el.type().equals("input") || el.type().equals("range") || el.type().equals("toggle") || el.type().equals("checkbox") || el.type().equals("button") || el.type().equals("a");
            boolean hasInteraction = false;
            if (el.events() != null) {
                for (String key : el.events().keySet()) {
                    if (key.startsWith("on")) {
                        hasInteraction = true;
                        break;
                    }
                }
            }

            ScreenBlockEntity.UIElement parentAnchor = elementMap.get(el.events() != null ? el.events().get("mc-parent") : null);
            boolean hasHoverAnim = parentAnchor != null && parentAnchor.animDef() != null && parentAnchor.animDef().contains("@@HOVER@@");

            if (!isInput && !hasInteraction && !hasHoverAnim) {
                continue;
            }

            double[] localPt = getLocalPointRecursive(sessionId, el, pixelX, pixelY, elementMap, "");
            double localX = localPt[0] - el.x();
            double localY = localPt[1] - el.y();

            if (localX >= 0 && localX <= el.width() && localY >= 0 && localY <= el.height()) {
                return new UIHitResult(el, localX, localY);
            }
        }
        return null;
    }

    public static UIHitResult getLocalCoords(String sessionId, String elementId, double pixelX, double pixelY) {
        ScreenBlockEntity.UIElement el = getElementById(sessionId, elementId);
        if (el == null) return null;

        List<ScreenBlockEntity.UIElement> elements = getElements(sessionId);
        Map<String, ScreenBlockEntity.UIElement> elementMap = new HashMap<>();
        for (ScreenBlockEntity.UIElement e : elements) elementMap.put(e.id(), e);

        double[] localPt = getLocalPointRecursive(sessionId, el, pixelX, pixelY, elementMap, "");
        return new UIHitResult(el, localPt[0] - el.x(), localPt[1] - el.y());
    }

    public static void updateElementTextLocally(String sessionId, String elementId, String newText) {
        HtmlNode target = findNodeById(getVirtualRoot(sessionId), getBaseNodeId(elementId));
        if (target != null) {
            if (java.util.Objects.equals(target.attrs.get("value"), newText)) return;

            target.attrs.put("value", newText);
            requestRender(sessionId);
        }

        List<ScreenBlockEntity.UIElement> elements = getElements(sessionId);
        for (int i = 0; i < elements.size(); i++) {
            ScreenBlockEntity.UIElement el = elements.get(i);
            if (el.id().equals(elementId)) {
                elements.set(i, new ScreenBlockEntity.UIElement(
                        el.id(), el.type(), el.x(), el.y(), el.width(), el.height(),
                        newText, el.color(), el.bgColor(), el.events(), el.radius(),
                        el.opacity(), el.translateX(), el.translateY(),
                        el.scaleX(), el.scaleY(), el.rotate(), el.transitionDuration(), el.animDef()
                ));
            }
        }
    }

    public static void clearSession(String sessionId) {
        RENDER_CACHE.put(sessionId, new ArrayList<>());
        DOM_CACHE.remove(sessionId);
        VIRTUAL_DOM_CACHE.remove(sessionId);
        DIRTY_SESSIONS.remove(sessionId);
        SESSION_ENV.remove(sessionId);
        DOM_HASH_CACHE.remove(sessionId);
        // ★ 追加: セッション終了時にPubSubバインディングも確実にクリーンアップ
        ClientPubSubManager.clearSession(sessionId);
    }

    public static UIParser.Document getDocument(String sessionId) {
        return DOM_CACHE.get(sessionId);
    }

    public static boolean hasDocument(String sessionId) {
        return DOM_CACHE.containsKey(sessionId);
    }

    public static Map<String, String> getEnv(String sessionId) {
        return SESSION_ENV.getOrDefault(sessionId, new HashMap<>());
    }

    public static int getLastRootW(String sessionId) {
        return LAST_ROOT_W.getOrDefault(sessionId, 1920);
    }

    public static int getLastRootH(String sessionId) {
        return LAST_ROOT_H.getOrDefault(sessionId, 1080);
    }

    public record UIHitResult(ScreenBlockEntity.UIElement element, double localX, double localY) {
    }
}