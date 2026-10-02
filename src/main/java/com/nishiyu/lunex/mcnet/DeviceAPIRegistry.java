package com.nishiyu.lunex.mcnet;

import com.nishiyu.lunex.program.server.machine.api.DeviceAPI;
import org.luaj.vm2.LuaTable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DeviceAPIRegistry {
    private static final Map<String, DeviceBuilder> BUILDERS = new HashMap<>();
    private static boolean initialized = false;

    public static void register(String type, DeviceBuilder builder) {
        BUILDERS.put(type.toLowerCase(), builder);
    }

    public static DeviceBuilder getBuilder(String type) {
        if (!initialized) init();
        return BUILDERS.get(type.toLowerCase());
    }

    public static void init() {
        if (initialized) return;

        register("default", DeviceAPI::buildInventoryWrapper);
        register("screen", DeviceAPI::buildScreenWrapper);
        register("portable_screen", DeviceAPI::buildScreenWrapper);
        register("ar_glasses", DeviceAPI::buildScreenWrapper);
        register("speaker", DeviceAPI::buildSpeakerWrapper);
        register("self", DeviceAPI::buildMachineWrapper);
        register("probe", DeviceAPI::buildProbeWrapper);
        register("printer", DeviceAPI::buildPrinterWrapper);
        register("database", DeviceAPI::buildDatabaseWrapper);
        register("camera", DeviceAPI::buildMachineWrapper);

        initialized = true;
    }

    public static Set<String> getRegisteredTypes() {
        if (!initialized) init();
        return BUILDERS.keySet();
    }

    public static String getDeviceTypeFromBlockName(String blockName) {
        if (!initialized) init();
        String lowerName = blockName.toLowerCase();

        for (String type : BUILDERS.keySet()) {
            if (!type.equals("default") && !type.equals("self") && lowerName.contains(type)) {
                return type;
            }
        }
        return blockName;
    }

    public interface DeviceBuilder {
        void build(DeviceAPI api, LuaTable wrapper, String target);
    }
}