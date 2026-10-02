package com.nishiyu.lunex.event;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.blockentity.ProbeBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@EventBusSubscriber(modid = "lunex")
public class ModCapabilitiesEvent {

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {

        // BioPrinterBlockEntity の EnergyStorage 登録
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                Lunex.BIO_PRINTER_BE.get(),
                (blockEntity, side) -> blockEntity.energyStorage
        );

        // PrinterBlockEntity の EnergyStorage 登録
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                Lunex.PRINTER_BE.get(),
                (blockEntity, side) -> blockEntity.energyStorage
        );

        // ★追加: ProbeBlockEntity の ItemHandler 登録 (メインフレームへの物理I/O委譲用)
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                Lunex.PROBE_BE.get(),
                ProbeBlockEntity::getItemHandler
        );
    }
}