package com.steveaaaaa.ability.presentation;

import com.steveaaaaa.ability.network.ClientboundAbilityCuePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side entry point used by abilities; it deliberately contains no visual asset choices. */
public final class AbilityPresentationService {
    private AbilityPresentationService() {
    }

    public static void sendToPlayer(ServerPlayer player, AbilityCue cue) {
        PacketDistributor.sendToPlayer(player, new ClientboundAbilityCuePayload(cue));
    }

    public static void sendTracking(Entity source, AbilityCue cue) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(source, new ClientboundAbilityCuePayload(cue));
    }
}
