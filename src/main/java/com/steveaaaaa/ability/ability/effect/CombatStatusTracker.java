package com.steveaaaaa.ability.ability.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public final class CombatStatusTracker {
    private static final Map<MarkKey, MarkState> MARKS = new HashMap<>();
    private static final Map<UUID, Long> STUNNED_UNTIL = new HashMap<>();

    private CombatStatusTracker() {
    }

    public static MarkResult addMark(
            UUID owner,
            UUID target,
            ResourceLocation markId,
            int amount,
            int threshold
    ) {
        if (amount <= 0 || threshold <= 0) {
            throw new IllegalArgumentException("Mark amount and threshold must be positive");
        }
        MarkKey key = new MarkKey(owner, target, markId);
        MarkState previous = MARKS.getOrDefault(key, new MarkState(0, false));
        int count = Math.min(threshold, previous.count() + amount);
        if (count >= threshold) {
            MARKS.remove(key);
            return new MarkResult(0, true);
        }
        MARKS.put(key, new MarkState(count, previous.glowing()));
        return new MarkResult(count, false);
    }

    public static void setMark(UUID owner, UUID target, ResourceLocation markId, boolean glowing) {
        MARKS.put(new MarkKey(owner, target, markId), new MarkState(1, glowing));
    }

    public static boolean hasMark(UUID owner, UUID target, ResourceLocation markId) {
        return MARKS.containsKey(new MarkKey(owner, target, markId));
    }

    public static boolean consumeMark(UUID owner, UUID target, ResourceLocation markId) {
        return MARKS.remove(new MarkKey(owner, target, markId)) != null;
    }

    public static boolean hasGlowingMark(UUID target) {
        return MARKS.entrySet().stream().anyMatch(entry ->
                entry.getKey().target().equals(target) && entry.getValue().glowing());
    }

    public static void stun(LivingEntity target, int durationTicks) {
        if (durationTicks <= 0) {
            return;
        }
        long expiresAt = target.level().getGameTime() + durationTicks;
        STUNNED_UNTIL.merge(target.getUUID(), expiresAt, Math::max);
    }

    public static boolean isStunned(LivingEntity entity) {
        Long expiresAt = STUNNED_UNTIL.get(entity.getUUID());
        if (expiresAt == null) {
            return false;
        }
        if (entity.level().getGameTime() >= expiresAt) {
            STUNNED_UNTIL.remove(entity.getUUID());
            return false;
        }
        return true;
    }

    public static void forgetTarget(UUID target) {
        MARKS.keySet().removeIf(key -> key.target().equals(target));
        STUNNED_UNTIL.remove(target);
    }

    public static void forgetOwner(UUID owner) {
        MARKS.keySet().removeIf(key -> key.owner().equals(owner));
    }

    static void resetForTests() {
        MARKS.clear();
        STUNNED_UNTIL.clear();
    }

    public record MarkResult(int remainingCount, boolean triggered) {
    }

    private record MarkKey(UUID owner, UUID target, ResourceLocation markId) {
    }

    private record MarkState(int count, boolean glowing) {
    }
}
