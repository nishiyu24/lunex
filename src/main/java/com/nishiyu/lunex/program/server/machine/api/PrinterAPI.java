package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.api.mainframe.MainframeConstants;
import com.nishiyu.lunex.api.mainframe.extension.IMainframeAPI;
import com.nishiyu.lunex.blockentity.PrinterBlockEntity;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.luaj.vm2.LuaValue;

public class PrinterAPI implements IMainframeAPI {
    private ServerLuaVM vm;

    public PrinterAPI() {}
    public PrinterAPI(ServerLuaVM vm) { this.vm = vm; }

    @Override
    public String getNamespace() { return MainframeConstants.API_PRINTER; }

    @Override
    public String getRequiredFeature() { return MainframeConstants.FEATURE_PRINTER; }

    @Override
    public Object createInstance(ServerLuaVM vm) {
        return new PrinterAPI(vm);
    }

    private PrinterBlockEntity getPrinter(String targetStr) {
        SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
        if (machine == null || targetStr == null) return null;

        BlockPos targetPos = machine.resolveDevice(targetStr);

        if (targetPos != null && machine.getLevel() != null) {
            BlockEntity be = machine.getLevel().getBlockEntity(targetPos);
            if (be instanceof PrinterBlockEntity printer) {
                return printer;
            }
        }
        return null;
    }

    @LuaFunction(
            value = "ディスクにプログラムを書き込みます。",
            args = {"str:target", "str:scriptName", "str:code"},
            rets = {"bool:success"}
    )
    public LuaValue writeDisc(String targetStr, String scriptName, String code) {
        return vm.executeInMainThreadSync(() -> {
            PrinterBlockEntity printer = getPrinter(targetStr);
            if (printer == null) return LuaValue.FALSE;

            boolean result = printer.createDisc(scriptName, code);
            return LuaValue.valueOf(result);
        });
    }

    @LuaFunction(
            value = "本を印刷します。",
            args = {"str:target", "str:title", "str:content"},
            rets = {"bool:success"}
    )
    public LuaValue printBook(String targetStr, String title, String content) {
        return vm.executeInMainThreadSync(() -> {
            PrinterBlockEntity printer = getPrinter(targetStr);
            if (printer == null) return LuaValue.FALSE;

            return LuaValue.valueOf(printer.printBook(title, content));
        });
    }
}