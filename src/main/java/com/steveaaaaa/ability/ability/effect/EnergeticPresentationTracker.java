package com.steveaaaaa.ability.ability.effect;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.presentation.AbilityPresentationService;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Keeps the long-running energetic presentation synchronized without coupling visuals to potion particles. */
public final class EnergeticPresentationTracker {
    public static final ResourceLocation ABILITY_ID = AbilityMod.id("energetic");
    public static final ResourceLocation ACTIVE_CUE = AbilityMod.id("active");
    private static final int REFRESH_DURATION_TICKS = 16;
    private static final long INSTANCE_SALT = 0x454E455247455449L;
    private static final Set<UUID> ACTIVE_PLAYERS = new HashSet<>();

    private EnergeticPresentationTracker() {
    }

    static void sync(ServerPlayer player, boolean active, int rank) {
        UUID playerId = player.getUUID();
        AbilityCue cue = AbilityCue.start(
                ABILITY_ID,
                ACTIVE_CUE,
                player.getId(),
                player.getId(),
                player.position(),
                Vec3.ZERO,
                Math.clamp(rank, 0, 255),
                REFRESH_DURATION_TICKS,
                playerId.getLeastSignificantBits() ^ INSTANCE_SALT,
                playerId.getMostSignificantBits()
        );
        if (active) {
            ACTIVE_PLAYERS.add(playerId);
            AbilityPresentationService.sendTracking(player, cue);
        } else if (ACTIVE_PLAYERS.remove(playerId)) {
            AbilityPresentationService.sendTracking(player, cue.asStop());
        }
    }

    static void forget(UUID playerId) {
        ACTIVE_PLAYERS.remove(playerId);
    }
}
