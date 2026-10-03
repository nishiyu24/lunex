package com.nishiyu.lunex.api.mainframe;

import com.nishiyu.lunex.program.server.ServerLuaVM;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lua API (IMainframeAPI) を管理する公開レジストリ。
 * MODの初期化フェーズ (FMLCommonSetupEvent等) でアドオンから登録を行います。
 */
public class LuaAPIRegistry {
    // マルチスレッドでの安全な登録を保証
    private static final Map<String, IMainframeAPI> REGISTRY = new ConcurrentHashMap<>();

    /**
     * 独自のLua APIを登録します。名前空間が重複した場合は上書きされます。
     */
    public static void register(IMainframeAPI api) {
        REGISTRY.put(api.getNamespace(), api);
    }

    public static Map<String, IMainframeAPI> getAll() {
        return REGISTRY;
    }

    public static Object createAPI(String namespace, ServerLuaVM vm) {
        IMainframeAPI api = REGISTRY.get(namespace);
        return api != null ? api.createInstance(vm) : null;
    }
}