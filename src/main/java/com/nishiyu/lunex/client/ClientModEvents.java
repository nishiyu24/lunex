package com.nishiyu.lunex.client;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.Config;
import com.nishiyu.lunex.api.client.MainframeUIRegistry;
import com.nishiyu.lunex.blockentity.DatabaseBlockEntity;
import com.nishiyu.lunex.blockentity.MainframeAdapterBlockEntity;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import com.nishiyu.lunex.blockentity.ScreenBlockEntity;
import com.nishiyu.lunex.client.renderer.MachineFrameRenderer;
import com.nishiyu.lunex.client.renderer.*;
import com.nishiyu.lunex.client.renderer.blocks.*;
import com.nishiyu.lunex.client.ui.*;
import com.nishiyu.lunex.client.ui.modules.CrafterAdapterModule;
import com.nishiyu.lunex.menu.BioEntity.BioEntitySettingsScreen;
import com.nishiyu.lunex.menu.*;
import com.nishiyu.lunex.menu.bioprinter.BioPrinterScreen;
import com.nishiyu.lunex.network.LocalWebServer;
import com.nishiyu.lunex.network.LocalWebSocketServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.NotNull;

@EventBusSubscriber(modid = Lunex.MODID, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        com.nishiyu.lunex.util.SkinLoader.init();
        boolean isWebServerEnabled = Config.ENABLE_WEB_SERVER.get();
        LocalWebSocketServer.start(isWebServerEnabled);
        LocalWebServer.start(isWebServerEnabled);

        event.enqueueWork(() -> {

            // ========================================================
            // ★ UI拡張プロバイダーの登録（条件付きでインスタンスを返す）
            // ========================================================

            // 1. DatabaseBlockEntity 用
            MainframeUIRegistry.registerProvider(be -> {
                if (be instanceof DatabaseBlockEntity) return new DatabaseUIExtension();
                return null;
            });

            // 2. ProbeBlockEntity 用
            MainframeUIRegistry.registerProvider(be -> {
                if (be instanceof ProbeBlockEntity) return new ProbeUIExtension();
                return null;
            });

            // 3. ScreenBlockEntity 用
            MainframeUIRegistry.registerProvider(be -> {
                if (be instanceof ScreenBlockEntity) return new ScreenUIExtension();
                return null;
            });

            // 4. MainframeAdapterBlockEntity 用 (Crafter / 自動作業台)
            // アダプターの場合、内部の originalState を見て Crafter かどうか判定する
            MainframeUIRegistry.registerProvider(be -> {
                if (be instanceof MainframeAdapterBlockEntity adapter) {
                    if (adapter.getOriginalState() != null && adapter.getOriginalState().is(Blocks.CRAFTER)) {
                        return new AdapterUIExtension();
                    }
                    // もし他MODのブロックなど、デフォルトのアダプターUIを返したい場合は
                    // return new DefaultAdapterUIExtension(); のように実装可能
                }
                return null;
            });

            MainframeUIRegistry.registerProvider(new CrafterUIExtension());
            AdapterUIExtension.registerModule(new CrafterAdapterModule());

            // ※他のMOD開発者は、自MODの FMLClientSetupEvent 内で
            // MainframeUIRegistry.registerProvider(be -> { ... }) を呼び出すだけで、
            // LUNEXのアダプターにラップされた自MODブロック専用のUIを追加できます。
        });
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Lunex.SIMPLE_MACHINE_MENU.get(), SimpleMachineScreen::new);
        event.register(Lunex.PROBE_MENU.get(), ProbeScreen::new);
        event.register(Lunex.PORTABLE_SCREEN_MENU.get(), PortableScreenScreen::new);
        event.register(Lunex.PRINTER_MENU.get(), PrinterScreen::new);
        event.register(Lunex.BIO_PRINTER_MENU.get(), BioPrinterScreen::new);
        event.register(Lunex.BIO_MOB_SETTINGS_MENU.get(), BioEntitySettingsScreen::new);
        event.register(Lunex.MAINFRAME_OVERVIEW_MENU.get(), MainframeOverviewScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Lunex.TURTLE_BOT_BE.get(), TurtleBotRenderer::new);

        event.registerBlockEntityRenderer(Lunex.SCREEN_BE.get(), ScreenRenderer::new);
        event.registerBlockEntityRenderer(Lunex.PRINTER_BE.get(), PrinterBlockEntityRenderer::new);

        event.registerBlockEntityRenderer(Lunex.PROBE_BE.get(), MachineFrameRenderer::new);
        event.registerBlockEntityRenderer(Lunex.DATABASE_BE.get(), MachineFrameRenderer::new);
        event.registerBlockEntityRenderer(Lunex.ROUTER_BE.get(), MachineFrameRenderer::new);
        event.registerBlockEntityRenderer(Lunex.MACHINE_FRAME_BE.get(), MachineFrameRenderer::new);
        event.registerBlockEntityRenderer(Lunex.MAINFRAME_ADAPTER_BE.get(), MachineFrameRenderer::new);

        event.registerBlockEntityRenderer(Lunex.SIMPLE_MACHINE_BE.get(), MachineFrameRenderer::new);

        event.registerEntityRenderer(Lunex.CUSTOM_BIO_MOB.get(), CustomBioMobRenderer::new);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public @NotNull BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return new PortableScreenItemRenderer(
                        Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                        Minecraft.getInstance().getEntityModels()
                );
            }
        }, Lunex.PORTABLE_SCREEN.get());
    }
}