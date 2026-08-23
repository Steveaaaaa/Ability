package com.steveaaaaa.ability.ability.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public final class ChargedLeapStateTracker {
    private static final Map<UUID, Long> CHARGE_STARTED_AT = new HashMap<>();
    private static final Map<UUID, LeapState> LEAPS = new HashMap<>();
    private static final Map<UUID, Long> FALL_PROTECTION_UNTIL = new HashMap<>();

    private ChargedLeapStateTracker() {
    }

    public static void beginCharge(UUID player, long gameTime) {
        CHARGE_STARTED_AT.put(player, gameTime);
    }

    public static OptionalLong releaseCharge(UUID player, long gameTime) {
        Long startedAt = CHARGE_STARTED_AT.remove(player);
        if (startedAt == null || gameTime < startedAt) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(gameTime - startedAt);
    }

    public static void armLeap(
            UUID player,
            long expiresAt,
            double impactRadius,
            double damageMultiplier,
            int stunTicks,
            boolean doubleJumpAvailable
    ) {
        LEAPS.put(player, new LeapState(
                expiresAt,
                impactRadius,
                damageMultiplier,
                stunTicks,
                doubleJumpAvailable
        ));
    }

    public static Optional<LeapState> consumeImpact(UUID player, long gameTime) {
        LeapState state = activeLeap(player, gameTime);
        if (state == null) {
            return Optional.empty();
        }
        LEAPS.remove(player);
        FALL_PROTECTION_UNTIL.put(player, state.expiresAt());
        return Optional.of(state);
    }

    public static boolean useDoubleJump(UUID player, long gameTime) {
        LeapState state = activeLeap(player, gameTime);
        if (state == null || !state.doubleJumpAvailable()) {
            return false;
        }
        LEAPS.put(player, new LeapState(
                state.expiresAt(),
                state.impactRadius(),
                state.damageMultiplier(),
                state.stunTicks(),
                false
        ));
        return true;
    }

    public static boolean consumeFallProtection(UUID player, long gameTime) {
        Long expiresAt = FALL_PROTECTION_UNTIL.remove(player);
        return expiresAt != null && gameTime < expiresAt;
    }

    public static void forget(UUID player) {
        CHARGE_STARTED_AT.remove(player);
        LEAPS.remove(player);
        FALL_PROTECTION_UNTIL.remove(player);
    }

    static void resetForTests() {
        CHARGE_STARTED_AT.clear();
        LEAPS.clear();
        FALL_PROTECTION_UNTIL.clear();
    }

    private static LeapState activeLeap(UUID player, long gameTime) {
        LeapState state = LEAPS.get(player);
        if (state == null) {
            return null;
        }
        if (gameTime >= state.expiresAt()) {
            LEAPS.remove(player);
            return null;
        }
        return state;
    }

    public record LeapState(
            long expiresAt,
            double impactRadius,
            double damageMultiplier,
            int stunTicks,
            boolean doubleJumpAvailable
    ) {
    }
}
