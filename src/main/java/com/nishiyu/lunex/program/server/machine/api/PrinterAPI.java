package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.blockentity.PrinterBlockEntity;
import com.nishiyu.lunex.blockentity.AdvancedMachineBlockEntity;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import com.nishiyu.lunex.util.TargetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.luaj.vm2.LuaValue;

public class PrinterAPI {
    private final ServerLuaVM vm;

    public PrinterAPI(ServerLuaVM vm) {
        this.vm = vm;
    }

    private PrinterBlockEntity getPrinter(String targetStr) {
        if (!(vm.hardware instanceof AdvancedMachineBlockEntity machine)) return null;

        BlockPos targetPos = machine.resolveDevice(targetStr);
        if (targetPos == null) {
            Direction dir = TargetUtil.getDirectionRelative(targetStr, machine.getBlockState());
            if (dir != null) {
                targetPos = machine.getBlockPos().relative(dir);
            }
        }

        if (targetPos != null) {
            Level level = machine.getLevel();
            if (level != null) {
                BlockEntity be = level.getBlockEntity(targetPos);
                if (be instanceof PrinterBlockEntity printer) {
                    return printer;
                }
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