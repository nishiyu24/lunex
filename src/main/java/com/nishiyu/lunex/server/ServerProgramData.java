package com.nishiyu.lunex.server;

import com.nishiyu.lunex.Lunex;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ServerProgramData {

    public static final Map<String, Map<String, String>> workspaces = new ConcurrentHashMap<>();

    private static Path getWorkspaceDir(String workspaceId) {
        Path dir = FMLPaths.GAMEDIR.get().resolve(com.nishiyu.lunex.Config.WORKSPACE_DIR.get()).resolve(workspaceId);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                Lunex.LOGGER.error("Failed to create workspace directory", e);
            }
        }
        return dir;
    }

    public static List<String> getAvailableWorkspaces() {
        List<String> list = new ArrayList<>();
        Path baseDir = FMLPaths.GAMEDIR.get().resolve(com.nishiyu.lunex.Config.WORKSPACE_DIR.get());
        if (Files.exists(baseDir)) {
            try (Stream<Path> stream = Files.list(baseDir)) {
                stream.filter(Files::isDirectory).forEach(p -> list.add(p.getFileName().toString()));
            } catch (IOException e) {
                Lunex.LOGGER.error("Failed to list workspaces", e);
            }
        }
        for (String key : workspaces.keySet()) {
            if (!list.contains(key)) list.add(key);
        }
        return list;
    }

    public static void load(String workspaceId) {
        if (workspaceId == null || workspaceId.isEmpty()) return;
        Map<String, String> savedPrograms = workspaces.computeIfAbsent(workspaceId, k -> new ConcurrentHashMap<>());
        savedPrograms.clear();

        Path dir = getWorkspaceDir(workspaceId);
        if (!Files.exists(dir)) return;

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.forEach(p -> {
                if (p.equals(dir)) return; // ルート自身は除外
                try {
                    String relative = dir.relativize(p).toString().replace("\\", "/");

                    if (Files.isDirectory(p)) {
                        if (!relative.endsWith("/")) relative += "/";
                        savedPrograms.put(relative, "");
                    } else if (Files.isRegularFile(p)) {
                        // ★修正: .luaや.txtの拡張子をカットせず、そのままファイル名としてメモリに登録する
                        String code = Files.readString(p, StandardCharsets.UTF_8);
                        savedPrograms.put(relative, code);
                    }
                } catch (IOException e) {
                    Lunex.LOGGER.error("Failed to read program file: " + p, e);
                }
            });
        } catch (IOException e) {
            Lunex.LOGGER.error("Failed to load programs for workspace " + workspaceId, e);
        }
    }

    public static void save(String workspaceId) {
        if (workspaceId == null || workspaceId.isEmpty()) return;
        Map<String, String> savedPrograms = workspaces.computeIfAbsent(workspaceId, k -> new ConcurrentHashMap<>());
        Path dir = getWorkspaceDir(workspaceId);

        // クリーンアップ：登録から外れたファイルを削除する
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> !p.equals(dir)).forEach(p -> {
                String relative = dir.relativize(p).toString().replace("\\", "/");

                if (Files.isDirectory(p)) {
                    final String dirRelative = relative.endsWith("/") ? relative : relative + "/";

                    boolean needed = savedPrograms.keySet().stream().anyMatch(k -> k.startsWith(dirRelative));
                    if (!needed && !savedPrograms.containsKey(dirRelative)) {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    }
                } else if (Files.isRegularFile(p)) {
                    // ★修正: 拡張子をカットせずに、マップ内のキーと直接照合して不要なファイルを削除する
                    if (!savedPrograms.containsKey(relative)) {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    }
                }
            });
        } catch (IOException e) {
            Lunex.LOGGER.error("Failed to clean up old programs", e);
        }

        // マップの内容を物理保存する
        for (Map.Entry<String, String> entry : savedPrograms.entrySet()) {
            String fileName = entry.getKey();

            // ディレクトリのみの作成
            if (fileName.endsWith("/")) {
                Path file = dir;
                for (String part : fileName.split("/")) file = file.resolve(part);
                try {
                    Files.createDirectories(file);
                } catch (IOException ignored) {
                }
                continue;
            }

            // ★修正: 許可された5種類の拡張子の場合はそのまま保存し、それ以外の場合は .lua を自動付与する
            String lower = fileName.toLowerCase();
            if (!lower.endsWith(".lua") && !lower.endsWith(".html") && !lower.endsWith(".css") && !lower.endsWith(".json") && !lower.endsWith(".txt")) {
                fileName += ".lua";
            }

            Path file = dir;
            for (String part : fileName.split("/")) {
                file = file.resolve(part);
            }

            try {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                Lunex.LOGGER.error("Failed to save program: " + entry.getKey(), e);
            }
        }
    }

    public static Map<String, String> getPrograms(String workspaceId) {
        return workspaces.computeIfAbsent(workspaceId, k -> new ConcurrentHashMap<>());
    }
}