package com.nishiyu.lunex.client;

import com.nishiyu.lunex.block.ScreenBlock;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.client.renderer.ScreenRenderCore;
import com.nishiyu.lunex.menu.utiles.InvisibleInputScreen;
import com.nishiyu.lunex.program.client.ClientScriptManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

// ★ 追加: NeoForgeのイベントを直接受け取る
@EventBusSubscriber(modid = "lunex", value = Dist.CLIENT)
public class ClientScreenInteractionManager {

    private static final Map<String, SessionState> states = new ConcurrentHashMap<>();
    private static boolean wasMouseDown = false;
    private static String lastHoveredSession = "";

    private static final Map<String, RouterDistanceInfo> ROUTER_DISTANCE_CACHE = new ConcurrentHashMap<>();
    public record RouterDistanceInfo(long posLong, String dimension, double maxDistSqr) {}

    // ★ ScreenInputHandlerからの統合部分
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean isMouseDown = mc.options.keyUse.isDown() || mc.options.keyAttack.isDown();
        boolean isMouseJustPressed = isMouseDown && !wasMouseDown;
        boolean isMouseJustReleased = !isMouseDown && wasMouseDown;
        wasMouseDown = isMouseDown;

        if (isMouseJustReleased && !lastHoveredSession.isEmpty()) {
            handleRelease(lastHoveredSession);
        }

        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hitResult = (BlockHitResult) mc.hitResult;
            BlockPos pos = hitResult.getBlockPos();

