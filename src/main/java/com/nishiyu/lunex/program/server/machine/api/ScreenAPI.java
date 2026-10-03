package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.api.MainframeConstants;
import com.nishiyu.lunex.api.mainframe.IMainframeAPI;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.machine.VirtualStorage;
import com.nishiyu.lunex.mcnet.ScreenSession;
import com.nishiyu.lunex.mcnet.ScreenSessionManager;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.server.ServerPubSubManager;
import com.nishiyu.lunex.webrender.UIParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScreenAPI implements AutoCloseable, IMainframeAPI {
    private ServerLuaVM vm;
    private ScreenSessionManager sessionManager;

    private final java.util.Set<BlockPos> linkedScreenPositions = ConcurrentHashMap.newKeySet();

    public ScreenAPI() {}
    public ScreenAPI(ServerLuaVM vm) {
        this.vm = vm;
        this.sessionManager = vm.getOrCreateAPI(ScreenSessionManager.class, ScreenSessionManager::new);
    }

    @Override
    public String getNamespace() { return MainframeConstants.API_SCREEN; }

    @Override
    public String getRequiredFeature() { return MainframeConstants.FEATURE_SCREEN; }

    @Override
    public Object createInstance(ServerLuaVM vm) {
        return new ScreenAPI(vm);
    }

    public UIParser.Document getDocument(String targetStr) {
        List<ScreenSession> sessions = sessionManager.resolveSessions(targetStr);
        return sessions.isEmpty() ? null : sessions.get(0).getDocument();
    }

    public void requestUpdate(String targetStr) {
        if (vm != null && vm.isRunning) {
            vm.mainThreadTasks.add(() -> {
                for (ScreenSession session : sessionManager.resolveSessions(targetStr)) {
                    session.requestReRender();
                }
            });
        }
    }

    @LuaFunction(
            value = "このスクリーンの横幅（ピクセル数）を取得します。",
            args = {}, rets = {"num:width"}, isAsync = false
    )
    public int getWidth(String targetStr) {
        List<ScreenSession> resolved = sessionManager.resolveSessions(targetStr);
        return resolved.isEmpty() ? 0 : resolved.get(0).getWidth();
    }

    @LuaFunction(
            value = "このスクリーンの縦幅（ピクセル数）を取得します。",
            args = {}, rets = {"num:height"}, isAsync = false
    )
    public int getHeight(String targetStr) {
        List<ScreenSession> resolved = sessionManager.resolveSessions(targetStr);
        return resolved.isEmpty() ? 0 : resolved.get(0).getHeight();
    }

    @LuaFunction(
            value = "HTML/ファイル/URL、CSS、Luaスクリプトを組み合わせてスクリーンに描画します。",
            args = {"str:source", "str:css(optional)", "str:script(optional)"}, rets = {"bool:success"}, isAsync = false
    )
    public boolean load(String targetStr, String source, String css, String script) {
        return loadWithBindings(targetStr, source, null, css, script);
    }

    public boolean loadWithBindings(String targetStr, String source, LuaTable bindings, String css, String script) {
        if (source == null || source.trim().isEmpty()) {
            throw new org.luaj.vm2.LuaError("DOM Error: Provided source string is empty.");
        }

        String htmlContent = resolveContent(source);
        String cssContent = (css != null && !css.isEmpty()) ? resolveContent(css) : "";
        String scriptContent = (script != null && !script.isEmpty()) ? resolveContent(script) : "";

        if (bindings != null) {
            String sessionPrefix = "scr_" + targetStr.replaceAll("[^a-zA-Z0-9]", "") + "_";
            StringBuilder injectionAttrs = new StringBuilder();

            LuaValue k = LuaValue.NIL;
            while (true) {
                Varargs n = bindings.next(k);
                if ((k = n.arg1()).isnil()) break;

                String bindKey = k.tojstring();
                LuaValue bindTarget = n.arg(2);
                String autoChannelName = sessionPrefix + bindKey + "_" + System.currentTimeMillis();

                if (bindTarget.istable() || bindTarget.isuserdata()) {
                    startVirtualStoragePublish(autoChannelName, bindTarget);
                } else if (!bindTarget.isnil()) {
                    Map<String, Object> wrapper = new HashMap<>();
                    if (bindTarget.isint()) wrapper.put(bindKey, bindTarget.toint());
                    else if (bindTarget.isnumber()) wrapper.put(bindKey, bindTarget.todouble());
                    else if (bindTarget.isboolean()) wrapper.put(bindKey, bindTarget.toboolean());
                    else wrapper.put(bindKey, bindTarget.tojstring());

                    ServerPubSubManager.publish(autoChannelName, wrapper);
                }
                injectionAttrs.append(" data-channel-").append(bindKey).append("=\"").append(autoChannelName).append("\"");
            }
            if (injectionAttrs.length() > 0) {
                htmlContent = htmlContent.replaceFirst("<([a-zA-Z]+)", "<$1" + injectionAttrs.toString());
            }
        }

        if (!scriptContent.isEmpty()) {
            htmlContent += "\n<script>\n" + scriptContent + "\n</script>";
        }

        final String finalHtml = htmlContent;
        final String finalCss = cssContent;

        return vm.executeInMainThreadSync(() -> {
            List<ScreenSession> resolved = sessionManager.resolveSessions(targetStr);
            if (resolved.isEmpty()) return false;
            for (ScreenSession session : resolved) session.loadWithCss(finalHtml, finalCss);
            return true;
        }, 50, true);
    }

    public boolean load(String targetStr, String source, String css) { return load(targetStr, source, css, null); }
    public boolean load(String targetStr, String source) { return load(targetStr, source, null, null); }

    private void startVirtualStoragePublish(String channel, LuaValue storageObj) {
        if (storageObj == null || storageObj.isnil()) return;
        Thread.startVirtualThread(() -> {
            boolean firstRun = true;
            while (vm != null && vm.isRunning) {
                try {
                    Thread.sleep(500);
                    final boolean isFirst = firstRun;
                    firstRun = false;
                    vm.mainThreadTasks.add(() -> {
                        if (!vm.isRunning) return;
                        Map<String, Object> currentItems = new HashMap<>();
                        String foundMethod = "NONE";
                        try {
                            Object result = null;
                            Object javaApi = null;
                            if (storageObj.isuserdata()) javaApi = storageObj.checkuserdata();
                            else if (storageObj.istable()) {
                                LuaValue ud = storageObj.get("userdata");
                                if (!ud.isnil() && ud.isuserdata()) javaApi = ud.checkuserdata();
                            }
                            if (javaApi instanceof VirtualStorage vs) {
                                result = vs.getAllItems();
                                foundMethod = "Java API -> VirtualStorage.getAllItems()";
                            }
                            if (result == null && storageObj.istable()) {
                                String[] methodNames = {"getAllItems", "getItems", "list", "getInventory", "getItemList", "getAll"};
                                for (String mName : methodNames) {
                                    LuaValue func = storageObj.get(mName);
                                    if (!func.isnil() && func.isfunction()) {
                                        LuaValue res = func.call(storageObj);
                                        if (res.istable() || res.isuserdata()) {
                                            result = res;
                                            foundMethod = "Lua API -> " + mName;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (result instanceof Map<?, ?> map) {
                                for (Map.Entry<?, ?> entry : map.entrySet()) currentItems.put(String.valueOf(entry.getKey()), entry.getValue());
                            } else if (result instanceof LuaTable table) {
                                LuaValue tk = LuaValue.NIL;
                                while (true) {
                                    Varargs n = table.next(tk);
                                    if ((tk = n.arg1()).isnil()) break;
                                    LuaValue val = n.arg(2);
                                    if (tk.isstring() && val.isint()) currentItems.put(tk.tojstring(), val.toint());
                                    else if (tk.isint() && val.istable()) {
                                        LuaValue idVal = val.get("id");
                                        if (idVal.isnil()) idVal = val.get("name");
                                        LuaValue countVal = val.get("count");
                                        if (!idVal.isnil() && !countVal.isnil()) currentItems.put(idVal.tojstring(), countVal.toint());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Lunex.LOGGER.error("[Server PubSub] Critical error during extraction: ", e);
                        }
                        ServerPubSubManager.publish(channel, currentItems);
                        if (isFirst || "NONE".equals(foundMethod)) {
                            Lunex.LOGGER.info("[Server PubSub] Tracking started. Channel: " + channel + " | Method: " + foundMethod + " | Total Items: " + currentItems.size());
                        }
                    });
                } catch (InterruptedException e) { break; }
            }
            Lunex.LOGGER.info("[Server PubSub] Stopped tracking storage. Channel: " + channel);
        });
    }

    private String resolveContent(String content) {
        if (content == null || content.trim().isEmpty()) return "";
        if (content.startsWith("http://") || content.startsWith("https://")) {
            try {
                URL url = new URI(content).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                }
                return sb.toString();
            } catch (Exception e) { return content; }
        } else if (!content.contains("\n")) {
            try {
                Path path = Paths.get(content);
                if (Files.exists(path) && !Files.isDirectory(path)) return Files.readString(path, StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }
        return content;
    }

    @LuaFunction(
            value = "直前にロードしたHTML/ファイル/URLを再読み込みして描画を更新します。",
            args = {}, rets = {"bool:success"}, isAsync = false
    )
    public boolean reload(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            List<ScreenSession> resolved = sessionManager.resolveSessions(targetStr);
            if (resolved.isEmpty()) return false;
            for (ScreenSession session : resolved) session.reload();
            return true;
        }, 50, true);
    }

    @LuaFunction(
            value = "このスクリーンの表示内容をすべて消去します。",
            args = {}, rets = {}, isAsync = false
    )
    public void clearScreen(String targetStr) {
        vm.executeInMainThreadSync(() -> {
            for (ScreenSession session : sessionManager.resolveSessions(targetStr)) session.clear();
            return null;
        }, 50, true);
    }

    @LuaFunction(
            value = "スクリーンが遠く離れた際に表示されるフォールバックの単色を設定します。",
            args = {"num:argbColor"}, rets = {}, isAsync = false
    )
    public void setLODColor(String targetStr, int argbColor) {
        vm.executeInMainThreadSync(() -> {
            for (ScreenBlockEntity screen : sessionManager.getScreensFast(targetStr)) screen.setLODColor(argbColor);
            return null;
        }, 50, true);
    }

    @LuaFunction(
            value = "スクリーンとスピーカーをリンクし、音声の出力先を変更します。",
            args = {"table:speakers"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean linkSpeakers(String targetStr, LuaTable speakerTargets) {
        return vm.executeInMainThreadSync(() -> {
            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            if (machine == null) return false;

            List<ScreenBlockEntity> screens = sessionManager.getScreensFast(targetStr);
            if (screens.isEmpty()) return false;

            DeviceAPI deviceApi = vm.getOrCreateAPI(DeviceAPI.class, DeviceAPI::new);
            Level level = machine.getLevel();
            if (level == null) return false;

            List<BlockPos> speakers = new java.util.ArrayList<>();
            for (int i = 1; i <= speakerTargets.length(); i++) {
                String spkTarget = speakerTargets.get(i).tojstring();
                BlockPos spPos = deviceApi.getActionTargetPos(spkTarget);
                if (spPos != null && level.getBlockEntity(spPos) instanceof com.nishiyu.lunex.blockentity.SpeakerBlockEntity) {
                    if (!speakers.contains(spPos)) speakers.add(spPos);
                }
            }

            boolean anyUpdated = false;
            for (ScreenBlockEntity sbe : screens) {
                if (speakers.isEmpty() && speakerTargets.length() > 0) {
                    sbe.setLinkedSpeakers(new java.util.ArrayList<>());
                } else {
                    sbe.setLinkedSpeakers(new java.util.ArrayList<>(speakers));
                    BlockPos actualPos = sbe.isMaster ? sbe.getBlockPos() : (sbe.masterPos != null ? sbe.masterPos : sbe.getBlockPos());
                    linkedScreenPositions.add(actualPos);
                    anyUpdated = true;
                }
            }
            return anyUpdated || speakerTargets.length() == 0;
        }, 50, false);
    }

    @LuaFunction(
            value = "クライアントからのfetchリクエストに対してレスポンス(JSON文字列など)を返します。",
            args = {"str:requestId", "str:responsePayload"}, rets = {}, isAsync = false
    )
    public void sendFetchResponse(String requestId, String responsePayload) {
        vm.executeInMainThreadSync(() -> {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("requestId", requestId);
            tag.putString("json", responsePayload);
            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
                    new com.nishiyu.lunex.network.packet.s2c.AppMessageS2CPacket("global", "fetch_response", tag)
            );
            return null;
        }, 0, false);
    }

    @LuaFunction(
            value = "指定したスクリーンの全クライアントへイベントをブロードキャストします。",
            args = {"str:targetStr", "str:eventName", "str:payload"}, rets = {}, isAsync = false
    )
    public void broadcastEvent(String targetStr, String eventName, String payload) {
        vm.executeInMainThreadSync(() -> {
            for (com.nishiyu.lunex.mcnet.ScreenSession session : sessionManager.resolveSessions(targetStr)) {
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                tag.putString("eventName", eventName);
                tag.putString("json", payload);
                net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
                        new com.nishiyu.lunex.network.packet.s2c.AppMessageS2CPacket(session.getSessionId(), "server_event", tag)
                );
            }
            return null;
        }, 0, false);
    }

    public void onShutdown() {
        if (sessionManager != null) sessionManager.onShutdown();
        clearSpeakerLinks();
    }

    @Override
    public void close() {
        if (sessionManager != null) sessionManager.onShutdown();
        clearSpeakerLinks();
    }

    private void clearSpeakerLinks() {
        if (vm == null) return;
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null || machine.getLevel() == null) return;
        Level level = machine.getLevel();

        Runnable clearTask = () -> {
            for (BlockPos pos : linkedScreenPositions) {
                if (level.getBlockEntity(pos) instanceof com.nishiyu.lunex.blockentity.ScreenBlockEntity sbe) {
                    sbe.setLinkedSpeakers(new java.util.ArrayList<>());
                }
            }
            linkedScreenPositions.clear();
        };

        if (level.getServer() != null && level.getServer().isSameThread()) level.getServer().execute(clearTask);
        else if (level.getServer() != null) level.getServer().execute(clearTask);
    }
}