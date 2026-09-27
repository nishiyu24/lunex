package com.nishiyu.lunex.client;

import com.google.gson.JsonObject;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.webrender.HtmlNode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.*;

public class DataBindingManager {

    public static com.google.gson.JsonElement getJsonValueByPath(JsonObject obj, String path) {
        if (obj == null || path == null || path.isEmpty()) return null;
        String[] parts = path.split("\\.");
        com.google.gson.JsonElement current = obj;
        for (String part : parts) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
        }
        return current;
    }

    public static boolean updateHudByConfig(HtmlNode root, HitResult hit, JsonObject extraData, HudConfig config) {
        boolean changed = false;

        HtmlNode container = root.getElementById(config.containerId);
        if (container != null) {
            String orig = config.origDisplay;
            if (hit == null || hit.getType() == HitResult.Type.MISS || (hit.getType() == HitResult.Type.BLOCK && ((BlockHitResult) hit).getBlockPos() == null)) {
                if (!"none".equals(container.attrs.get("display"))) {
                    container.attrs.put("display", "none");
                    container.attrs.put("style", container.attrs.getOrDefault("style", "").replaceAll("(?i)display\\s*:\\s*[^;]+;?", "") + "; display: none;");
                    changed = true;
                }
                return changed;
            } else {
                if (!orig.equals(container.attrs.get("display"))) {
                    container.attrs.put("display", orig);
                    container.attrs.put("style", container.attrs.getOrDefault("style", "").replaceAll("(?i)display\\s*:\\s*none\\s*;?", "") + "; display: " + orig + ";");
                    changed = true;
                }
            }
        }

        for (HudBinding b : config.bindings) {
            HtmlNode node = root.getElementById(b.id);
            if (node == null) continue;

            boolean show = true;
            if (b.showIfType != null) {
                if (hit == null || hit.getType() == HitResult.Type.MISS) show = false;
                else if (b.showIfType.equals("entity") && hit.getType() != HitResult.Type.ENTITY) show = false;
                else if (b.showIfType.equals("block") && hit.getType() != HitResult.Type.BLOCK) show = false;
            }

            if (show && b.showIfPath != null && extraData != null) {
                com.google.gson.JsonElement condElem = getJsonValueByPath(extraData, b.showIfPath);
                if (condElem == null || condElem.isJsonNull()) show = false;
                else if (condElem.isJsonPrimitive()) {
                    if (condElem.getAsJsonPrimitive().isBoolean() && !condElem.getAsBoolean()) show = false;
                    else if (condElem.getAsJsonPrimitive().isNumber() && condElem.getAsDouble() <= 0) show = false;
                } else if (condElem.isJsonArray() && condElem.getAsJsonArray().isEmpty()) {
                    show = false;
                }
            }

            if (!show) {
                if (!"none".equals(node.attrs.get("display"))) {
                    node.attrs.put("display", "none");
                    node.attrs.put("style", node.attrs.getOrDefault("style", "").replaceAll("(?i)display\\s*:\\s*[^;]+;?", "") + "; display: none;");
                    changed = true;
                }
                continue;
            }

            boolean hasContent = false;
            if (b.type != null) {
                com.google.gson.JsonElement dataElem = (b.dataPath != null && extraData != null) ? getJsonValueByPath(extraData, b.dataPath) : null;

                if (b.type.equals("icon")) {
                    if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                        String name = BuiltInRegistries.BLOCK.getKey(Minecraft.getInstance().level.getBlockState(pos).getBlock()).toString();
                        if (!name.equals(node.attrs.get("name"))) {
                            node.attrs.put("name", name);
                            changed = true;
                        }
                        hasContent = true;
                    }
                }
                // ★修正: dataPathが "name" だった場合や、type自体が "name" の場合は確実にローカライズ名を取得する
                else if (b.type.equals("name") || (b.type.equals("text") && "name".equals(b.dataPath))) {
                    String newName = "";
                    if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                        // ItemStack経由で取得することで綺麗な日本語名が手に入る
                        newName = new ItemStack(Minecraft.getInstance().level.getBlockState(pos).getBlock()).getHoverName().getString();
                        if (newName.isEmpty() || newName.equals("Air")) {
                            newName = Minecraft.getInstance().level.getBlockState(pos).getBlock().getName().getString();
                        }
                        hasContent = true;
                    } else if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
                        LivingEntity le = (LivingEntity) ((EntityHitResult) hit).getEntity();
                        newName = le.getDisplayName().getString();
                        hasContent = true;
                    }

                    if (!newName.isEmpty()) {
                        String text = (b.format != null) ? b.format.replace("%s", newName) : newName;
                        if (setTextNodeIfChanged(node, text)) changed = true;
                    }
                } else if (b.type.equals("text")) {
                    if (dataElem != null && dataElem.isJsonPrimitive()) {
                        String val = dataElem.getAsString();
                        if (dataElem.getAsJsonPrimitive().isNumber() && val.endsWith(".0"))
                            val = val.substring(0, val.length() - 2);
                        String text = (b.format != null) ? b.format.replace("%s", val) : val;
                        if (setTextNodeIfChanged(node, text)) changed = true;
                        hasContent = true;
                    }
                } else if (b.type.equals("hearts")) {
                    if (dataElem != null && dataElem.isJsonPrimitive()) {
                        float hp = dataElem.getAsFloat();
                        int hearts = (int) Math.ceil(hp / 2.0);
                        String hpStr = hearts <= 0 ? "Dead" : "♥".repeat(hearts);
                        if (setTextNodeIfChanged(node, hpStr)) changed = true;
                        hasContent = true;
                    }
                } else if (b.type.equals("inventory")) {
                    if (dataElem != null && dataElem.isJsonArray()) {
                        node.children.clear();
                        boolean hasItems = false;
                        com.google.gson.JsonElement nameElem = getJsonValueByPath(extraData, "name");
                        String targetName = nameElem != null ? nameElem.getAsString() : "";

                        for (var elem : dataElem.getAsJsonArray()) {
                            JsonObject itemObj = elem.getAsJsonObject();
                            int slot = itemObj.get("slot").getAsInt();
                            String idStr = itemObj.get("id").getAsString();
                            int count = itemObj.get("count").getAsInt();

                            String itemName = idStr;
                            if (itemName.contains(":")) itemName = itemName.split(":")[1];
                            String[] words = itemName.split("_");
                            StringBuilder formattedName = new StringBuilder();
                            for (String word : words) {
                                if (word.length() > 0) {
                                    formattedName.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
                                }
                            }
                            itemName = formattedName.toString().trim();

                            String prefix = "Slot " + slot + ": ";
                            if (targetName.contains("furnace")) {
                                if (slot == 0) prefix = "Input: ";
                                else if (slot == 1) prefix = "Fuel: ";
                                else if (slot == 2) prefix = "Output: ";
                            }

                            HtmlNode line = new HtmlNode("div");
                            line.attrs.put("style", "color: #AAAAAA; font-size: 1.1rem; margin-bottom: 2px;");

                            HtmlNode txt = new HtmlNode("#text");
                            txt.text = prefix + itemName + " x " + count;
                            txt.parent = line;
                            line.children.add(txt);

                            line.parent = node;
                            node.children.add(line);
                            hasItems = true;
                        }
                        changed = true;
                        hasContent = hasItems;
                    }
                }
            } else {
                hasContent = true;
            }

            String orig = b.origDisplay;
            if (hasContent) {
                if (!orig.equals(node.attrs.get("display"))) {
                    node.attrs.put("display", orig);
                    node.attrs.put("style", node.attrs.getOrDefault("style", "").replaceAll("(?i)display\\s*:\\s*none\\s*;?", "") + "; display: " + orig + ";");
                    changed = true;
                }
            } else {
                if (!"none".equals(node.attrs.get("display"))) {
                    node.attrs.put("display", "none");
                    node.attrs.put("style", node.attrs.getOrDefault("style", "").replaceAll("(?i)display\\s*:\\s*[^;]+;?", "") + "; display: none;");
                    changed = true;
                }
            }
        }
        return changed;
    }

    public static void bindTrackerDataByConfig(HtmlNode root, TrackerCache cache, TrackerConfig config) {
        for (TrackerBinding b : config.bindings) {
            HtmlNode node = root.getElementById(b.id);
            if (node == null) continue;

            if (b.type.equals("text") && b.dataPath != null) {
                String val = cache.data.getOrDefault(b.dataPath, "");
                if (!val.isEmpty()) {
                    if (val.endsWith(".0")) val = val.substring(0, val.length() - 2);
                    String text = (b.format != null) ? b.format.replace("%s", val) : val;
                    setTextNodeIfChanged(node, text);
                }
            } else if (b.type.equals("effects")) {
                node.children.clear();
                for (String effectId : cache.effects) {
                    HtmlNode img = new HtmlNode("img");
                    img.id = "effect_img_" + UUID.randomUUID().toString();
                    img.attrs.put("src", "effect:" + effectId);
                    img.attrs.put("style", "width: 14px; height: 14px; margin-left: 2px; margin-top: 1px;");
                    img.parent = node;
                    node.children.add(img);
                }
            } else if (b.type.equals("hp-bar")) {
                float hp = 0;
                float maxHp = 1;
                try {
                    hp = Float.parseFloat(cache.data.getOrDefault("hp", "0"));
                    maxHp = Float.parseFloat(cache.data.getOrDefault("max_hp", "1"));
                } catch (Exception ignored) {
                }

                float percent = Math.max(0, Math.min(100, (hp / maxHp) * 100));
                String color = "#00FF00";
                if (percent < 25) color = "#FF0000";
                else if (percent < 50) color = "#FFFF00";

                String style = node.attrs.getOrDefault("style", "");
                style = style.replaceAll("(?i)width\\s*:\\s*[^;]+;?", "");
                style = style.replaceAll("(?i)background-color\\s*:\\s*[^;]+;?", "");
                style += "; width: " + percent + "%; background-color: " + color + "; height: 14px;";
                node.attrs.put("style", style);
            }
        }
    }

    public static boolean setTextNodeIfChanged(HtmlNode node, String text) {
        for (HtmlNode child : node.children) {
            if ("#text".equals(child.tag)) {
                if (!text.equals(child.text)) {
                    child.text = text;
                    return true;
                }
                return false;
            }
        }
        if (!text.isEmpty()) {
            HtmlNode txtNode = new HtmlNode("#text");
            txtNode.text = text;
            txtNode.parent = node;
            node.children.add(txtNode);
            return true;
        }
        return false;
    }

    public static class TrackerBinding {
        public String id;
        public String type = "text";
        public String dataPath;
        public String format;
    }

    public static class TrackerConfig {
        public double radius = 24.0;
        public double yOffset = 0.6;
        public String targetType = "living";
        public List<TrackerBinding> bindings = new ArrayList<>();
    }

    public static class TrackerCache {
        public Map<String, String> data = new HashMap<>();
        public List<String> effects = new ArrayList<>();
        public List<ScreenBlockEntity.UIElement> elements;
        public int width;
        public int height;

        public boolean isChanged(Map<String, String> newData, List<String> newEffects) {
            return !this.data.equals(newData) || !this.effects.equals(newEffects);
        }
    }

    public static class HudBinding {
        public String id;
        public String type = "text";
        public String dataPath;
        public String showIfType;
        public String showIfPath;
        public String origDisplay = "block";
        public String format;
    }

    public static class HudConfig {
        public String containerId;
        public String origDisplay = "flex";
        public List<HudBinding> bindings = new ArrayList<>();
    }
}