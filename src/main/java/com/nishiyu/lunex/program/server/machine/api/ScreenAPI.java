package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.mcnet.ScreenSession;
import com.nishiyu.lunex.mcnet.ScreenSessionManager;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.webrender.UIParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ScreenAPI implements AutoCloseable {
    private final ServerLuaVM vm;
    private final ScreenSessionManager sessionManager;

    private final java.util.Set<BlockPos> linkedScreenPositions = ConcurrentHashMap.newKeySet();

    public ScreenAPI(ServerLuaVM vm) {
        this.vm = vm;
        this.sessionManager = vm.getOrCreateAPI(ScreenSessionManager.class, ScreenSessionManager::new);
    }

    public UIParser.Document getDocument(String targetStr) {
        List<ScreenSession> sessions = sessionManager.resolveSessions(targetStr);
        return sessions.isEmpty() ? null : sessions.get(0).getDocument();
    }

    public void requestUpdate(String targetStr) {
        if (vm.isRunning) {
            vm.mainThreadTasks.add(() -> {
                for (ScreenSession session : sessionManager.resolveSessions(targetStr)) {
                    session.requestReRender();
                }
            });
        }
    }

    @LuaFunction(
            value = "このスクリーンの横幅（ピクセル数）を取得します。",
            en = "Gets the width of this screen (in pixels).",
            args = {}, rets = {"num:width"}, isAsync = false
    )
    public int getWidth(String targetStr) {
        List<ScreenSession> resolved = sessionManager.resolveSessions(targetStr);
        return resolved.isEmpty() ? 0 : resolved.get(0).getWidth();
    }

    @LuaFunction(
            value = "このスクリーンの縦幅（ピクセル数）を取得します。",
            en = "Gets the height of this screen (in pixels).",
            args = {}, rets = {"num:height"}, isAsync = false
    )
    public int getHeight(String targetStr) {
        List<ScreenSession> resolved = sessionManager.resolveSessions(targetStr);
        return resolved.isEmpty() ? 0 : resolved.get(0).getHeight();
    }

    @LuaFunction(
            value = "HTML/ファイル/URL、CSS、Luaスクリプトを組み合わせてスクリーンに描画します。引数の数に応じて処理が変わります。",
            en = "Loads HTML/file/URL, CSS, and Lua scripts and renders the UI on the screen.",
            args = {"str:source", "str:css(optional)", "str:script(optional)"}, rets = {"bool:success"}, isAsync = false
    )
    public boolean load(String targetStr, String source, String css, String script) {
        if (source == null || source.trim().isEmpty()) {
            throw new org.luaj.vm2.LuaError("DOM Error: Provided source string is empty.");
        }

        String htmlContent = resolveContent(source);
        String cssContent = (css != null && !css.isEmpty()) ? resolveContent(css) : "";
        String scriptContent = (script != null && !script.isEmpty()) ? resolveContent(script) : "";

        if (!scriptContent.isEmpty()) {
            htmlContent += "\n<script>\n" + scriptContent + "\n</script>";
        }

        final String finalHtml = htmlContent;
        final String finalCss = cssContent;

        return vm.executeInMainThreadSync(() -> {
            List<ScreenSession> resolved = sessionManager.resolveSessions(targetStr);
            if (resolved.isEmpty()) {
                return false;
            }

            for (ScreenSession session : resolved) {
                session.loadWithCss(finalHtml, finalCss);
            }
            return true;
        }, 50, true);
    }

    public boolean load(String targetStr, String source, String css) {
        return load(targetStr, source, css, null);
    }

    public boolean load(String targetStr, String source) {
        return load(targetStr, source, null, null);
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
            } catch (Exception e) {
                return content;
            }
        }
        else if (!content.contains("\n")) {
            try {
                Path path = Paths.get(content);
                if (Files.exists(path) && !Files.isDirectory(path)) {
                    return Files.readString(path, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
            }
        }
        return content;
    }

    @LuaFunction(
            value = "直前にロードしたHTML/ファイル/URLを再読み込みして描画を更新します。",
            en = "Reloads the previously loaded HTML/file/URL and updates the rendering.",
            args = {}, rets = {"bool:success"}, isAsync = false
    )
    public boolean reload(String targetStr) {
        return vm.executeInMainThreadSync(() -> {
            List<ScreenSession> resolved = sessionManager.resolveSessions(targetStr);
            if (resolved.isEmpty()) return false;

            for (ScreenSession session : resolved) {
                session.reload();
            }
            return true;
        }, 50, true);
    }

    @LuaFunction(
            value = "このスクリーンの表示内容をすべて消去します。",
            en = "Clears all displayed content on this screen.",
            args = {}, rets = {}, isAsync = false
    )
    public void clearScreen(String targetStr) {
        vm.executeInMainThreadSync(() -> {
            for (ScreenSession session : sessionManager.resolveSessions(targetStr)) {
                session.clear();
            }
            return null;
        }, 50, true);
    }

    @LuaFunction(
            value = "スクリーンが遠く離れた際に表示されるフォールバックの単色を設定します。",
            en = "Sets the fallback solid color displayed when the screen is far away.",
            args = {"num:argbColor"}, rets = {}, isAsync = false
    )
    public void setLODColor(String targetStr, int argbColor) {
        vm.executeInMainThreadSync(() -> {
            for (ScreenBlockEntity screen : sessionManager.getScreensFast(targetStr)) {
                screen.setLODColor(argbColor);
            }
            return null;
        }, 50, true);
    }

    @LuaFunction(
            value = "スクリーンとスピーカーをリンクし、音声の出力先を変更します。",
            en = "Links the screen and speakers to change the audio output destination.",
            args = {"table:speakers"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean linkSpeakers(String targetStr, LuaTable speakerTargets) {
        return vm.executeInMainThreadSync(() -> {
            if (vm.hardware == null) return false;

            List<ScreenBlockEntity> screens = sessionManager.getScreensFast(targetStr);
            if (screens.isEmpty()) return false;

            DeviceAPI deviceApi = vm.getOrCreateAPI(DeviceAPI.class, DeviceAPI::new);
            Level level = vm.hardware.getLevel();
            if (level == null) return false;

            List<BlockPos> speakers = new java.util.ArrayList<>();
            for (int i = 1; i <= speakerTargets.length(); i++) {
                String spkTarget = speakerTargets.get(i).tojstring();
                BlockPos spPos = deviceApi.getActionTargetPos(spkTarget);

                if (spPos != null && level.getBlockEntity(spPos) instanceof com.nishiyu.lunex.blockentity.SpeakerBlockEntity) {
                    if (!speakers.contains(spPos)) {
                        speakers.add(spPos);
                    }
                }
            }

            boolean anyUpdated = false;
            for (ScreenBlockEntity sbe : screens) {
                if (speakers.isEmpty() && speakerTargets.length() > 0) {
                    // ★ 修正: 直接代入せず、確実にマスターブロックに伝播する setLinkedSpeakers メソッドを使用
                    sbe.setLinkedSpeakers(new java.util.ArrayList<>());
                } else {
                    // ★ 修正: 同上
                    sbe.setLinkedSpeakers(new java.util.ArrayList<>(speakers));

                    // シャットダウン時にクリアするため、実際のマスター座標を記録
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
            en = "Sends a response back to the client's fetch request.",
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
            en = "Broadcasts an event to all clients of the specified screen.",
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
        sessionManager.onShutdown();
        clearSpeakerLinks();
    }

    @Override
    public void close() {
        sessionManager.onShutdown();
        clearSpeakerLinks();
    }

    private void clearSpeakerLinks() {
        if (vm.hardware == null || vm.hardware.getLevel() == null) return;
        Level level = vm.hardware.getLevel();

        Runnable clearTask = () -> {
            for (BlockPos pos : linkedScreenPositions) {
                if (level.getBlockEntity(pos) instanceof com.nishiyu.lunex.blockentity.ScreenBlockEntity sbe) {
                    // ★ 修正: 直接代入を廃止
                    sbe.setLinkedSpeakers(new java.util.ArrayList<>());
                }
            }
            linkedScreenPositions.clear();
        };

        if (level.getServer() != null && level.getServer().isSameThread()) {
            clearTask.run();
        } else if (level.getServer() != null) {
            level.getServer().execute(clearTask);
        }
    }
}