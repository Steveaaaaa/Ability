package com.steveaaaaa.ability.ability.effect;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.presentation.AbilityPresentationService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class CombatStatusTracker {
    private static final ResourceLocation STUN_PRESENTATION = AbilityMod.id("stun");
    private static final ResourceLocation STUN_ACTIVE_CUE = AbilityMod.id("active");
    private static final Map<MarkKey, MarkState> MARKS = new HashMap<>();
    private static final Map<UUID, StunState> STUNS = new HashMap<>();

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
        StunState updated = STUNS.compute(target.getUUID(), (id, previous) -> {
            if (previous != null && target.level().getGameTime() < previous.expiresAt()) {
                return previous.withExpiresAt(Math.max(previous.expiresAt(), expiresAt));
            }
            return new StunState(
                    expiresAt,
                    target.getYRot(),
                    target.getXRot(),
                    target.yBodyRot,
                    target.yHeadRot
            );
        });
        sendStunStart(target, updated);
    }

    public static boolean isStunned(LivingEntity entity) {
        return activeStun(entity) != null;
    }

    public static boolean maintainStun(LivingEntity entity) {
        StunState state = activeStun(entity);
        if (state == null) {
            return false;
        }
        Vec3 movement = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0D, movement.y, 0.0D);
        entity.setYRot(state.yRot());
        entity.setXRot(state.xRot());
        entity.setYBodyRot(state.bodyYRot());
        entity.setYHeadRot(state.headYRot());
        entity.yRotO = state.yRot();
        entity.xRotO = state.xRot();
        entity.yBodyRotO = state.bodyYRot();
        entity.yHeadRotO = state.headYRot();
        entity.hurtMarked = true;
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        return true;
    }

    public static void forgetTarget(UUID target) {
        MARKS.keySet().removeIf(key -> key.target().equals(target));
        STUNS.remove(target);
    }

    public static void forgetOwner(UUID owner) {
        MARKS.keySet().removeIf(key -> key.owner().equals(owner));
    }

    static void resetForTests() {
        MARKS.clear();
        STUNS.clear();
    }

    private static StunState activeStun(LivingEntity entity) {
        StunState state = STUNS.get(entity.getUUID());
        if (state == null) {
            return null;
        }
        if (entity.level().getGameTime() >= state.expiresAt()) {
            STUNS.remove(entity.getUUID());
            sendStunStop(entity);
            return null;
        }
        return state;
    }

    public static void syncStunTo(ServerPlayer player, LivingEntity target) {
        StunState state = activeStun(target);
        if (state != null) {
            AbilityPresentationService.sendToPlayer(player, stunStartCue(target, state));
        }
    }

    private static void sendStunStart(LivingEntity target, StunState state) {
        if (target.level() instanceof ServerLevel) {
            AbilityPresentationService.sendTracking(target, stunStartCue(target, state));
        }
    }

    private static AbilityCue stunStartCue(LivingEntity target, StunState state) {
        long remaining = Math.max(0L, state.expiresAt() - target.level().getGameTime());
        return stunCue(target, (int) Math.min(AbilityCue.MAX_DURATION_TICKS, remaining));
    }

    private static AbilityCue stunCue(LivingEntity target, int durationTicks) {
        return AbilityCue.start(
                STUN_PRESENTATION,
                STUN_ACTIVE_CUE,
                -1,
                target.getId(),
                target.position().add(0.0D, target.getBbHeight(), 0.0D),
                Vec3.ZERO,
                0,
                durationTicks,
                stunInstanceId(target.getUUID()),
                target.getUUID().getLeastSignificantBits()
        );
    }

    private static void sendStunStop(LivingEntity target) {
        if (target.level() instanceof ServerLevel) {
            AbilityPresentationService.sendTracking(target, stunCue(target, 0).asStop());
        }
    }

    private static long stunInstanceId(UUID targetId) {
        return targetId.getMostSignificantBits() ^ targetId.getLeastSignificantBits();
    }

    public record MarkResult(int remainingCount, boolean triggered) {
    }

    private record MarkKey(UUID owner, UUID target, ResourceLocation markId) {
    }

    private record MarkState(int count, boolean glowing) {
    }

    private record StunState(long expiresAt, float yRot, float xRot, float bodyYRot, float headYRot) {
        private StunState withExpiresAt(long updatedExpiresAt) {
            return new StunState(updatedExpiresAt, yRot, xRot, bodyYRot, headYRot);
        }
    }
}
