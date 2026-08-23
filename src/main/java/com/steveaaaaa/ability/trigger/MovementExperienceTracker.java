package com.steveaaaaa.ability.trigger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class MovementExperienceTracker {
    private static final int WINDOW_TICKS = 20;
    private static final double MAX_DISTANCE_PER_TICK = 16.0D;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private MovementExperienceTracker() {
    }

    public static Optional<ExperienceContext.Movement> update(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || player.isPassenger()) {
            forget(player);
            return Optional.empty();
        }
        ExperienceContext.MovementMode mode = player.isFallFlying()
                ? ExperienceContext.MovementMode.ELYTRA
                : player.isSwimming()
                        ? ExperienceContext.MovementMode.SWIMMING
                        : ExperienceContext.MovementMode.ON_FOOT;
        Optional<Sample> sample = update(
                player.getUUID(),
                level.dimension(),
                player.position(),
                mode
        );
        return sample.map(value -> new ExperienceContext.Movement(
                player,
                level,
                value.distance(),
                value.mode(),
                level.getGameTime() / WINDOW_TICKS
        ));
    }

    static Optional<Sample> update(
            UUID playerId,
            ResourceKey<Level> dimension,
            Vec3 position,
            ExperienceContext.MovementMode mode
    ) {
        State previous = STATES.get(playerId);
        if (previous == null || !previous.dimension().equals(dimension) || previous.mode() != mode) {
            STATES.put(playerId, new State(dimension, position, mode, 0.0D, 0));
            return Optional.empty();
        }

        double step = position.subtract(previous.position()).horizontalDistance();
        double accumulated = previous.accumulatedDistance();
        if (Double.isFinite(step) && step <= MAX_DISTANCE_PER_TICK) {
            accumulated += step;
        }
        int ticks = previous.ticks() + 1;
        if (ticks < WINDOW_TICKS) {
            STATES.put(playerId, new State(dimension, position, mode, accumulated, ticks));
            return Optional.empty();
        }

        STATES.put(playerId, new State(dimension, position, mode, 0.0D, 0));
        return accumulated > 0.0D
                ? Optional.of(new Sample(accumulated, mode))
                : Optional.empty();
    }

    public static void forget(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    static void clear() {
        STATES.clear();
    }

    record Sample(double distance, ExperienceContext.MovementMode mode) {
    }

    private record State(
            ResourceKey<Level> dimension,
            Vec3 position,
            ExperienceContext.MovementMode mode,
            double accumulatedDistance,
            int ticks
    ) {
    }
}
