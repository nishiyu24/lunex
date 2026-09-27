package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.Config;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HttpAPI {
    private final ServerLuaVM vm;

    public HttpAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    @LuaFunction(
            value = "指定したURLにGETリクエストを送信し、結果を取得します。(同期)",
            en = "Sends a GET request to the specified URL and retrieves the result. (Synchronous)",
            args = {"str:url"},
            rets = {"table:response"},
            isAsync = true
    )
    public LuaValue get(String urlStr) {
        return requestSync(urlStr, "GET", null, null);
    }

    @LuaFunction(
            value = "指定したURLにPOSTリクエストを送信し、結果を取得します。(同期)",
            en = "Sends a POST request to the specified URL and retrieves the result. (Synchronous)",
            args = {"str:url", "str:body"},
            rets = {"table:response"},
            isAsync = true
    )
    public LuaValue post(String urlStr, String body) {
        return requestSync(urlStr, "POST", body, null);
    }

    @LuaFunction(
            value = "指定したURLにHTTPリクエストを送信し、レスポンスを取得します。(同期)",
            en = "Sends an HTTP request to the specified URL and retrieves the response. (Synchronous)",
            args = {"str:url", "str:method", "str:body", "table:headers"},
            rets = {"table:response"},
            isAsync = true
    )
    public LuaValue requestSync(String urlStr, String method, String body, LuaTable headers) {
        if (!Config.ENABLE_HTTP_API.get()) {
            Lunex.LOGGER.warn("[Lunex] HTTP API はConfigで無効化されています。");
            return LuaValue.NIL;
        }
        Map<String, String> headerMap = parseHeaders(headers);
        return executeRequest(urlStr, method, body, headerMap);
    }

    @LuaFunction(
            value = "指定したURLに非同期でHTTPリクエストを送信し、完了時に指定したイベントを発火させます。",
            en = "Sends an HTTP request asynchronously and triggers the specified event upon completion.",
            args = {"str:url", "str:method", "str:body", "table:headers", "str:callbackName"}, // callbackEvent -> callbackName に変更
            rets = {"bool:success"},
            isAsync = true,
            execOuts = {"Out", "OnResponse"}
    )
    public boolean requestAsync(String urlStr, String method, String body, LuaTable headers, String callbackName) {
        if (!Config.ENABLE_HTTP_API.get()) return false;
        Map<String, String> headerMap = parseHeaders(headers);

        Thread.startVirtualThread(() -> {
            LuaValue result = executeRequest(urlStr, method, body, headerMap);
            vm.triggerEvent(callbackName, urlStr, result);
        });
        return true;
    }

    @LuaFunction(
            value = "WebSocket接続を非同期で開始します。",
            en = "Starts a WebSocket connection asynchronously.",
            args = {"str:url", "table:headers", "str:callbackName"}, // callbackEvent -> callbackName に変更
            rets = {"bool:success"},
            isAsync = true,
            execOuts = {"Out", "OnMessage", "OnClose"}
    )
    public boolean websocketAsync(String urlStr, LuaTable headers, String callbackName) {
        if (!Config.ENABLE_HTTP_API.get()) return false;
        Map<String, String> headerMap = parseHeaders(headers);

        Thread.startVirtualThread(() -> {
            try {
                URI uri = new URI(urlStr);
                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("ws") && !scheme.equalsIgnoreCase("wss"))) {
                    vm.triggerEvent(callbackName, urlStr, LuaValue.NIL, "Invalid WebSocket protocol. Use ws:// or wss://");
                    return;
                }

                if (!Config.HTTP_ALLOW_LOCAL_IP.get()) {
                    InetAddress address = InetAddress.getByName(uri.getHost());
                    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                        vm.triggerEvent(callbackName, urlStr, LuaValue.NIL, "Access to local/private IP is blocked.");
                        return;
                    }
                }

                HttpClient client = HttpClient.newHttpClient();
                WebSocket.Builder builder = client.newWebSocketBuilder();
                for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }

                WebSocket.Listener listener = new WebSocket.Listener() {
                    final StringBuilder textBuffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        textBuffer.append(data);
                        if (last) {
                            vm.triggerEvent("websocket_message", urlStr, textBuffer.toString());
                            textBuffer.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        vm.triggerEvent("websocket_closed", urlStr, reason);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        vm.triggerEvent("websocket_closed", urlStr, error.getMessage());
                    }
                };

                CompletableFuture<WebSocket> wsFuture = builder.buildAsync(uri, listener);
                WebSocket ws = wsFuture.join();

                LuaTable wsObject = new LuaTable();
                wsObject.set("send", new org.luaj.vm2.lib.OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg) {
                        ws.sendText(arg.tojstring(), true);
                        return LuaValue.NIL;
                    }
                });
                wsObject.set("close", new org.luaj.vm2.lib.ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        ws.sendClose(WebSocket.NORMAL_CLOSURE, "Closed by client");
                        return LuaValue.NIL;
                    }
                });

                vm.triggerEvent(callbackName, urlStr, wsObject);

            } catch (Exception e) {
                vm.triggerEvent(callbackName, urlStr, LuaValue.NIL, e.getMessage());
            }
        });
        return true;
    }

    private Map<String, String> parseHeaders(LuaTable headers) {
        Map<String, String> map = new HashMap<>();
        if (headers != null && !headers.isnil()) {
            for (LuaValue key : headers.keys()) {
                if (key.isstring() && headers.get(key).isstring()) {
                    map.put(key.tojstring(), headers.get(key).tojstring());
                }
            }
        }
        return map;
    }

    private LuaValue executeRequest(String urlStr, String method, String body, Map<String, String> headers) {
        try {
            URL url = new URL(urlStr);
            if (!url.getProtocol().equalsIgnoreCase("http") && !url.getProtocol().equalsIgnoreCase("https")) {
                Lunex.LOGGER.warn("[Lunex] HTTP API: 無効なプロトコルです: " + url.getProtocol());
                return LuaValue.NIL;
            }
            if (!Config.HTTP_ALLOW_LOCAL_IP.get()) {
                InetAddress address = InetAddress.getByName(url.getHost());
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                    Lunex.LOGGER.warn("[Lunex] HTTP API: ローカル/プライベートIPへのアクセスはブロックされています: " + url.getHost());
                    return LuaValue.NIL;
                }
            }

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method != null && !method.isEmpty() ? method.toUpperCase() : "GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if (body != null && !body.isEmpty() && (conn.getRequestMethod().equals("POST") || conn.getRequestMethod().equals("PUT"))) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = body.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            int responseCode = conn.getResponseCode();
            InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                }
            }

            LuaTable result = new LuaTable();
            result.set("code", LuaValue.valueOf(responseCode));
            result.set("body", LuaValue.valueOf(sb.toString()));
            return result;
        } catch (Exception e) {
            Lunex.LOGGER.warn("[Lunex] HTTP API エラー: " + e.getMessage());
            return LuaValue.NIL;
        }
    }
}