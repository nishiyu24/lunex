package com.nishiyu.lunex.util;

import com.nishiyu.lunex.Config;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

public class WorkspaceManager {
    private static final long EXPIRATION_DAYS = 30;
    private static final String LIFESPAN_FILE = ".lifespan";

    public static Path getBaseDir(MinecraftServer server) {
        String dirName = Config.WORKSPACE_DIR.get();
        return server.getServerDirectory().resolve(dirName);
    }

    public static boolean requiresInitialization(MinecraftServer server, String workspaceId) {
        if (workspaceId == null || workspaceId.isEmpty()) return false;
        return !Files.exists(getBaseDir(server).resolve(workspaceId));
    }

    public static void initializeWorkspace(MinecraftServer server, String workspaceId) {
        if (workspaceId == null || workspaceId.isEmpty()) return;
        Path wsPath = getBaseDir(server).resolve(workspaceId);
        try {
            if (!Files.exists(wsPath)) {
                Files.createDirectories(wsPath);
            }
            updateAccessTime(server, workspaceId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void updateAccessTime(MinecraftServer server, String workspaceId) {
        if (server == null || workspaceId == null || workspaceId.isEmpty()) return;
        Path wsPath = getBaseDir(server).resolve(workspaceId);

        if (Files.exists(wsPath) && Files.isDirectory(wsPath)) {
            Path lifespanFile = wsPath.resolve(LIFESPAN_FILE);
            try {
                if (!Files.exists(lifespanFile)) {
                    Files.createFile(lifespanFile);
                    try {
                        Files.setAttribute(lifespanFile, "dos:hidden", true);
                    } catch (UnsupportedOperationException | IllegalArgumentException ignored) {
                    }
                }
                Files.setLastModifiedTime(lifespanFile, FileTime.from(Instant.now()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void cleanOldWorkspaces(MinecraftServer server) {
        Path baseDir = getBaseDir(server);
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) return;

        Instant threshold = Instant.now().minus(EXPIRATION_DAYS, ChronoUnit.DAYS);

        try {
            Files.list(baseDir).forEach(workspacePath -> {
                if (Files.isDirectory(workspacePath)) {
                    try {
                        Path lifespanFile = workspacePath.resolve(LIFESPAN_FILE);
                        Instant lastAccess;

                        if (Files.exists(lifespanFile)) {
                            lastAccess = Files.getLastModifiedTime(lifespanFile).toInstant();
                        } else {
                            lastAccess = Files.getLastModifiedTime(workspacePath).toInstant();
                        }

                        if (lastAccess.isBefore(threshold)) {
                            deleteDirectoryRecursively(workspacePath);
                            System.out.println("[Lunex] Deleted expired workspace: " + workspacePath.getFileName());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteWorkspace(MinecraftServer server, String workspaceId) {
        if (server == null || workspaceId == null || workspaceId.isEmpty()) return;
        Path wsPath = getBaseDir(server).resolve(workspaceId);

        if (Files.exists(wsPath) && Files.isDirectory(wsPath)) {
            try {
                deleteDirectoryRecursively(wsPath);
                System.out.println("[Lunex] Manually deleted workspace: " + workspaceId);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ★ 追加: ワークスペース内の特定ファイルを即座に削除する (ルーターの解体時などに使用)
    public static void deleteFileInWorkspace(MinecraftServer server, String workspaceId, String fileName) {
        if (server == null || workspaceId == null || workspaceId.isEmpty() || fileName == null || fileName.isEmpty())
            return;
        Path filePath = getBaseDir(server).resolve(workspaceId).resolve(fileName);

        if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
            try {
                Files.delete(filePath);
                System.out.println("[Lunex] Manually deleted file: " + filePath.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void deleteDirectoryRecursively(Path path) throws IOException {
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}