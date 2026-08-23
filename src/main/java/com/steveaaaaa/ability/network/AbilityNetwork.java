package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.ability.AbilityService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AbilityNetwork {
    private static final String NETWORK_VERSION = "1";

    private AbilityNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(
                ClientboundProgressPayload.TYPE,
                ClientboundProgressPayload.STREAM_CODEC,
                (payload, context) -> ClientProgressCache.accept(payload.snapshot())
        );
        registrar.playToServer(
                ServerboundPurchaseAbilityPayload.TYPE,
                ServerboundPurchaseAbilityPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    AbilityService.PurchaseResult result = AbilityService.purchase(player, payload.abilityId());
                    PacketDistributor.sendToPlayer(player, ClientboundPurchaseResultPayload.from(result));
                }
        );
        registrar.playToClient(
                ClientboundPurchaseResultPayload.TYPE,
                ClientboundPurchaseResultPayload.STREAM_CODEC,
                (payload, context) -> ClientProgressCache.acceptPurchaseResult(payload)
        );
    }
}
