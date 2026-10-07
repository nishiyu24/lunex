package com.nishiyu.lunex.client;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.Config;
import com.nishiyu.lunex.api.client.MainframeBottomTabRegistry;
import com.nishiyu.lunex.api.client.MainframeUIRegistry;
import com.nishiyu.lunex.api.client.ui.panel.*;
import com.nishiyu.lunex.api.client.ui.tabs.NetworkStatusBottomTab;
import com.nishiyu.lunex.client.renderer.blocks.PrinterBlockEntityRenderer;
import com.nishiyu.lunex.client.renderer.blocks.TurtleBotRenderer;
import com.nishiyu.lunex.api.client.ui.tabs.SystemStorageBottomTab;
import com.nishiyu.lunex.client.renderer.MachineFrameRenderer;
import com.nishiyu.lunex.client.renderer.*;
import com.nishiyu.lunex.menu.BioEntity.BioEntitySettingsScreen;
import com.nishiyu.lunex.menu.*;
import com.nishiyu.lunex.menu.bioprinter.BioPrinterScreen;
import com.nishiyu.lunex.menu.turtle.TurtleBotScreen;
import com.nishiyu.lunex.menu.turtle.TurtleSettingsScreen;
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

        event.enqueueWork(() -> {
            MainframeBottomTabRegistry.registerGlobal(
                    SystemStorageBottomTab::new,
                    (master, level) -> master.getCore() != null && !master.getCore().resourceCapacities.isEmpty()
            );

            MainframeBottomTabRegistry.registerBlockTab(Lunex.ROUTER_BLOCK.get(), NetworkStatusBottomTab::new);

            MainframeUIRegistry.register(Lunex.PROBE_BLOCK.get(), ProbeUIExtension::new);
            MainframeUIRegistry.register(Lunex.SCREEN_BLOCK.get(), ScreenUIExtension::new);
            MainframeUIRegistry.register(Lunex.DATABASE_BLOCK.get(), DatabaseUIExtension::new);
            MainframeUIRegistry.register(Lunex.SIMPLE_MACHINE.get(), MachineUIExtension::new);
            MainframeUIRegistry.register(net.minecraft.world.level.block.Blocks.CRAFTER, CrafterUIExtension::new);
            MainframeUIRegistry.register(net.minecraft.world.level.block.Blocks.FURNACE, FurnaceUIExtension::new);
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
        event.register(Lunex.TURTLE_BOT_MENU.get(), TurtleBotScreen::new);
        event.register(Lunex.TURTLE_SETTINGS_MENU.get(), TurtleSettingsScreen::new);
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