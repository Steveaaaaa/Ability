package com.steveaaaaa.ability.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlayerProgressSynchronizer {
    private PlayerProgressSynchronizer() {
    }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(
                player,
                new ClientboundProgressPayload(PlayerProgressSnapshot.from(player))
        );
    }
}
