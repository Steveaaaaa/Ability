package com.steveaaaaa.ability.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SkillProgress(long totalXp, int grantedSkillPoints, int spentSkillPoints) {
    public static final SkillProgress EMPTY = new SkillProgress(0L, 0, 0);

    public static final Codec<SkillProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("total_xp", 0L).forGetter(SkillProgress::totalXp),
            Codec.INT.optionalFieldOf("granted_skill_points", 0).forGetter(SkillProgress::grantedSkillPoints),
            Codec.INT.optionalFieldOf("spent_skill_points", 0).forGetter(SkillProgress::spentSkillPoints)
    ).apply(instance, SkillProgress::new));

    public SkillProgress {
        if (totalXp < 0L || grantedSkillPoints < 0 || spentSkillPoints < 0) {
            throw new IllegalArgumentException("Skill progress counters must be non-negative");
        }
    }

    public int availableSkillPoints() {
        return Math.max(0, grantedSkillPoints - spentSkillPoints);
    }

    public SkillProgress add(long amount, int newlyGrantedPoints) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Experience amount must be non-negative");
        }
        if (newlyGrantedPoints < 0) {
            throw new IllegalArgumentException("Granted skill points must be non-negative");
        }
        long result;
        try {
            result = Math.addExact(totalXp, amount);
        } catch (ArithmeticException ignored) {
            result = Long.MAX_VALUE;
        }
        return new SkillProgress(
                result,
                Math.addExact(grantedSkillPoints, newlyGrantedPoints),
                spentSkillPoints
        );
    }

    public SkillProgress spend(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Spent skill points must be non-negative");
        }
        if (amount > availableSkillPoints()) {
            throw new IllegalStateException("Not enough skill points");
        }
        return new SkillProgress(totalXp, grantedSkillPoints, Math.addExact(spentSkillPoints, amount));
    }
}
