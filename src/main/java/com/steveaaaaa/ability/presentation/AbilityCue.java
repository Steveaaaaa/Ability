package com.steveaaaaa.ability.presentation;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A server-authored, asset-agnostic instruction for the client presentation layer. */
public record AbilityCue(
        ResourceLocation abilityId,
        ResourceLocation cueId,
        Action action,
        int sourceEntityId,
        int targetEntityId,
        Vec3 position,
        Vec3 direction,
        int rank,
        long instanceId,
        long randomSeed
) {
    public AbilityCue {
        Objects.requireNonNull(abilityId, "abilityId");
        Objects.requireNonNull(cueId, "cueId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(direction, "direction");
        if (sourceEntityId < -1 || targetEntityId < -1) {
            throw new IllegalArgumentException("Entity ids must be -1 or non-negative");
        }
        if (!finite(position) || !finite(direction)) {
            throw new IllegalArgumentException("Cue vectors must be finite");
        }
        if (rank < 0 || rank > 255) {
            throw new IllegalArgumentException("Cue rank must be between 0 and 255");
        }
    }

    public static AbilityCue pulse(
            ResourceLocation abilityId,
            ResourceLocation cueId,
            int sourceEntityId,
            int targetEntityId,
            Vec3 position,
            Vec3 direction,
            int rank,
            long randomSeed
    ) {
        return new AbilityCue(
                abilityId, cueId, Action.PULSE, sourceEntityId, targetEntityId,
                position, direction, rank, 0L, randomSeed
        );
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    public enum Action {
        START,
        PULSE,
        STOP
    }
}
