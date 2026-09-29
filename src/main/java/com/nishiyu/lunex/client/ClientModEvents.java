package com.nishiyu.lunex.client;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.Config;
import com.nishiyu.lunex.client.renderer.*;
import com.nishiyu.lunex.client.renderer.blocks.*;
import com.nishiyu.lunex.menu.BioEntity.BioEntitySettingsScreen;
import com.nishiyu.lunex.menu.MachineSettings.MachineSettingsScreen;
import com.nishiyu.lunex.menu.*;
import com.nishiyu.lunex.menu.bioprinter.BioPrinterScreen;
// ★ 追加: SimpleMachineScreen
import com.nishiyu.lunex.menu.SimpleMachineScreen;
import com.nishiyu.lunex.network.LocalWebServer;
import com.nishiyu.lunex.network.LocalWebSocketServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
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
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Lunex.ADVANCED_MACHINE_MENU.get(), AdvancedMachineScreen::new);

        event.register(Lunex.SIMPLE_MACHINE_MENU.get(), SimpleMachineScreen::new);
        event.register(Lunex.UPGRADE_MENU.get(), UpgradeScreen::new);
        event.register(Lunex.MACHINE_SETTINGS_MENU.get(), MachineSettingsScreen::new);
        event.register(Lunex.PROBE_MENU.get(), ProbeScreen::new);
        event.register(Lunex.ROUTER_DASHBOARD_MENU.get(), RouterDashboardScreen::new);
        event.register(Lunex.PORTABLE_SCREEN_MENU.get(), PortableScreenScreen::new);
        event.register(Lunex.PRINTER_MENU.get(), PrinterScreen::new);
        event.register(Lunex.BIO_PRINTER_MENU.get(), BioPrinterScreen::new);
        event.register(Lunex.BIO_MOB_SETTINGS_MENU.get(), BioEntitySettingsScreen::new);
        event.register(Lunex.DATABASE_MENU.get(), DatabaseScreen::new);

        // ★ 追加: MainframeOverviewScreen の登録
        event.register(Lunex.MAINFRAME_OVERVIEW_MENU.get(), MainframeOverviewScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Lunex.TURTLE_BOT_BE.get(), TurtleBotRenderer::new);
        event.registerBlockEntityRenderer(Lunex.SCREEN_BE.get(), ScreenRenderer::new);
        event.registerBlockEntityRenderer(Lunex.ADVANCED_MACHINE_BE.get(), MachineRenderer::new);
        event.registerBlockEntityRenderer(Lunex.PROBE_BE.get(), ProbeRenderer::new);
        event.registerBlockEntityRenderer(Lunex.PRINTER_BE.get(), PrinterBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(Lunex.ROUTER_BE.get(), RouterRenderer::new);
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