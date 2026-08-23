package com.steveaaaaa.ability.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public final class ActiveAbilityRuntime {
    private static final Map<StateKey, Long> COOLDOWNS = new HashMap<>();
    private static final Map<StateKey, ActiveWindow> ACTIVE_WINDOWS = new HashMap<>();

    private ActiveAbilityRuntime() {
    }

    public static boolean tryActivate(
            UUID player,
            ResourceLocation abilityId,
            long gameTime,
            int cooldownTicks,
            int durationTicks,
            double incomingDamageMultiplier
    ) {
        if (cooldownTicks < 0 || durationTicks <= 0) {
            throw new IllegalArgumentException("Cooldown must be non-negative and duration must be positive");
        }
        if (!Double.isFinite(incomingDamageMultiplier)
                || incomingDamageMultiplier < 0.0D
                || incomingDamageMultiplier > 1.0D) {
            throw new IllegalArgumentException("Incoming damage multiplier must be between 0 and 1");
        }
        StateKey key = new StateKey(player, abilityId);
        if (remainingCooldown(player, abilityId, gameTime) > 0L) {
            return false;
        }
        COOLDOWNS.put(key, gameTime + cooldownTicks);
        ACTIVE_WINDOWS.put(key, new ActiveWindow(gameTime + durationTicks, incomingDamageMultiplier));
        return true;
    }

    public static long remainingCooldown(UUID player, ResourceLocation abilityId, long gameTime) {
        StateKey key = new StateKey(player, abilityId);
        Long until = COOLDOWNS.get(key);
        if (until == null) {
            return 0L;
        }
        long remaining = until - gameTime;
        if (remaining <= 0L) {
            COOLDOWNS.remove(key);
            return 0L;
        }
        return remaining;
    }

    public static double incomingDamageMultiplier(UUID player, long gameTime) {
        double multiplier = 1.0D;
        var iterator = ACTIVE_WINDOWS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<StateKey, ActiveWindow> entry = iterator.next();
            ActiveWindow window = entry.getValue();
            if (gameTime >= window.expiresAt()) {
                iterator.remove();
            } else if (entry.getKey().player().equals(player)) {
                multiplier *= window.incomingDamageMultiplier();
            }
        }
        return multiplier;
    }

    public static void forget(UUID player) {
        COOLDOWNS.keySet().removeIf(key -> key.player().equals(player));
        ACTIVE_WINDOWS.keySet().removeIf(key -> key.player().equals(player));
    }

    static void resetForTests() {
        COOLDOWNS.clear();
        ACTIVE_WINDOWS.clear();
    }

    private record StateKey(UUID player, ResourceLocation abilityId) {
    }

    private record ActiveWindow(long expiresAt, double incomingDamageMultiplier) {
    }
}
