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
        int durationTicks,
        long instanceId,
        long randomSeed
) {
    public static final int USE_DEFINITION_DURATION = -1;
    public static final int MAX_DURATION_TICKS = 20 * 60 * 10;

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
        if (durationTicks < USE_DEFINITION_DURATION || durationTicks > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("Cue duration must be -1 or between 0 and " + MAX_DURATION_TICKS);
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
                position, direction, rank, USE_DEFINITION_DURATION, 0L, randomSeed
        );
    }

    public static AbilityCue start(
            ResourceLocation abilityId,
            ResourceLocation cueId,
            int sourceEntityId,
            int targetEntityId,
            Vec3 position,
            Vec3 direction,
            int rank,
            int durationTicks,
            long instanceId,
            long randomSeed
    ) {
        return new AbilityCue(
                abilityId, cueId, Action.START, sourceEntityId, targetEntityId,
                position, direction, rank, durationTicks, instanceId, randomSeed
        );
    }

    public AbilityCue asStop() {
        return new AbilityCue(
                abilityId, cueId, Action.STOP, sourceEntityId, targetEntityId,
                position, direction, rank, 0, instanceId, randomSeed
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
