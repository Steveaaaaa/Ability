package com.steveaaaaa.ability.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record ExperienceLimitState(long day, Map<ResourceLocation, Long> rawXpBySource) {
    public static final ExperienceLimitState EMPTY = new ExperienceLimitState(-1L, Map.of());

    public static final Codec<ExperienceLimitState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("day", -1L).forGetter(ExperienceLimitState::day),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG)
                    .optionalFieldOf("raw_xp_by_source", Map.of())
                    .forGetter(ExperienceLimitState::rawXpBySource)
    ).apply(instance, ExperienceLimitState::new));

    public ExperienceLimitState {
        rawXpBySource = Map.copyOf(rawXpBySource);
    }

    public ExperienceLimitState forDay(long currentDay) {
        return day == currentDay ? this : new ExperienceLimitState(currentDay, Map.of());
    }

    public long rawXp(ResourceLocation sourceId) {
        return Math.max(0L, rawXpBySource.getOrDefault(sourceId, 0L));
    }

    public ExperienceLimitState addRawXp(ResourceLocation sourceId, long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Raw experience amount must be non-negative");
        }
        HashMap<ResourceLocation, Long> updated = new HashMap<>(rawXpBySource);
        long previous = rawXp(sourceId);
        long total;
        try {
            total = Math.addExact(previous, amount);
        } catch (ArithmeticException ignored) {
            total = Long.MAX_VALUE;
        }
        updated.put(sourceId, total);
        return new ExperienceLimitState(day, updated);
    }
}
