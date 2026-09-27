package com.nishiyu.lunex.program.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseLuaVM {
    private static final int MAX_TABLE_SIZE = 1000;
    private static final int MAX_DEPTH = 16;
    public final AtomicInteger executionId = new AtomicInteger(0);
    public Globals globals;
    public volatile boolean isRunning = false;
    protected volatile Thread currentExecutingThread;

    public BaseLuaVM() {
        injectStringMetatable();
    }

    public boolean isValidRun(int expectedExecId) {
        return this.isRunning && this.executionId.get() == expectedExecId;
    }

    protected void injectStringMetatable() {
        if (LuaString.s_metatable == null) {
            LuaString.s_metatable = new LuaTable();
        }
        LuaString.s_metatable.set(LuaValue.ADD, new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue arg1, LuaValue arg2) {
                return LuaValue.valueOf(arg1.tojstring() + arg2.tojstring());
            }
        });
    }

    protected void initBaseSandbox() {
        globals = JsePlatform.standardGlobals();

        globals.set("io", LuaValue.NIL);
        globals.set("os", LuaValue.NIL);
        globals.set("luajava", LuaValue.NIL);
        globals.set("package", LuaValue.NIL);
        globals.set("require", LuaValue.NIL);
        globals.set("load", LuaValue.NIL);
        globals.set("loadstring", LuaValue.NIL);
        globals.set("dofile", LuaValue.NIL);
        globals.set("loadfile", LuaValue.NIL);
        globals.set("debug", LuaValue.NIL);
        globals.set("collectgarbage", LuaValue.NIL);
    }

    // ★修正: 拡張子なしのプログラム名にも対応し、入れ子になったエラーを最後まで抽出する
    protected String parseLuaErrorString(String msg) {
        if (msg == null) return "Unknown Error";

        // 不要な改行以降（スタックトレース等）をカット
        if (msg.contains("\n")) {
            msg = msg.substring(0, msg.indexOf('\n')).trim();
        }

        // 先頭につく可能性のあるプレフィックスを除去
        msg = msg.replaceFirst("^(?i)(task error:|error:)\\s*", "");

        String file = "Unknown";
        String line = "?";
        String error = msg;

        // "ファイル名またはチャンク名:行番号:" のパターン（.lua がなくてもマッチするように変更）
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_\\-\\.]+):(\\d+):?\\s*(.*)");

        // 文字列の奥深くにユーザーのプログラムエラーがある場合、それを掘り下げる
        while (true) {
            Matcher m = pattern.matcher(error);
            if (m.find()) {
                file = m.group(1);
                line = m.group(2);
                error = m.group(3).trim();
            } else {
                break; // これ以上 "名前:行:" の形式が見つからなければ終了
            }
        }

        if (!file.equals("Unknown")) {
            return String.format("File: %s\nLine: %s\nError: %s", file, line, error);
        }

        return msg;
    }

    protected String formatLuaError(Throwable e) {
        String msg = null;
        if (e instanceof org.luaj.vm2.LuaError luaError) {
            msg = luaError.getMessage();
            Throwable cause = luaError.getCause();
            while (cause != null) {
                if (cause instanceof java.lang.reflect.InvocationTargetException && cause.getCause() != null) {
                    cause = cause.getCause();
                } else {
                    break;
                }
            }
            if (cause != null && cause.getMessage() != null) {
                msg += " (" + cause.getMessage() + ")";
            }
        } else {
            msg = e.getMessage();
        }
        return parseLuaErrorString(msg);
    }

    public LuaValue loadFromNBT(Tag tag) {
        if (tag instanceof net.minecraft.nbt.IntTag it) return LuaValue.valueOf(it.getAsInt());
        if (tag instanceof net.minecraft.nbt.DoubleTag dt) return LuaValue.valueOf(dt.getAsDouble());
        if (tag instanceof net.minecraft.nbt.StringTag st) return LuaValue.valueOf(st.getAsString());
        if (tag instanceof net.minecraft.nbt.ByteTag bt) return bt.getAsByte() != 0 ? LuaValue.TRUE : LuaValue.FALSE;

        if (tag instanceof net.minecraft.nbt.CompoundTag ct) {
            LuaTable table = new LuaTable();
            for (String key : ct.getAllKeys()) {
                LuaValue loaded = loadFromNBT(ct.get(key));
                if (loaded != LuaValue.NIL) {
                    try {
                        table.set(Integer.parseInt(key), loaded);
                    } catch (NumberFormatException e) {
                        table.set(key, loaded);
                    }
                }
            }
            return table;
        }
        return LuaValue.NIL;
    }

    protected Tag saveToNBT(LuaValue v, int depth) {
        return saveToNBT(v, depth, new HashSet<>());
    }

    private Tag saveToNBT(LuaValue v, int depth, Set<LuaTable> visitedTables) {
        if (depth > MAX_DEPTH) return null;

        if (v.isint()) return net.minecraft.nbt.IntTag.valueOf(v.checkint());
        if (v.isnumber()) return net.minecraft.nbt.DoubleTag.valueOf(v.checkdouble());
        if (v.isstring()) return net.minecraft.nbt.StringTag.valueOf(v.checkjstring());
        if (v.isboolean()) return net.minecraft.nbt.ByteTag.valueOf((byte) (v.checkboolean() ? 1 : 0));

        if (v.istable()) {
            LuaTable table = v.checktable();

            if (visitedTables.contains(table)) {
                return null;
            }
            visitedTables.add(table);

            CompoundTag ct = new CompoundTag();
            LuaValue key = LuaValue.NIL;
            int count = 0;

            while (true) {
                Varargs nextNode = table.next(key);
                key = nextNode.arg1();

                if (key.isnil()) break;

                if (key.isstring() || key.isint()) {
                    LuaValue val = nextNode.arg(2);
                    Tag child = saveToNBT(val, depth + 1, visitedTables);
                    if (child != null) {
                        ct.put(key.tojstring(), child);
                    }
                }

                count++;
                if (count >= MAX_TABLE_SIZE) {
                    ct.putString("_warning", "[Table Size Limit Reached]");
                    break;
                }
            }
            visitedTables.remove(table);
            return ct;
        }
        return null;
    }

    public abstract void startCode(String rawCode, String processName);

    public abstract void stopProgram();
}