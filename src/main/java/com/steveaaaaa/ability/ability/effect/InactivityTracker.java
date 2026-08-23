package com.steveaaaaa.ability.ability.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class InactivityTracker {
    private static final Map<UUID, Long> LAST_ACTIVITY = new HashMap<>();

    private InactivityTracker() {
    }

    public static void recordActivity(ServerPlayer player) {
        recordAt(player.getUUID(), player.level().getGameTime());
    }

    public static long elapsedTicks(ServerPlayer player) {
        return elapsedAt(player.getUUID(), player.level().getGameTime());
    }

    public static void forget(UUID player) {
        LAST_ACTIVITY.remove(player);
    }

    static void recordAt(UUID player, long gameTime) {
        LAST_ACTIVITY.put(player, gameTime);
    }

    static long elapsedAt(UUID player, long gameTime) {
        Long lastActivity = LAST_ACTIVITY.putIfAbsent(player, gameTime);
        if (lastActivity == null || gameTime < lastActivity) {
            LAST_ACTIVITY.put(player, gameTime);
            return 0L;
        }
        return gameTime - lastActivity;
    }

    static void resetForTests() {
        LAST_ACTIVITY.clear();
    }
}
