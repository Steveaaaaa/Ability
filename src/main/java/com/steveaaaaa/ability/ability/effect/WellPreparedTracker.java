package com.steveaaaaa.ability.ability.effect;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WellPreparedTracker {
    private static final Map<UUID, Long> INVULNERABLE_UNTIL = new ConcurrentHashMap<>();

    private WellPreparedTracker() {
    }

    public static void grant(UUID playerId, long invulnerableUntilGameTime) {
        INVULNERABLE_UNTIL.merge(playerId, invulnerableUntilGameTime, Math::max);
    }

    public static boolean isInvulnerable(UUID playerId, long currentGameTime) {
        Long until = INVULNERABLE_UNTIL.get(playerId);
        if (until == null) {
            return false;
        }
        if (currentGameTime >= until) {
            INVULNERABLE_UNTIL.remove(playerId, until);
            return false;
        }
        return true;
    }

    public static void forget(UUID playerId) {
        INVULNERABLE_UNTIL.remove(playerId);
    }
}
