package com.steveaaaaa.ability.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SkillProgress(long totalXp) {
    public static final SkillProgress EMPTY = new SkillProgress(0L);

    public static final Codec<SkillProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("total_xp", 0L).forGetter(SkillProgress::totalXp)
    ).apply(instance, SkillProgress::new));

    public SkillProgress add(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Experience amount must be non-negative");
        }
        long result;
        try {
            result = Math.addExact(totalXp, amount);
        } catch (ArithmeticException ignored) {
            result = Long.MAX_VALUE;
        }
        return new SkillProgress(result);
    }
}
