package com.steveaaaaa.ability.ability.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

public final class PrimerStateTracker {
    private static final Map<UUID, Long> CHARGE_STARTED_AT = new HashMap<>();
    private static final Map<UUID, ProjectileState> PROJECTILES = new HashMap<>();

    private PrimerStateTracker() {
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

    public static void trackProjectile(
            UUID projectile,
            long expiresAt,
            double explosionDamageMultiplier
    ) {
        if (!Double.isFinite(explosionDamageMultiplier) || explosionDamageMultiplier < 0.0D) {
            throw new IllegalArgumentException("Explosion damage multiplier must be finite and non-negative");
        }
        PROJECTILES.put(projectile, new ProjectileState(expiresAt, explosionDamageMultiplier));
    }

    public static double explosionDamageMultiplier(UUID projectile, long gameTime) {
        ProjectileState state = PROJECTILES.get(projectile);
        if (state == null) {
            return 1.0D;
        }
        if (gameTime >= state.expiresAt()) {
            PROJECTILES.remove(projectile);
            return 1.0D;
        }
        return state.explosionDamageMultiplier();
    }

    public static void cleanup(long gameTime) {
        PROJECTILES.values().removeIf(state -> gameTime >= state.expiresAt());
    }

    public static void forgetPlayer(UUID player) {
        CHARGE_STARTED_AT.remove(player);
    }

    static void resetForTests() {
        CHARGE_STARTED_AT.clear();
        PROJECTILES.clear();
    }

    private record ProjectileState(long expiresAt, double explosionDamageMultiplier) {
    }
}
