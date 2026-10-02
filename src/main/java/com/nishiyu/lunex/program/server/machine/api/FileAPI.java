package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.Config;
import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.api.mainframe.extension.IMainframeAPI;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.program.server.SystemAPI;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

public class FileAPI implements IMainframeAPI {
    private ServerLuaVM vm;

    public FileAPI() {}
    public FileAPI(ServerLuaVM vm) { this.vm = vm; }

    @Override
    public String getNamespace() { return "fs"; }

    @Override
    public String getRequiredFeature() { return ""; }

    @Override
    public Object createInstance(ServerLuaVM vm) {
        return new FileAPI(vm);
    }

    private Path getWorkspaceDir() {
        String baseDirName = Config.WORKSPACE_DIR.get();
        if (baseDirName == null || baseDirName.isEmpty()) baseDirName = "lunex_programs";
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) this.vm).simpleMachine;

        String wsId = machine.getPersistentData().getString("WorkspaceId");
        if (wsId.isEmpty() || wsId.contains("..") || wsId.contains("/") || wsId.contains("\\")) {
            wsId = java.util.UUID.randomUUID().toString();
            machine.getPersistentData().putString("WorkspaceId", wsId);
            machine.setChanged();
        }
        Path dir = Paths.get(baseDirName, wsId).toAbsolutePath().normalize();
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (Exception ignored) {}
        return dir;
    }

    private Path resolveSafePath(String relativePath) {
        if (relativePath == null || relativePath.contains("..") || relativePath.contains(":")) return null;
        while (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            relativePath = relativePath.substring(1);
        }
        Path baseDir = getWorkspaceDir();
        Path targetPath = baseDir.resolve(relativePath).toAbsolutePath().normalize();
        if (!targetPath.startsWith(baseDir)) return null;
        if (targetPath.getFileName() != null && targetPath.getFileName().toString().equals(".lifespan")) return null;
        return targetPath;
    }

    @LuaFunction(
            value = "ファイル全体を読み込みます。内容がJSON形式の場合は自動的にテーブルに変換して返します。",
            args = {"str:path"},
            rets = {"any:content"},
            isAsync = true
    )
    public LuaValue readFile(String path) {
        Path target = resolveSafePath(path);
        if (target == null || !Files.exists(target) || Files.isDirectory(target)) return LuaValue.NIL;
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            SystemAPI sys = vm.getOrCreateAPI(SystemAPI.class, SystemAPI::new);
            if (sys.isJson(content)) return sys.parseJson(content);
            return LuaValue.valueOf(content);
        } catch (Exception e) {
            return LuaValue.NIL;
        }
    }

    @LuaFunction(
            value = "非同期でファイルを読み込み、完了時に指定したイベントを発火させます。",
            args = {"str:path", "str:callbackName"},
            rets = {"bool:success"},
            isAsync = true,
            execOuts = {"Out", "OnRead"}
    )
    public boolean readFileAsync(String path, String callbackName) {
        Path target = resolveSafePath(path);
        if (target == null || !Files.exists(target) || Files.isDirectory(target)) return false;

        Thread.startVirtualThread(() -> {
            try {
                String content = Files.readString(target, StandardCharsets.UTF_8);
                SystemAPI sys = vm.getOrCreateAPI(SystemAPI.class, SystemAPI::new);
                LuaValue result = sys.isJson(content) ? sys.parseJson(content) : LuaValue.valueOf(content);
                vm.triggerEvent(callbackName, path, result);
            } catch (Exception e) {
                vm.triggerEvent(callbackName, path, LuaValue.NIL, e.getMessage());
            }
        });
        return true;
    }

    @LuaFunction(
            value = "ファイルにデータを保存します。",
            args = {"str:path", "str:content"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean saveFile(String path, String content) {
        Path target = resolveSafePath(path);
        if (target == null || content == null) return false;
        try {
            if (target.getParent() != null && !Files.exists(target.getParent()))
                Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @LuaFunction(
            value = "指定したディレクトリ内のファイルやフォルダの一覧を取得します。",
            args = {"str:path"},
            rets = {"table:files"},
            isAsync = true
    )
    public LuaTable list(String path) {
        Path target = resolveSafePath(path != null && !path.isEmpty() ? path : ".");
        LuaTable result = new LuaTable();
        if (target == null || !Files.isDirectory(target)) return result;

        try (Stream<Path> stream = Files.list(target)) {
            int index = 1;
            for (Path p : stream.toList()) {
                if (!p.getFileName().toString().equals(".lifespan")) {
                    result.set(index++, p.getFileName().toString());
                }
            }
        } catch (Exception e) {
            Lunex.LOGGER.warn("[Lunex] FS API list エラー: " + e.getMessage());
        }
        return result;
    }

    @LuaFunction(
            value = "指定したパスにファイルやフォルダが存在するか確認します。",
            args = {"str:path"},
            rets = {"bool:exists"},
            isAsync = true
    )
    public boolean exists(String path) {
        Path target = resolveSafePath(path);
        return target != null && Files.exists(target);
    }

    @LuaFunction(
            value = "指定したパスがディレクトリ(フォルダ)かどうかを確認します。",
            args = {"str:path"},
            rets = {"bool:isDir"},
            isAsync = true
    )
    public boolean isDir(String path) {
        Path target = resolveSafePath(path);
        return target != null && Files.isDirectory(target);
    }

    @LuaFunction(
            value = "新しいディレクトリを作成します。",
            args = {"str:path"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean makeDir(String path) {
        Path target = resolveSafePath(path);
        if (target == null) return false;
        try {
            Files.createDirectories(target);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @LuaFunction(
            value = "指定したファイルまたはディレクトリを削除します。",
            args = {"str:path"},
            rets = {"bool:success"},
            isAsync = true
    )
    public boolean delete(String path) {
        Path target = resolveSafePath(path);
        if (target == null || target.equals(getWorkspaceDir())) return false;
        try {
            return Files.deleteIfExists(target);
        } catch (Exception e) {
            return false;
        }
    }

    @LuaFunction(
            value = "ファイルを開き、読み書き用のハンドル（テーブル）を返します。modeは 'r'(読込), 'w'(書込), 'a'(追記) を指定します。",
            args = {"str:path", "str:mode"},
            rets = {"table:handle"},
            isAsync = true
    )
    public LuaValue open(String path, String mode) {
        Path target = resolveSafePath(path);
        if (target == null) return LuaValue.NIL;

        try {
            LuaTable handle = new LuaTable();

            if ("r".equals(mode)) {
                if (!Files.exists(target) || Files.isDirectory(target)) return LuaValue.NIL;
                BufferedReader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8);

                handle.set("readLine", new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        try {
                            String line = reader.readLine();
                            return line != null ? LuaValue.valueOf(line) : LuaValue.NIL;
                        } catch (Exception e) { return LuaValue.NIL; }
                    }
                });

                handle.set("readAll", new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        try {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                            return LuaValue.valueOf(sb.toString());
                        } catch (Exception e) { return LuaValue.NIL; }
                    }
                });

                handle.set("close", new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        try { reader.close(); } catch (Exception ignored) {}
                        return LuaValue.NIL;
                    }
                });
                return handle;

            } else if ("w".equals(mode) || "a".equals(mode)) {
                if (target.getParent() != null && !Files.exists(target.getParent()))
                    Files.createDirectories(target.getParent());
                BufferedWriter writer = "a".equals(mode) ? Files.newBufferedWriter(target, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND) : Files.newBufferedWriter(target, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                handle.set("write", new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg) {
                        try {
                            writer.write(arg.tojstring());
                            writer.flush();
                        } catch (Exception ignored) {}
                        return LuaValue.NIL;
                    }
                });

                handle.set("writeLine", new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg) {
                        try {
                            writer.write(arg.tojstring());
                            writer.newLine();
                            writer.flush();
                        } catch (Exception ignored) {}
                        return LuaValue.NIL;
                    }
                });

                handle.set("close", new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        try { writer.close(); } catch (Exception ignored) {}
                        return LuaValue.NIL;
                    }
                });
                return handle;
            }
        } catch (Exception e) {
            Lunex.LOGGER.warn("[Lunex] FS open エラー: " + e.getMessage());
        }
        return LuaValue.NIL;
    }
}