            if (mc.level.getBlockEntity(pos) instanceof ScreenBlockEntity screen) {
                Direction facing = screen.getBlockState().getValue(ScreenBlock.FACING);

                if (hitResult.getDirection() == facing) {
                    double hitX = hitResult.getLocation().x - pos.getX();
                    double hitY = hitResult.getLocation().y - pos.getY();
                    double hitZ = hitResult.getLocation().z - pos.getZ();

                    double blockU = 0, blockV = 1.0 - hitY;
                    switch (facing) {
                        case NORTH -> blockU = 1.0 - hitX;
                        case SOUTH -> blockU = hitX;
                        case WEST -> blockU = hitZ;
                        case EAST -> blockU = 1.0 - hitZ;
                    }

                    ScreenBlockEntity master = screen.isMaster ? screen :
                            (screen.masterPos != null && mc.level.getBlockEntity(screen.masterPos) instanceof ScreenBlockEntity m ? m : screen);

                    String sessionId = master.getSessionId();
                    lastHoveredSession = sessionId;
                    BlockPos masterPos = master.getBlockPos();

                    Direction screenRight = facing.getCounterClockWise();
                    int logicalX = 0;
                    if (screenRight == Direction.WEST) logicalX = masterPos.getX() - pos.getX();
                    else if (screenRight == Direction.EAST) logicalX = pos.getX() - masterPos.getX();
                    else if (screenRight == Direction.NORTH) logicalX = masterPos.getZ() - pos.getZ();
                    else if (screenRight == Direction.SOUTH) logicalX = pos.getZ() - masterPos.getZ();
                    int logicalY = pos.getY() - masterPos.getY();

                    double pixelX = (logicalX + blockU) * ScreenBlockEntity.RESOLUTION;
                    double pixelY = ((master.screenHeight - 1 - logicalY) + blockV) * ScreenBlockEntity.RESOLUTION;

                    if (isMouseJustPressed) {
                        ScreenBlockEntity.UIElement clickedEl = handleClick(sessionId, pixelX, pixelY);
                        if (clickedEl != null && "input".equals(clickedEl.type())) {
                            ScreenRenderCore.activeInputId = clickedEl.id();
                            openInputScreen(sessionId, clickedEl, masterPos);
                        } else {
                            ScreenRenderCore.activeInputId = "";
                        }
                    } else if (isMouseDown) {
                        handleDrag(sessionId, pixelX, pixelY);
                    }

                    handleHover(sessionId, pixelX, pixelY);
                    return;
                }
            }
        }

        if (!lastHoveredSession.isEmpty()) {
            handleHover(lastHoveredSession, -1, -1);
            lastHoveredSession = "";
        }
    }
    // ★ 統合部分ここまで

    // ★ ClientScreenHandlerからの統合部分
    public static void openInputScreen(String sessionId, ScreenBlockEntity.UIElement clickedElement, BlockPos pos) {
        if (clickedElement != null && "input".equals(clickedElement.type())) {
            Minecraft.getInstance().setScreen(new InvisibleInputScreen(sessionId, clickedElement, pos));
        }
    }
    // ★ 統合部分ここまで

    public static boolean isWithinRange(String screenKey, CompoundTag tag, Minecraft mc) {
        if (mc.player == null || mc.level == null) return false;

        RouterDistanceInfo info = ROUTER_DISTANCE_CACHE.computeIfAbsent(screenKey, k -> {
            if (!tag.contains("RouterPos")) return null;
            double maxDist = tag.getDouble("RouterRange");
            return new RouterDistanceInfo(
                    tag.getLong("RouterPos"),
                    tag.getString("RouterDim"),
                    maxDist == Double.MAX_VALUE ? Double.MAX_VALUE : maxDist * maxDist
            );
        });

        if (info == null) return true;

        boolean isSameDim = mc.level.dimension().location().toString().equals(info.dimension());
        if (!isSameDim) {
            return info.maxDistSqr() == Double.MAX_VALUE;
        }
        if (info.maxDistSqr() == Double.MAX_VALUE) {
            return true;
        }

        BlockPos pos = BlockPos.of(info.posLong());
        return mc.player.blockPosition().distSqr(pos) <= info.maxDistSqr();
    }

    public static void clearDistanceCache(String screenKey) {
        ROUTER_DISTANCE_CACHE.remove(screenKey);
    }

    private static SessionState getState(String sessionId) {
        return states.computeIfAbsent(sessionId, k -> new SessionState());
    }

    private static void triggerLocalEvent(String sessionId, String elementId, String eventName, String actionName, String value) {
        if (sessionId == null || sessionId.isEmpty()) return;
        if (actionName != null && !actionName.isEmpty() && !"auto".equals(actionName)) {
            ClientScriptManager.triggerClientEvent(sessionId, actionName, value);
        }
    }

    private static String getEventAction(Map<String, String> events, String eventName) {
        if (events == null || events.isEmpty()) return "";
        for (Map.Entry<String, String> entry : events.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(eventName)) {
                return entry.getValue();
            }
        }
        return "";
    }

    private static boolean hasEvent(Map<String, String> events, String eventName) {
        if (events == null || events.isEmpty()) return false;
        for (String key : events.keySet()) {
            if (key.equalsIgnoreCase(eventName)) return true;
        }
        return false;
    }

    public static void checkAndSendChange(String sessionId, ScreenBlockEntity.UIElement el, String newValue) {
        if (el.events() == null || !el.events().containsKey("onchange")) return;

        SessionState state = getState(sessionId);
        String key = "bind_" + el.id();

        if (newValue == null) newValue = "";
        String lastValue = state.lastBindValues.getOrDefault(key, "");

        if (!newValue.equals(lastValue)) {
            state.lastBindValues.put(key, newValue);
            triggerLocalEvent(sessionId, ClientScreenManager.getBaseNodeId(el.id()), "onchange", getEventAction(el.events(), "onchange"), newValue);
        }
    }

    public static void ensureSynced(String sessionId) {
        if (!ClientScreenManager.hasDocument(sessionId) && ClientScreenManager.shouldRequestSync(sessionId)) {
            PacketDistributor.sendToServer(new com.nishiyu.lunex.network.packet.c2s.AppMessageC2SPacket(sessionId, "request_sync", new CompoundTag()));
        }
    }

    public static String getHoveredId(String sessionId) {
        SessionState state = states.get(sessionId);
        return state != null ? state.hoveredId : "";
    }

    public static void applyLocalOverrides(String sessionId) {
        SessionState state = getState(sessionId);
        for (Map.Entry<String, String> entry : state.localInputCache.entrySet()) {
            ClientScreenManager.updateElementTextLocally(sessionId, entry.getKey(), entry.getValue());
        }
        if (!state.draggingId.isEmpty() && !state.lastRangeText.isEmpty()) {
            ClientScreenManager.updateElementTextLocally(sessionId, state.draggingId, state.lastRangeText);
        }
        if (System.currentTimeMillis() - state.lastSubmitTime < 1500) {
            if (!state.lastSubmittedId.isEmpty() && !state.lastSubmittedText.isEmpty()) {
                ClientScreenManager.updateElementTextLocally(sessionId, state.lastSubmittedId, state.lastSubmittedText);
            }
        }
        if (!state.hoveredId.isEmpty()) {
            ClientScreenManager.updateNodeAttributeLocally(sessionId, state.hoveredId, "hover", "true", false);
        }
        String activeId = com.nishiyu.lunex.client.renderer.ScreenRenderCore.activeInputId;
        if (activeId != null && !activeId.isEmpty()) {
            net.minecraft.client.gui.screens.Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
            if (screen != null) {
                String currentText = null;
                for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
                    if (child instanceof net.minecraft.client.gui.components.EditBox eb && eb.isFocused()) {
                        currentText = eb.getValue();
                        break;
                    }
                }
                if (currentText != null) {
                    updateInputLocally(sessionId, activeId, currentText);
                }
            }
        }
    }

    public static String getSessionIdFromItem(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (tag.contains("IPAddress")) {
            String ip = tag.getString("IPAddress");
            String networkId = tag.contains("NetworkId") ? tag.getUUID("NetworkId").toString() : "global";
            return networkId + ":" + ip;
        }
        return null;
    }

    public static String handleHover(String sessionId, double pixelX, double pixelY) {
        SessionState state = getState(sessionId);
        ClientScreenManager.UIHitResult hit = ClientScreenManager.getHitElement(sessionId, pixelX, pixelY);
        long now = System.currentTimeMillis();

        if (hit != null) {
            ScreenBlockEntity.UIElement hitElement = hit.element();
            String currentHitId = ClientScreenManager.getBaseNodeId(hitElement.id());
            Map<String, String> events = hitElement.events() != null ? hitElement.events() : Map.of();
            String coordStr = String.format(Locale.US, "%.1f,%.1f", hit.localX(), hit.localY());

            if (!currentHitId.equals(state.hoveredId)) {
                if (!state.hoveredId.isEmpty()) {
                    triggerLocalEvent(sessionId, state.hoveredId, "onmouseleave", "auto", "");
                    ClientScreenManager.updateNodeAttributeLocally(sessionId, state.hoveredId, "hover", null, true);
                }
                triggerLocalEvent(sessionId, currentHitId, "onmouseenter", getEventAction(events, "onmouseenter"), coordStr);
                ClientScreenManager.updateNodeAttributeLocally(sessionId, currentHitId, "hover", "true", false);
                state.hoveredId = currentHitId;
            }
            if (hasEvent(events, "onmousemove") && (now - state.lastEventTime > 50)) {
                triggerLocalEvent(sessionId, currentHitId, "onmousemove", getEventAction(events, "onmousemove"), coordStr);
                state.lastEventTime = now;
            }
            return currentHitId;
        } else {
            if (!state.hoveredId.isEmpty()) {
                triggerLocalEvent(sessionId, state.hoveredId, "onmouseleave", "auto", "");
                ClientScreenManager.updateNodeAttributeLocally(sessionId, state.hoveredId, "hover", null, true);
                state.hoveredId = "";
            }
            return "";
        }
    }

    public static ScreenBlockEntity.UIElement handleClick(String sessionId, double pixelX, double pixelY) {
        SessionState state = getState(sessionId);
        ClientScreenManager.UIHitResult hit = ClientScreenManager.getHitElement(sessionId, pixelX, pixelY);

        if (hit != null) {
            ScreenBlockEntity.UIElement clickedEl = hit.element();
            String baseId = ClientScreenManager.getBaseNodeId(clickedEl.id());
            Map<String, String> events = clickedEl.events() != null ? clickedEl.events() : Map.of();
            String cordStr = String.format(Locale.US, "%.1f,%.1f", hit.localX(), hit.localY());
            String clickPayload = baseId + "|" + cordStr;

            triggerLocalEvent(sessionId, baseId, "onclick", getEventAction(events, "onclick"), clickPayload);

            if ("toggle".equals(clickedEl.type()) || "checkbox".equals(clickedEl.type()) || "radio".equals(clickedEl.type())) {
                ClientScreenManager.toggleNodeAttributeLocally(sessionId, baseId, "checked");
            }
            if (hasEvent(events, "ondrag") || "range".equals(clickedEl.type())) {
                state.draggingId = baseId;
                state.lastSentX = hit.localX();
                state.lastSentY = hit.localY();

                if ("range".equals(clickedEl.type())) {
                    state.lastRatio = Mth.clamp((float) (hit.localX() / clickedEl.width()), 0.0f, 1.0f);
                    state.lastRangeText = generateRangeText(clickedEl.text(), state.lastRatio);
                    state.lastRangeAction = getEventAction(events, "oninput");
                    state.lastRangeChangeAction = getEventAction(events, "onchange");
                    updateInputLocally(sessionId, state.draggingId, state.lastRangeText);
                    triggerLocalEvent(sessionId, state.draggingId, "oninput", state.lastRangeAction, state.lastRangeText);
                } else {
                    triggerLocalEvent(sessionId, state.draggingId, "ondragstart", getEventAction(events, "ondragstart"), cordStr);
                }
            }
            return clickedEl;
        } else {
            triggerLocalEvent(sessionId, "", "onclick", "", String.format(Locale.US, "%.5f,%.5f", pixelX / ScreenBlockEntity.RESOLUTION, pixelY / ScreenBlockEntity.RESOLUTION));
            return null;
        }
    }

    public static boolean handleDrag(String sessionId, double pixelX, double pixelY) {
        SessionState state = getState(sessionId);
        if (state.draggingId.isEmpty()) return false;
        ClientScreenManager.UIHitResult dragHit = ClientScreenManager.getLocalCoords(sessionId, state.draggingId, pixelX, pixelY);
        long now = System.currentTimeMillis();

        if (dragHit != null) {
            ScreenBlockEntity.UIElement draggedEl = dragHit.element();
            state.lastSentX = dragHit.localX();
            state.lastSentY = dragHit.localY();
            Map<String, String> events = draggedEl.events() != null ? draggedEl.events() : Map.of();

            if ("range".equals(draggedEl.type())) {
                float ratio = Mth.clamp((float) (dragHit.localX() / draggedEl.width()), 0.0f, 1.0f);
                if (Math.abs(state.lastRatio - ratio) > 0.005f) {
                    state.lastRatio = ratio;
                    state.lastRangeText = generateRangeText(draggedEl.text(), ratio);
                    updateInputLocally(sessionId, state.draggingId, state.lastRangeText);
                    if (now - state.lastEventTime > 50) {
                        triggerLocalEvent(sessionId, state.draggingId, "oninput", state.lastRangeAction, state.lastRangeText);
                        state.lastEventTime = now;
                    }
                }
            } else {
                if (now - state.lastEventTime > 50) {
                    triggerLocalEvent(sessionId, state.draggingId, "ondrag", getEventAction(events, "ondrag"), String.format(Locale.US, "%.1f,%.1f", dragHit.localX(), dragHit.localY()));
                    state.lastEventTime = now;
                }
            }
        }
        return true;
    }

    public static void handleRelease(String sessionId) {
        SessionState state = getState(sessionId);
        if (!state.draggingId.isEmpty()) {
            if (state.lastRatio >= 0 && !state.lastRangeText.isEmpty()) {
                triggerLocalEvent(sessionId, state.draggingId, "oninput", state.lastRangeAction, state.lastRangeText);
                triggerLocalEvent(sessionId, state.draggingId, "onchange", state.lastRangeChangeAction, state.lastRangeText);
                state.lastSubmittedId = state.draggingId;
                state.lastSubmittedText = state.lastRangeText;
                state.lastSubmitTime = System.currentTimeMillis();
            }
            triggerLocalEvent(sessionId, state.draggingId, "ondragend", "auto", String.format(Locale.US, "%.1f,%.1f", state.lastSentX, state.lastSentY));
            state.draggingId = "";
            state.lastRatio = -1.0f;
            state.lastRangeText = "";
            state.lastRangeAction = "";
            state.lastRangeChangeAction = "";
        }
    }

    public static boolean handleScroll(String sessionId, double pixelX, double pixelY, double deltaX, double deltaY) {
        return ClientScreenManager.handleScrollLocally(sessionId, pixelX, pixelY, deltaX, deltaY);
    }

    public static void updateInputLocally(String sessionId, String elementId, String text) {
        SessionState state = getState(sessionId);
        String current = state.localInputCache.get(elementId);
        if (java.util.Objects.equals(current, text)) return;
        state.localInputCache.put(elementId, text);
        ClientScreenManager.updateElementTextLocally(sessionId, elementId, text);
    }

    public static void submitInput(String sessionId, String elementId, String text) {
        updateInputLocally(sessionId, elementId, text);
        ScreenBlockEntity.UIElement el = ClientScreenManager.getElementById(sessionId, elementId);
        Map<String, String> events = el != null && el.events() != null ? el.events() : Map.of();
        triggerLocalEvent(sessionId, elementId, "oninput", getEventAction(events, "oninput"), text);
        triggerLocalEvent(sessionId, elementId, "onchange", getEventAction(events, "onchange"), text);
        SessionState state = getState(sessionId);
        state.lastSubmittedId = elementId;
        state.lastSubmittedText = text;
        state.lastSubmitTime = System.currentTimeMillis();
    }

    private static String generateRangeText(String oldText, float ratio) {
        float min = 0, max = 100;
        String[] parts = (oldText != null ? oldText : "").split(":");
        if (parts.length >= 3) {
            try {
                min = Float.parseFloat(parts[1]);
                max = Float.parseFloat(parts[2]);
            } catch (Exception ignored) {}
        }
        int newVal = (int) (min + (max - min) * ratio);
        return newVal + ":" + min + ":" + max;
    }

    private static class SessionState {
        String hoveredId = "";
        String draggingId = "";
        float lastRatio = -1.0f;
        String lastRangeText = "";
        String lastRangeAction = "";
        String lastRangeChangeAction = "";
        double lastSentX = -1, lastSentY = -1;
        long lastEventTime = 0;
        String lastSubmittedId = "";
        String lastSubmittedText = "";
        long lastSubmitTime = 0;
        Map<String, String> lastBindValues = new ConcurrentHashMap<>();
        Map<String, String> localInputCache = new ConcurrentHashMap<>();
    }
}