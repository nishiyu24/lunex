package com.nishiyu.lunex.program.client.api;

import com.nishiyu.lunex.program.client.ClientScriptManager;
import com.nishiyu.lunex.program.core.LuaFunction;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;
import org.luaj.vm2.LuaValue;

public class ClientFetchAPI {
    private final String sessionId;

    public ClientFetchAPI(String sessionId) {
        this.sessionId = sessionId;
    }

    @LuaFunction(
            value = "サーバーの指定エンドポイントにリクエストを送信し、非同期でレスポンスを受け取ります。",
            en = "Sends a request to the specified server endpoint and receives a response asynchronously.",
            args = {"str:endpoint", "val:callback"},
            rets = {}
    )
    public void fetch(String endpoint, LuaValue callback) {
        String requestId = java.util.UUID.randomUUID().toString();
        if (callback.isfunction()) {
            ClientScriptManager.addPendingRequest(requestId, callback);
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("requestId", requestId);
        tag.putString("endpoint", endpoint);
        tag.putString("json", "{}");
        PacketDistributor.sendToServer(
                new com.nishiyu.lunex.network.packet.c2s.AppMessageC2SPacket(sessionId, "fetch_request", tag)
        );
    }
}