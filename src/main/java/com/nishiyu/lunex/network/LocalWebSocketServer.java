package com.nishiyu.lunex.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.entity.goals.GoalComponentRegistry;
import com.nishiyu.lunex.network.packet.c2s.AppMessageC2SPacket;
import com.nishiyu.lunex.program.core.APIRegistry;
import com.nishiyu.lunex.server.ServerProgramData;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class LocalWebSocketServer {
    private static final Gson GSON = new Gson();
    private static final Map<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();

    public static volatile String currentLoadedProgramName = "";
    public static volatile String currentLoadedProgramData = "";
    public static volatile String currentWorkspaceId = "default";

    public static volatile String activeVmId = "none";
    public static volatile String activeWorkspaceId = "default";
    public static volatile String activeLockedFile = "";
    public static volatile String activeDefaultFile = "";

    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static OutputStream activeClientOut;

    public static void handleProgramResponse(String name, String data) {
        CompletableFuture<String> future = pendingRequests.remove(name);
        if (future != null) future.complete(data);
    }

    public static void setActiveVm(String vmId, String workspaceId, String lockedFile, String defaultFile) {
        activeVmId = vmId;
        activeWorkspaceId = workspaceId != null ? workspaceId : "default";
        activeLockedFile = lockedFile != null ? lockedFile : "";
        activeDefaultFile = defaultFile != null ? defaultFile : "";

        JsonObject json = new JsonObject();
        json.addProperty("type", "linked_vm");
        json.addProperty("vmId", activeVmId);
        json.addProperty("workspaceId", activeWorkspaceId);
        json.addProperty("lockedFile", activeLockedFile);
        json.addProperty("defaultFile", activeDefaultFile);

        boolean isEntity = activeVmId != null && activeVmId.startsWith("biomob_");
        json.addProperty("isEntity", isEntity);

        broadcast(json);
    }

    public static void sendVmError(String vmId, String errorMessage) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "vm_error");
        json.addProperty("vmId", vmId);
        json.addProperty("error", errorMessage);
        broadcast(json);
    }

    public static void start(boolean isEnabled) {
        if (!isEnabled) {
            Lunex.LOGGER.info("Local WebSocket server is disabled.");
            return;
        }

        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(14321);
                Lunex.LOGGER.info("MineFlow WebSocket Server started on port 14321");

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                }
            } catch (IOException e) {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    Lunex.LOGGER.error("WebSocket Server error", e);
                }
            }
        });
        serverThread.setName("MineFlow-WSServer");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public static void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            if (activeClientOut != null) activeClientOut.close();
        } catch (IOException e) {
            Lunex.LOGGER.error("Failed to stop WebSocket server", e);
        }
    }

    public static void broadcast(JsonObject json) {
        sendWebSocketMessage(json.toString());
    }

    private static void handleClient(Socket socket) {
        new Thread(() -> {
            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {

                activeClientOut = out;

                String wsKey = null;
                StringBuilder headerBuilder = new StringBuilder();
                int b;
                while ((b = in.read()) != -1) {
                    headerBuilder.append((char) b);
                    int len = headerBuilder.length();
                    if (len >= 4 && headerBuilder.substring(len - 4).equals("\r\n\r\n")) {
                        break;
                    }
                }

                String[] lines = headerBuilder.toString().split("\r\n");
                for (String line : lines) {
                    if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                        wsKey = line.substring(18).trim();
                        break;
                    }
                }

                if (wsKey != null) {
                    String acceptKey = Base64.getEncoder().encodeToString(
                            MessageDigest.getInstance("SHA-1").digest((wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8))
                    );
                    String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\n" +
                            "Connection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
                    out.write(response.getBytes(StandardCharsets.UTF_8));
                    out.flush();

                    Lunex.LOGGER.info("MineFlow Editor connected via WebSocket!");

                    JsonObject linkJson = new JsonObject();
                    linkJson.addProperty("type", "linked_vm");
                    linkJson.addProperty("vmId", activeVmId);
                    linkJson.addProperty("workspaceId", activeWorkspaceId);
                    linkJson.addProperty("lockedFile", activeLockedFile);
                    linkJson.addProperty("defaultFile", activeDefaultFile);

                    boolean isEntity = activeVmId != null && activeVmId.startsWith("biomob_");
                    linkJson.addProperty("isEntity", isEntity);

                    broadcast(linkJson);

                    while (true) {
                        String payload = readWebSocketFrame(in);
                        if (payload == null) break;
                        processMessage(payload);
                    }
                }

            } catch (Exception e) {
                Lunex.LOGGER.error("Error in WebSocket connection", e);
            } finally {
                activeClientOut = null;
                Lunex.LOGGER.info("Editor disconnected.");
            }
        }).start();
    }

    private static void processMessage(String payload) {
        try {
            JsonObject req = GSON.fromJson(payload, JsonObject.class);
            String action = req.has("action") ? req.get("action").getAsString() : "";
            int msgId = req.has("msgId") ? req.get("msgId").getAsInt() : -1;

            JsonObject res = new JsonObject();
            if (msgId != -1) res.addProperty("msgId", msgId);

            switch (action) {
                case "get_api_data" -> {
                    res.addProperty("type", "api_data");
                    APIRegistry registry = APIRegistry.getRegistryFor(activeVmId);
                    res.add("suggestions", GSON.toJsonTree(registry.getSuggestions()));

                    JsonArray actions = new JsonArray();
                    for (String key : GoalComponentRegistry.getActionKeys()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("name", key);
                        obj.addProperty("desc", GoalComponentRegistry.getActionDesc(key));
                        obj.addProperty("descEn", GoalComponentRegistry.getActionDescEn(key));
                        JsonArray argsArray = new JsonArray();
                        for (String arg : GoalComponentRegistry.getActionArgs(key)) {
                            argsArray.add(arg);
                        }
                        obj.add("args", argsArray);
                        actions.add(obj);
                    }
                    res.add("actions", actions);

                    JsonArray conditions = new JsonArray();
                    for (String key : GoalComponentRegistry.getConditionKeys()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("name", key);
                        obj.addProperty("desc", GoalComponentRegistry.getConditionDesc(key));
                        obj.addProperty("descEn", GoalComponentRegistry.getConditionDescEn(key));
                        JsonArray argsArray = new JsonArray();
                        for (String arg : GoalComponentRegistry.getConditionArgs(key)) {
                            argsArray.add(arg);
                        }
                        obj.add("args", argsArray);
                        conditions.add(obj);
                    }
                    res.add("conditions", conditions);

                    String mcLang = "en_us";
                    try {
                        mcLang = net.minecraft.client.Minecraft.getInstance().options.languageCode;
                    } catch (Throwable t) {
                    }
                    res.addProperty("mcLang", mcLang);

                    JsonObject registries = new JsonObject();

                    JsonArray entities = new JsonArray();
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().forEach(rl -> entities.add(rl.toString()));
                    registries.add("entities", entities);

                    JsonArray blocks = new JsonArray();
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet().forEach(rl -> blocks.add(rl.toString()));
                    registries.add("blocks", blocks);

                    JsonArray items = new JsonArray();
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().forEach(rl -> items.add(rl.toString()));
                    registries.add("items", items);

                    res.add("registries", registries);

                    sendWebSocketMessage(res.toString());
                }
                case "get_files" -> {
                    res.addProperty("type", "file_list");
                    JsonObject workspacesJson = new JsonObject();
                    for (String ws : ServerProgramData.getAvailableWorkspaces()) {
                        ServerProgramData.load(ws);
                        JsonArray filesArray = new JsonArray();
                        for (String file : ServerProgramData.getPrograms(ws).keySet()) {
                            filesArray.add(file);
                        }
                        workspacesJson.add(ws, filesArray);
                    }
                    res.add("workspaces", workspacesJson);
                    sendWebSocketMessage(res.toString());
                }
                case "load_file" -> {
                    // ★修正: カット処理を完全に廃止し、フロントからの名前をそのまま使用
                    String fullName = req.get("programName").getAsString();

                    String wsId = fullName.contains("/") ? fullName.substring(0, fullName.indexOf("/")) : "default";
                    String progName = fullName.contains("/") ? fullName.substring(fullName.indexOf("/") + 1) : fullName;

                    currentWorkspaceId = wsId;
                    currentLoadedProgramName = progName;

                    CompletableFuture<String> future = new CompletableFuture<>();
                    pendingRequests.put(progName, future);

                    CompoundTag tag = new CompoundTag();
                    tag.putString("programName", fullName);
                    PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "editor_request_program", tag));

                    String programData = "";
                    try {
                        programData = future.get(3, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        pendingRequests.remove(progName);
                    }

                    res.addProperty("type", "file_data");
                    res.addProperty("programName", fullName);
                    res.addProperty("content", programData);
                    sendWebSocketMessage(res.toString());
                }
                case "save_file" -> {
                    // ★修正: カット処理を完全に廃止し、フロントからの名前をそのまま使用
                    String fullName = req.get("programName").getAsString();

                    String wsId = fullName.contains("/") ? fullName.substring(0, fullName.indexOf("/")) : "default";
                    String progName = fullName.contains("/") ? fullName.substring(fullName.indexOf("/") + 1) : fullName;
                    String code = req.get("content").getAsString();

                    currentWorkspaceId = wsId;
                    currentLoadedProgramName = progName;
                    currentLoadedProgramData = code;

                    CompoundTag tag = new CompoundTag();
                    tag.putString("programName", fullName);
                    tag.putString("code", code);
                    PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "sync_program", tag));

                    res.addProperty("type", "saved");
                    res.addProperty("programName", fullName);
                    sendWebSocketMessage(res.toString());
                }
                case "delete_file" -> {
                    // ★修正: カット処理を完全に廃止し、フロントからの名前をそのまま使用
                    String fullName = req.get("programName").getAsString();

                    CompoundTag tag = new CompoundTag();
                    tag.putString("programName", fullName);
                    PacketDistributor.sendToServer(new AppMessageC2SPacket("global", "delete_program", tag));

                    res.addProperty("type", "deleted");
                    res.addProperty("programName", fullName);
                    sendWebSocketMessage(res.toString());
                }
                case "get_link_info" -> {
                    res.addProperty("type", "linked_vm");
                    res.addProperty("vmId", activeVmId);
                    res.addProperty("workspaceId", activeWorkspaceId);
                    res.addProperty("lockedFile", activeLockedFile);
                    res.addProperty("defaultFile", activeDefaultFile);

                    boolean isEntity = activeVmId != null && activeVmId.startsWith("biomob_");
                    res.addProperty("isEntity", isEntity);

                    sendWebSocketMessage(res.toString());
                }
                default -> Lunex.LOGGER.warn("Unknown action from editor: " + action);
            }
        } catch (Exception e) {
            Lunex.LOGGER.error("Error processing message", e);
        }
    }

    private static void sendWebSocketMessage(String message) {
        if (activeClientOut == null) return;
        try {
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            activeClientOut.write(0x81);
            if (payload.length <= 125) {
                activeClientOut.write(payload.length);
            } else if (payload.length <= 65535) {
                activeClientOut.write(126);
                activeClientOut.write((payload.length >> 8) & 0xFF);
                activeClientOut.write(payload.length & 0xFF);
            } else {
                activeClientOut.write(127);
                for (int i = 0; i < 4; i++) activeClientOut.write(0);
                activeClientOut.write((payload.length >> 24) & 0xFF);
                activeClientOut.write((payload.length >> 16) & 0xFF);
                activeClientOut.write((payload.length >> 8) & 0xFF);
                activeClientOut.write(payload.length & 0xFF);
            }
            activeClientOut.write(payload);
            activeClientOut.flush();
        } catch (IOException e) {
            Lunex.LOGGER.error("Failed to send WS message", e);
        }
    }

    private static String readWebSocketFrame(InputStream in) throws IOException {
        int b0 = in.read();
        if (b0 == -1) return null;
        int opcode = b0 & 0x0F;
        if (opcode == 8) return null;

        int b1 = in.read();
        boolean mask = (b1 & 0x80) != 0;
        int payloadLen = b1 & 0x7F;

        if (payloadLen == 126) {
            payloadLen = (in.read() << 8) | in.read();
        } else if (payloadLen == 127) {
            for (int i = 0; i < 4; i++) in.read();
            payloadLen = (in.read() << 24) | (in.read() << 16) | (in.read() << 8) | in.read();
        }

        byte[] maskingKey = new byte[4];
        if (mask) in.read(maskingKey);

        byte[] payload = new byte[payloadLen];
        int read = 0;
        while (read < payloadLen) {
            int r = in.read(payload, read, payloadLen - read);
            if (r == -1) break;
            read += r;
        }

        if (mask) {
            for (int i = 0; i < payloadLen; i++) {
                payload[i] = (byte) (payload[i] ^ maskingKey[i % 4]);
            }
        }
        return new String(payload, StandardCharsets.UTF_8);
    }
}