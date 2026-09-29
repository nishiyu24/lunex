package com.nishiyu.lunex.client;

import com.nishiyu.lunex.webrender.HtmlNode;
import com.nishiyu.lunex.client.renderer.ARGlassesHudRenderer;

public class DeclarativeBindingManager {

    public static void initializeBindings(String sessionId, HtmlNode rootNode) {
        if (rootNode == null) return;

        // 1. data-bind
        if (rootNode.attrs.containsKey("data-bind")) {
            String bindDefinition = rootNode.attrs.get("data-bind");
            String targetAttr = "text";
            String path = bindDefinition;

            if (bindDefinition.contains(":")) {
                String[] parts = bindDefinition.split(":", 2);
                targetAttr = parts[0].trim();
                path = parts[1].trim();
            }

            String channel = resolveChannel(rootNode, path);

            ClientPubSubManager.registerDataBinding(sessionId, rootNode.id, channel, path, targetAttr);
            ClientPubSubManager.requestSubscribe(sessionId, channel);
        }

        // 2. data-list と <template>
        if (rootNode.attrs.containsKey("data-list")) {
            String listPath = rootNode.attrs.get("data-list");
            HtmlNode templateNode = findTemplateChild(rootNode);

            if (templateNode != null) {
                int itemW = Integer.parseInt(rootNode.attrs.getOrDefault("data-item-width", "36"));
                int itemH = Integer.parseInt(rootNode.attrs.getOrDefault("data-item-height", "36"));

                String channel = resolveChannel(rootNode, listPath);

                String templateHtml = serializeToSimpleHtml(templateNode);

                ClientPubSubManager.registerVirtualList(sessionId, rootNode.id, channel, templateHtml, itemW, itemH);
                ClientPubSubManager.requestSubscribe(sessionId, channel);
            }
        }

        // 3. data-hud
        if (rootNode.attrs.containsKey("data-hud")) {
            String origDisplay = rootNode.attrs.getOrDefault("data-hud-display", "flex");
            java.util.List<String> requireNbt = new java.util.ArrayList<>();

            if (rootNode.attrs.containsKey("data-hud-require-nbt")) {
                String[] nbtKeys = rootNode.attrs.get("data-hud-require-nbt").split(",");
                for (String key : nbtKeys) requireNbt.add(key.trim());
            }

            ARGlassesHudRenderer.enableHudTemplate(sessionId, rootNode.id, origDisplay, rootNode, requireNbt);
        }

        // 4. data-tracker
        if (rootNode.attrs.containsKey("data-tracker")) {
            ARGlassesHudRenderer.TrackerTemplateConfig config = new ARGlassesHudRenderer.TrackerTemplateConfig();

            if (rootNode.attrs.containsKey("data-tracker-radius"))
                config.radius = Double.parseDouble(rootNode.attrs.get("data-tracker-radius"));
            if (rootNode.attrs.containsKey("data-tracker-y-offset"))
                config.yOffset = Double.parseDouble(rootNode.attrs.get("data-tracker-y-offset"));

            if (rootNode.attrs.containsKey("data-tracker-type"))
                config.targetType = rootNode.attrs.get("data-tracker-type");
            if (rootNode.attrs.containsKey("data-tracker-require-nbt")) {
                String[] nbtKeys = rootNode.attrs.get("data-tracker-require-nbt").split(",");
                for (String key : nbtKeys) config.requireNbt.add(key.trim());
            }

            ARGlassesHudRenderer.setTrackerTemplate(sessionId, rootNode, config);
        }

        for (HtmlNode child : rootNode.children) {
            initializeBindings(sessionId, child);
        }
    }

    private static String resolveChannel(HtmlNode node, String bindKey) {
        // ★ 属性名はすべて小文字でパースされるため、toLowerCase() で確実に一致させる
        String attrName = "data-channel-" + bindKey.toLowerCase(java.util.Locale.ROOT);
        HtmlNode current = node;
        while (current != null) {
            if (current.attrs.containsKey(attrName)) {
                return current.attrs.get(attrName);
            }
            current = current.parent;
        }
        return node.attrs.getOrDefault("data-channel", bindKey);
    }

    private static HtmlNode findTemplateChild(HtmlNode parent) {
        for (HtmlNode child : parent.children) {
            if (child.tag.equals("template")) return child;
        }
        return null;
    }

    private static String serializeToSimpleHtml(HtmlNode node) {
        if (node.tag.equals("#text")) return node.text;

        StringBuilder sb = new StringBuilder();
        if (!node.tag.equals("template")) {
            sb.append("<").append(node.tag);
            for (java.util.Map.Entry<String, String> attr : node.attrs.entrySet()) {
                sb.append(" ").append(attr.getKey()).append("=\"").append(attr.getValue()).append("\"");
            }
            sb.append(">");
        }

        for (HtmlNode child : node.children) {
            sb.append(serializeToSimpleHtml(child));
        }

        if (!node.tag.equals("template")) {
            sb.append("</").append(node.tag).append(">");
        }
        return sb.toString();
    }
}