package com.nishiyu.lunex.program.client;

import com.nishiyu.lunex.program.core.BaseLuaVM;

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
        // クライアント用VMでは、セッションごとの環境構築を ClientScriptManager で行うため、
        // 単一のグローバル実行である startCode は使用しません。
        throw new UnsupportedOperationException("ClientLuaVM should be executed via ClientScriptManager.");
    }

    @Override
    public void stopProgram() {
        // クライアント全体のVMなので停止はしません。
        // セッション単位での破棄は ClientScriptManager.clearSession を使います。
    }

    /**
     * クライアントのメインスレッド上で安全にLuaを実行するためのヘルパー
     * サーバーと違い、遅延（Delay）は一切発生させません。
     */
    public void executeLocally(Runnable task) {
        if (!isRunning) return;
        try {
            // マイクラのクライアントスレッドからの呼び出しを前提とするため、直接実行します。
            task.run();
        } catch (Exception e) {
            // ★変更: クライアント側のコンソールも綺麗なログになるようにフォーマット
            String cleanError = formatLuaError(e);
            System.err.println("[ClientLuaVM Error] " + cleanError);
        }
    }
}