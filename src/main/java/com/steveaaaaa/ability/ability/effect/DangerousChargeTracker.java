package com.steveaaaaa.ability.ability.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DangerousChargeTracker {
    private static final Map<UUID, ProjectileState> PROJECTILES = new HashMap<>();

    private DangerousChargeTracker() {
    }

    public static void track(UUID projectile, long expiresAt, double damageMultiplier) {
        if (!Double.isFinite(damageMultiplier) || damageMultiplier < 0.0D) {
            throw new IllegalArgumentException("Damage multiplier must be finite and non-negative");
        }
        PROJECTILES.put(projectile, new ProjectileState(expiresAt, damageMultiplier));
    }

    public static double damageMultiplier(UUID projectile, long gameTime) {
        ProjectileState state = PROJECTILES.get(projectile);
        if (state == null) {
            return 1.0D;
        }
        if (gameTime >= state.expiresAt()) {
            PROJECTILES.remove(projectile);
            return 1.0D;
        }
        return state.damageMultiplier();
    }

    public static void cleanup(long gameTime) {
        PROJECTILES.values().removeIf(state -> gameTime >= state.expiresAt());
    }

    static void resetForTests() {
        PROJECTILES.clear();
    }

    private record ProjectileState(long expiresAt, double damageMultiplier) {
    }
}
