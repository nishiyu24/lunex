package com.nishiyu.lunex.api.mainframe;

import com.nishiyu.lunex.program.server.ServerLuaVM;

/**
 * LUNEXのLua VMに登録されるAPIのひな型インターフェース。
 * アドオンで独自のLua APIを追加する場合は、このインターフェースを実装し、
 * LuaAPIRegistry に登録してください。
 */
public interface IMainframeAPI {
    /**
     * Luaスクリプトから呼び出す際の名前空間 (例: "fs", "net", "inventory")。
     * MainframeConstants で定義された API_xxx 定数の使用を推奨します。
     */
    String getNamespace();

    /**
     * このAPIを使用するために必要なメインフレームコンポーネントの機能名(Feature)。
     * MainframeConstants で定義された FEATURE_xxx を指定します。
     * 空文字 ("") を返した場合は、コンポーネント構成に依存せず常に利用可能になります。
     */
    String getRequiredFeature();

    /**
     * APIインスタンスを初期化して返します（VM生成・再構築ごとに呼ばれます）。
     */
    Object createInstance(ServerLuaVM vm);
}