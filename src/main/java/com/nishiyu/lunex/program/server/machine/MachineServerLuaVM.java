package com.nishiyu.lunex.program.server.machine;

import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.mcnet.DeviceAPIRegistry;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.program.server.machine.api.*;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.HashSet;
import java.util.Set;

public class MachineServerLuaVM extends ServerLuaVM {

    private static final Set<String> MACHINE_SYSTEM_GLOBALS;

    static {
        MACHINE_SYSTEM_GLOBALS = new HashSet<>(ServerLuaVM.BASE_SYSTEM_GLOBALS);
        MACHINE_SYSTEM_GLOBALS.add("Direction");
        MACHINE_SYSTEM_GLOBALS.add("DEVICE");
    }

    public final AdvancedMachineBlockEntity machineEntity;

    public MachineServerLuaVM(AdvancedMachineBlockEntity machineEntity) {
        super(machineEntity);
        this.machineEntity = machineEntity;
    }

    @Override
    protected Set<String> getSystemGlobals() {
        return MACHINE_SYSTEM_GLOBALS;
    }

    @Override
    protected void initSandboxAndAPIs() {
        super.initSandboxAndAPIs();

        String enumDefinition = "Direction = { UP='up', DOWN='down', LEFT='left', RIGHT='right', FRONT='front', BACK='back', NORTH='north', SOUTH='south', EAST='east', WEST='west' }";
        globals.load(enumDefinition).call();

        LuaTable deviceTable = new LuaTable();
        for (String type : DeviceAPIRegistry.getRegisteredTypes()) {
            deviceTable.set(type.toUpperCase(java.util.Locale.ROOT), LuaValue.valueOf(type));
        }
        globals.set("DEVICE", deviceTable);

        registerAPI("machine", getOrCreateAPI(MachineAPI.class, MachineAPI::new));
        registerAPI("printer", getOrCreateAPI(PrinterAPI.class, PrinterAPI::new));
        registerAPI("http", getOrCreateAPI(HttpAPI.class, HttpAPI::new));
        registerAPI("fs", getOrCreateAPI(FileAPI.class, FileAPI::new));
        registerAPI("device", getOrCreateAPI(DeviceAPI.class, DeviceAPI::new));
        registerAPI("rs", getOrCreateAPI(RedPowerAPI.class, RedPowerAPI::new));
        registerAPI("speaker", getOrCreateAPI(SpeakerAPI.class, SpeakerAPI::new));
        registerAPI("commands", getOrCreateAPI(CommandAPI.class, CommandAPI::new));
        registerAPI("screen", getOrCreateAPI(ScreenAPI.class, ScreenAPI::new));
        registerAPI("net", getOrCreateAPI(NetAPI.class, NetAPI::new));
        registerAPI("lan", getOrCreateAPI(LanAPI.class, LanAPI::new));
        registerAPI("storage", getOrCreateAPI(StorageAPI.class, StorageAPI::new));
        registerAPI("inventory", getOrCreateAPI(InventoryAPI.class, InventoryAPI::new));
        registerAPI("router", getOrCreateAPI(RouterAPI.class, RouterAPI::new));
    }
}