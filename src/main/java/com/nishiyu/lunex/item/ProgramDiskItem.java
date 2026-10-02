package com.nishiyu.lunex.item;

import com.nishiyu.lunex.api.mainframe.MainframeConstants;
import com.nishiyu.lunex.blockentity.SimpleMachineBlockEntity;
import com.nishiyu.lunex.datagen.AutoLanguageProvider;
import com.nishiyu.lunex.datagen.ITranslationGatherer;
import com.nishiyu.lunex.datagen.Translatable;
import com.nishiyu.lunex.server.ServerProgramData;
import com.nishiyu.lunex.util.WorkspaceManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ProgramDiskItem extends Item {

    @Translatable(en = "Program: %s", ja = "プログラム: %s")
    public static final String TOOLTIP_PROGRAM = "tooltip.lunex.program_disk.program";

    @Translatable(en = "Shift+Right-click to install into Machine or Router", ja = "Shift+右クリックでマシンやルーターにインストール")
    public static final String TOOLTIP_INSTALL_HINT = "tooltip.lunex.program_disk.install_hint";

    @Translatable(en = "Empty Disk", ja = "空のディスク")
    public static final String TOOLTIP_EMPTY_DISK = "tooltip.lunex.program_disk.empty_disk";

    @Translatable(en = "This disk is empty.", ja = "このディスクは空です。")
    public static final String MSG_DISK_EMPTY = "message.lunex.program_disk.disk_empty";

    @Translatable(en = "Installed program '%s' to the machine.", ja = "マシンにプログラム '%s' をインストールしました。")
    public static final String MSG_INSTALLED_MACHINE = "message.lunex.program_disk.installed_machine";

    @Translatable(en = "Overwrote router program with '%s'.", ja = "ルーターのプログラムを '%s' に上書きインストールしました。")
    public static final String MSG_INSTALLED_ROUTER = "message.lunex.program_disk.installed_router";

    @Translatable(en = "Installation failed.", ja = "インストールに失敗しました。")
    public static final String MSG_INSTALL_FAILED = "message.lunex.program_disk.install_failed";

    public ProgramDiskItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.contains("ProgramName")) {
            String name = customData.copyTag().getString("ProgramName");
            tooltipComponents.add(Component.translatable(TOOLTIP_PROGRAM, name).withStyle(ChatFormatting.GREEN));
            tooltipComponents.add(Component.translatable(TOOLTIP_INSTALL_HINT).withStyle(ChatFormatting.GRAY));
        } else {
            tooltipComponents.add(Component.translatable(TOOLTIP_EMPTY_DISK).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide || player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (!customData.contains("ProgramName") || !customData.contains("ProgramCode")) {
            player.displayClientMessage(Component.translatable(MSG_DISK_EMPTY).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        String programName = customData.copyTag().getString("ProgramName");
        String programCode = customData.copyTag().getString("ProgramCode");

        if (!programName.endsWith(".lua")) {
            programName += ".lua";
        }

        BlockEntity be = level.getBlockEntity(pos);
        boolean installed = false;

        // ★修正: SimpleMachineBlockEntity にインストールするよう変更
        if (be instanceof SimpleMachineBlockEntity sm && sm.isMainframeMaster && level instanceof ServerLevel serverLevel) {
            String wsId = sm.getWorkspaceId();
            if (wsId == null || wsId.isEmpty()) {
                wsId = sm.machineId != null ? sm.machineId.toString() : java.util.UUID.randomUUID().toString();
                sm.setWorkspaceId(wsId);
            }

            if (installToFileSystem(serverLevel, wsId, programName, programCode)) {
                ServerProgramData.saveProgram(wsId, programName, programCode);
                installed = true;

                if (sm.activeFeatures.contains(MainframeConstants.FEATURE_ROUTER)) {
                    player.displayClientMessage(Component.translatable(MSG_INSTALLED_ROUTER, programName).withStyle(ChatFormatting.GREEN), true);
                } else {
                    player.displayClientMessage(Component.translatable(MSG_INSTALLED_MACHINE, programName).withStyle(ChatFormatting.GREEN), true);
                }
            }
        }

        if (installed) {
            level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.2F);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        } else {
            player.displayClientMessage(Component.translatable(MSG_INSTALL_FAILED).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
    }

    private boolean installToFileSystem(ServerLevel level, String workspaceId, String fileName, String content) {
        try {
            WorkspaceManager.initializeWorkspace(level.getServer(), workspaceId);
            Path workspaceDir = WorkspaceManager.getBaseDir(level.getServer()).resolve(workspaceId);
            Path filePath = workspaceDir.resolve(fileName);

            Files.writeString(filePath, content);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static class Translations implements ITranslationGatherer {
        @Override
        public void gatherTranslations(AutoLanguageProvider provider, String locale) {
            provider.addTranslation(TOOLTIP_PROGRAM, "Program: %s", "プログラム: %s");
            provider.addTranslation(TOOLTIP_INSTALL_HINT, "Shift+Right-click to install into Machine or Router", "Shift+右クリックでマシンやルーターにインストール");
            provider.addTranslation(TOOLTIP_EMPTY_DISK, "Empty Disk", "空のディスク");
            provider.addTranslation(MSG_DISK_EMPTY, "This disk is empty.", "このディスクは空です。");
            provider.addTranslation(MSG_INSTALLED_MACHINE, "Installed program '%s' to the machine.", "マシンにプログラム '%s' をインストールしました。");
            provider.addTranslation(MSG_INSTALLED_ROUTER, "Overwrote router program with '%s'.", "ルーターのプログラムを '%s' に上書きインストールしました。");
            provider.addTranslation(MSG_INSTALL_FAILED, "Installation failed.", "インストールに失敗しました。");
        }
    }
}