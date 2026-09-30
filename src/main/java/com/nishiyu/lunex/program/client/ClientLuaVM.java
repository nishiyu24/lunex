// 上書き: ClientLuaVM.java
package com.nishiyu.lunex.program.client;

import com.nishiyu.lunex.program.core.BaseLuaVM;
import net.minecraft.client.Minecraft;

/**
 * クライアントサイド専用のLuaVM (JSエンジンのような役割)
 * クライアント全体で1つだけインスタンス化されます。
 */
public class ClientLuaVM extends BaseLuaVM {

    public ClientLuaVM() {
        super();
        initBaseSandbox();
        this.isRunning = true; // クライアント用は常に実行可能状態にしておく
    }

    @Override
    public void startCode(String rawCode, String processName) {
        throw new UnsupportedOperationException("ClientLuaVM should be executed via ClientScriptManager.");
    }

    @Override
    public void stopProgram() {
    }

    /**
     * クライアントのメインスレッド上で安全にLuaを実行するためのヘルパー
     * ネットワークスレッドから呼ばれた場合でも安全にメインスレッドに同期させます。
     */
    public void executeLocally(Runnable task) {
        if (!isRunning) return;

        Runnable safeTask = () -> {
            try {
                task.run();
            } catch (Exception e) {
                String cleanError = formatLuaError(e);
                System.err.println("[ClientLuaVM Error] " + cleanError);
            }
        };

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            safeTask.run(); // 初期化前などの安全なフォールバック
        } else if (mc.isSameThread()) {
            safeTask.run(); // 既にメインスレッドなら即実行
        } else {
            mc.execute(safeTask); // ネットワークスレッドからの呼び出しならメインスレッドにスケジュール
        }
    }
}