package com.steveaaaaa.ability.data.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

public record SkillDefinition(
        int schemaVersion,
        DisplayDefinition display,
        int maxLevel,
        List<Integer> xpToNext,
        int skillPointsPerLevel
) {
    private static final Codec<SkillDefinition> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(SkillDefinition::schemaVersion),
            DisplayDefinition.CODEC.fieldOf("display").forGetter(SkillDefinition::display),
            Codec.intRange(1, 10_000).fieldOf("max_level").forGetter(SkillDefinition::maxLevel),
            Codec.INT.listOf().fieldOf("xp_to_next").forGetter(SkillDefinition::xpToNext),
            Codec.intRange(0, 1_000).fieldOf("skill_points_per_level").forGetter(SkillDefinition::skillPointsPerLevel)
    ).apply(instance, SkillDefinition::new));

    public static final Codec<SkillDefinition> CODEC = RAW_CODEC.flatXmap(
            SkillDefinition::validate,
            SkillDefinition::validate
    );

    public SkillDefinition {
        xpToNext = List.copyOf(xpToNext);
    }

    private static DataResult<SkillDefinition> validate(SkillDefinition definition) {
        if (definition.schemaVersion != 1) {
            return DataResult.error(() -> "Unsupported skill schema_version: " + definition.schemaVersion);
        }
        if (definition.xpToNext.size() != definition.maxLevel) {
            return DataResult.error(() -> "xp_to_next length must equal max_level");
        }
        if (definition.xpToNext.stream().anyMatch(value -> value <= 0)) {
            return DataResult.error(() -> "xp_to_next values must all be positive");
        }
        return DataResult.success(definition);
    }

    public int levelForExperience(long totalExperience) {
        long remaining = Math.max(0L, totalExperience);
        int level = 0;
        for (int required : xpToNext) {
            if (remaining < required) {
                break;
            }
            remaining -= required;
            level++;
        }
        return level;
    }

    public int maximumSkillPoints() {
        return Math.multiplyExact(maxLevel, skillPointsPerLevel);
    }
}
