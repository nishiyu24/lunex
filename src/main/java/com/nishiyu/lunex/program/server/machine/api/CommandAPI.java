package com.nishiyu.lunex.program.server.machine.api;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.api.mainframe.extension.IMainframeAPI;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.program.server.machine.CoreMachineServerLuaVM;
import com.nishiyu.lunex.program.core.LuaFunction;
import com.nishiyu.lunex.program.server.ServerLuaVM;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;

public class CommandAPI implements IMainframeAPI {
    private ServerLuaVM vm;

    public CommandAPI() {}
    public CommandAPI(ServerLuaVM vm) { this.vm = vm; }

    @Override
    public String getNamespace() { return "commands"; }

    @Override
    public String getRequiredFeature() { return ""; }

    @Override
    public Object createInstance(ServerLuaVM vm) {
        return new CommandAPI(vm);
    }

    @LuaFunction(
            value = "OP権限レベル4でサーバーコマンドを実行し、成否と出力メッセージのリストを返します。",
            args = {"str:command"},
            rets = {"table:result"},
            isAsync = true
    )
    public LuaTable execCommand(String command) {
        return vm.executeInMainThreadSync(() -> {
            LuaTable result = new LuaTable();
            result.set("success", LuaValue.FALSE);

            SimpleMachineBlockEntity machine = ((CoreMachineServerLuaVM) vm).simpleMachine;
            if (machine == null || !machine.getPersistentData().getBoolean("HasCommandUpgrade")) {
                vm.triggerEvent("print", "エラー: このマシンにはコマンドアップグレード(クリエイティブ専用)が搭載されていません。");
                return result;
            }

            if (!(machine.getLevel() instanceof ServerLevel serverLevel)) {
                return result;
            }

            List<String> outputLines = new ArrayList<>();
            CommandSource customSource = new CommandSource() {
                @Override public void sendSystemMessage(Component component) { outputLines.add(component.getString()); }
                @Override public boolean acceptsSuccess() { return true; }
                @Override public boolean acceptsFailure() { return true; }
                @Override public boolean shouldInformAdmins() { return false; }
            };

            CommandSourceStack sourceStack = new CommandSourceStack(
                    customSource,
                    Vec3.atCenterOf(machine.getBlockPos()),
                    Vec2.ZERO,
                    serverLevel,
                    4,
                    "Lunex",
                    Component.literal("Lunex Machine"),
                    serverLevel.getServer(),
                    null
            ).withSuppressedOutput();

            try {
                serverLevel.getServer().getCommands().performPrefixedCommand(sourceStack, command);
                result.set("success", LuaValue.TRUE);
                LuaTable outputTable = new LuaTable();
                for (int i = 0; i < outputLines.size(); i++) {
                    outputTable.set(i + 1, LuaValue.valueOf(outputLines.get(i)));
                }
                result.set("output", outputTable);
            } catch (Exception e) {
                Lunex.LOGGER.warn("[Lunex] Command execution failed: " + e.getMessage());
            }
            return result;
        });
    }
}