package com.nishiyu.lunex.network.packet.s2c;

import com.nishiyu.lunex.Lunex;
import com.nishiyu.lunex.menu.turtle.TurtleSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ItemFileListS2CPacket(List<String> files) implements CustomPacketPayload {
    public static final Type<ItemFileListS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Lunex.MODID, "item_file_list_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemFileListS2CPacket> STREAM_CODEC = StreamCodec.ofMember(ItemFileListS2CPacket::write, ItemFileListS2CPacket::new);

    public ItemFileListS2CPacket(RegistryFriendlyByteBuf buf) {
        this(readList(buf));
    }

    private static List<String> readList(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(buf.readUtf());
        return list;
    }

    public static void handle(ItemFileListS2CPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof TurtleSettingsScreen screen) {
                screen.receiveItemFiles(packet.files());
            }
        });
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(files.size());
        for (String p : files) buf.writeUtf(p);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}