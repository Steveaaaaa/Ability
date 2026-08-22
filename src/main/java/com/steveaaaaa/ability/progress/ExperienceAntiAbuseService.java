package com.steveaaaaa.ability.progress;

import com.steveaaaaa.ability.data.model.ExperienceSourceDefinition;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ExperienceAntiAbuseService {
    private static final long TICKS_PER_DAY = 24_000L;
    private static final Map<ServerPlayer, Map<CooldownKey, Long>> TARGET_COOLDOWNS = new WeakHashMap<>();

    private ExperienceAntiAbuseService() {
    }

    public static boolean isTargetReady(
            ServerPlayer player,
            ResourceLocation sourceId,
            String targetKey,
            int cooldownTicks
    ) {
        if (cooldownTicks <= 0) {
            return true;
        }
        long now = player.level().getGameTime();
        long availableAt = TARGET_COOLDOWNS
                .getOrDefault(player, Map.of())
                .getOrDefault(new CooldownKey(sourceId, targetKey), Long.MIN_VALUE);
        return now >= availableAt;
    }

    public static void recordTarget(
            ServerPlayer player,
            ResourceLocation sourceId,
            String targetKey,
            int cooldownTicks
    ) {
        if (cooldownTicks <= 0) {
            return;
        }
        long now = player.level().getGameTime();
        long availableAt;
        try {
            availableAt = Math.addExact(now, cooldownTicks);
        } catch (ArithmeticException ignored) {
            availableAt = Long.MAX_VALUE;
        }
        TARGET_COOLDOWNS
                .computeIfAbsent(player, ignored -> new HashMap<>())
                .put(new CooldownKey(sourceId, targetKey), availableAt);
    }

    public static long applyDailyLimit(
            ServerPlayer player,
            ResourceLocation sourceId,
            long rawXp,
            ExperienceSourceDefinition.AntiAbuse antiAbuse
    ) {
        if (rawXp <= 0L || antiAbuse.dailySoftCap() <= 0) {
            return rawXp;
        }

        long currentDay = Math.floorDiv(player.level().getGameTime(), TICKS_PER_DAY);
        ExperienceLimitState before = player.getData(ModAttachments.EXPERIENCE_LIMITS).forDay(currentDay);
        long alreadyEarned = before.rawXp(sourceId);
        long awarded = calculateDailyAward(
                rawXp,
                alreadyEarned,
                antiAbuse.dailySoftCap(),
                antiAbuse.xpAfterSoftCapMultiplier()
        );
        player.setData(ModAttachments.EXPERIENCE_LIMITS, before.addRawXp(sourceId, rawXp));
        return awarded;
    }

    public static long calculateDailyAward(long rawXp, long alreadyEarned, long softCap, double multiplier) {
        if (rawXp <= 0L) {
            return 0L;
        }
        if (softCap <= 0L) {
            return rawXp;
        }
        long fullRateRemaining = Math.max(0L, softCap - Math.max(0L, alreadyEarned));
        long fullRateXp = Math.min(rawXp, fullRateRemaining);
        long reducedXp = rawXp - fullRateXp;
        long reducedAward = (long) Math.floor(reducedXp * Math.clamp(multiplier, 0.0D, 1.0D));
        return fullRateXp + reducedAward;
    }

    private record CooldownKey(ResourceLocation sourceId, String targetKey) {
    }
}
