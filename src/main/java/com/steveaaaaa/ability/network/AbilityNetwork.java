package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.ability.ActiveAbilityActionService;
import com.steveaaaaa.ability.ability.effect.WorldTravelerEffect;
import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AbilityNetwork {
    private static final String NETWORK_VERSION = "2";

    private AbilityNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(
                ClientboundDodgeAnimationPayload.TYPE,
                ClientboundDodgeAnimationPayload.STREAM_CODEC,
                (payload, context) -> ClientDodgeAnimationQueue.accept(payload)
        );
        registrar.playToClient(
                ClientboundProgressPayload.TYPE,
                ClientboundProgressPayload.STREAM_CODEC,
                (payload, context) -> ClientProgressCache.accept(payload.snapshot())
        );
        registrar.playToClient(
                ClientboundWorldTravelerStatePayload.TYPE,
                ClientboundWorldTravelerStatePayload.STREAM_CODEC,
                (payload, context) -> ClientWorldTravelerCache.accept(payload)
        );
        registrar.playToServer(
                ServerboundWorldTravelerPayload.TYPE,
                ServerboundWorldTravelerPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    switch (payload.action()) {
                        case REQUEST -> WorldTravelerEffect.requestState(player);
                        case SET_FILTER -> WorldTravelerEffect.setFilter(player, payload.slot(), false);
                        case CLEAR_FILTER -> WorldTravelerEffect.setFilter(player, payload.slot(), true);
                        case OPEN_REMOTE -> WorldTravelerEffect.openRemote(player);
                    }
                }
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
        registrar.playToServer(
                ServerboundActivateAbilityPayload.TYPE,
                ServerboundActivateAbilityPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    ActiveAbilityActionService.ActivationResult result = ActiveAbilityActionService.activate(
                            player,
                            payload.abilityId(),
                            payload.input()
                    );
                    if (result == ActiveAbilityActionService.ActivationResult.SUCCESS
                            && payload.abilityId().equals(AbilityMod.id("dodge"))) {
                        var motion = player.getDeltaMovement();
                        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                                player,
                                new ClientboundDodgeAnimationPayload(
                                        player.getId(),
                                        (float) motion.x,
                                        (float) motion.z
                                )
                        );
                    }
                }
        );
        registrar.playToClient(
                ClientboundPurchaseResultPayload.TYPE,
                ClientboundPurchaseResultPayload.STREAM_CODEC,
                (payload, context) -> ClientProgressCache.acceptPurchaseResult(payload)
        );
    }
}
