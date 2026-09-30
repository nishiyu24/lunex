package com.nishiyu.lunex.program.client;

import com.nishiyu.lunex.program.client.api.ClientDOMAPI;
import com.nishiyu.lunex.program.client.api.ClientFetchAPI;
import com.nishiyu.lunex.program.core.APIRegistry;
import com.nishiyu.lunex.program.core.SuggestionDef;

import java.util.ArrayList;
import java.util.List;

public class ClientAPIRegistry extends APIRegistry {

    @Override
    protected void registerAPIs() {
        registerAPIClass("document", ClientDOMAPI.class);
        registerAPIClass("", ClientFetchAPI.class);

        // -----------------------------------------------------
        // クライアントDOM要素 (element) 用のメソッドサジェスト
        // (DOMの要素は動的に生成されるため、型情報を手動で定義して補完対象にする)
        // -----------------------------------------------------
        suggestions.add(new SuggestionDef("api", "element", "setText", List.of("str:text"), new ArrayList<>(), "テキストを設定します。", "Sets text.", false, false, new ArrayList<>()));
        suggestions.add(new SuggestionDef("api", "element", "setAttribute", List.of("str:attr", "str:value"), new ArrayList<>(), "属性を設定します。", "Sets an attribute.", false, false, new ArrayList<>()));
        suggestions.add(new SuggestionDef("api", "element", "removeAttribute", List.of("str:attr"), new ArrayList<>(), "属性を削除します。", "Removes an attribute.", false, false, new ArrayList<>()));
        suggestions.add(new SuggestionDef("api", "element", "addEventListener", List.of("str:event", "val:action"), new ArrayList<>(), "イベントリスナーを登録します。", "Adds an event listener.", false, false, new ArrayList<>()));
        suggestions.add(new SuggestionDef("api", "element", "getAttribute", List.of("str:attr"), List.of("str:value"), "属性を取得します。", "Gets an attribute.", false, false, new ArrayList<>()));
        suggestions.add(new SuggestionDef("api", "element", "getText", new ArrayList<>(), List.of("str:text"), "テキストを取得します。", "Gets the text.", false, false, new ArrayList<>()));
        suggestions.add(new SuggestionDef("api", "element", "appendElement", List.of("str:tag", "str:newId"), new ArrayList<>(), "子要素を追加します。", "Appends a child element.", false, false, new ArrayList<>()));
        suggestions.add(new SuggestionDef("api", "element", "remove", new ArrayList<>(), new ArrayList<>(), "要素を削除します。", "Removes the element.", false, false, new ArrayList<>()));
        suggestions.add(new SuggestionDef("api", "element", "clear", new ArrayList<>(), new ArrayList<>(), "子要素を全てクリアします。", "Clears all child elements.", false, false, new ArrayList<>()));
        suggestions.add(new SuggestionDef("api", "element", "setSpeed", List.of("num:speed"), new ArrayList<>(), "動画等の再生速度を設定します。", "Sets playback speed.", false, false, new ArrayList<>()));
        suggestions.add(new SuggestionDef("api", "element", "seek", List.of("num:seconds"), new ArrayList<>(), "動画等の再生位置をシークします。", "Seeks playback position.", false, false, new ArrayList<>()));
    }
}