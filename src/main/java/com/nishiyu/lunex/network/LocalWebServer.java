package com.nishiyu.lunex.network;

import com.nishiyu.lunex.Lunex;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class LocalWebServer {
    // WebSocket(14321)とは別のポートを使用します
    private static final int PORT = 14320;
    private static HttpServer server;

    public static void start(boolean isEnabled) {
        if (!isEnabled) {
            Lunex.LOGGER.info("Local Web Server is disabled by config.");
            return;
        }
        try {
            // ポート14320でHTTPサーバーを起動
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/", new ResourceFileHandler());
            server.setExecutor(null); // デフォルトのExecutor
            server.start();
            Lunex.LOGGER.info("MineFlow Web Server started on http://localhost:" + PORT);
        } catch (IOException e) {
            Lunex.LOGGER.error("Failed to start Web Server", e);
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    static class ResourceFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String uriPath = exchange.getRequestURI().getPath();

            // ★ 変更箇所：ルートアクセス時は index.html ではなく machine.html にルーティング
            if (uriPath == null || uriPath.equals("/")) {
                uriPath = "/machine.html";
            }

            // ディレクトリトラバーサル対策
            if (uriPath.contains("..")) {
                sendResponse(exchange, 403, "403 Forbidden", "text/plain");
                return;
            }

            // src/main/resources/web 配下のリソースを読み込む
            String resourcePath = "/web" + uriPath;
            try (InputStream is = Lunex.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    sendResponse(exchange, 404, "404 Not Found: " + uriPath, "text/plain");
                    return;
                }

                // 拡張子から Content-Type を判定
                String contentType = "application/octet-stream";
                if (uriPath.endsWith(".html")) contentType = "text/html; charset=UTF-8";
                else if (uriPath.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
                else if (uriPath.endsWith(".css")) contentType = "text/css; charset=UTF-8";
                else if (uriPath.endsWith(".json")) contentType = "application/json; charset=UTF-8";
                else if (uriPath.endsWith(".svg")) contentType = "image/svg+xml";
                else if (uriPath.endsWith(".png")) contentType = "image/png";

                // InputStreamからデータを読み取る
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                byte[] bytes = baos.toByteArray();

                // ファイルをレスポンスとして返す
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                Lunex.LOGGER.error("Error serving web resource: " + resourcePath, e);
                sendResponse(exchange, 500, "500 Internal Server Error", "text/plain");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}