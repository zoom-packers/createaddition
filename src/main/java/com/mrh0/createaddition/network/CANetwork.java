package com.mrh0.createaddition.network;

import com.mrh0.createaddition.CreateAddition;
import com.zurrtum.create.neoforge.Platform;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class CANetwork {
    // NeoForge: payloads are registered versioned, so NeoForge refuses a client whose Crafts & Additions differs.
    // This replaces upstream's configuration-phase handshake (CAHandshake). Bump PROTOCOL when a payload changes.
    public static final int PROTOCOL = 1;

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CreateAddition.MODID)
                .versioned(PROTOCOL + "-" + Platform.getModVersion(CreateAddition.MODID));
        registrar.playToServer(ObservePacketPayload.TYPE, ObservePacketPayload.STREAM_CODEC,
                (payload, context) -> ServerPayloadHandler.handleObservePayload(payload, (ServerPlayer) context.player()));
        // Client handlers: CreateAdditionClient#registerPayloadHandlers
        registrar.playToClient(EnergyNetworkPacketPayload.TYPE, EnergyNetworkPacketPayload.STREAM_CODEC);
        registrar.playToClient(TimeRemainingPacketPayload.TYPE, TimeRemainingPacketPayload.STREAM_CODEC);
    }
}
