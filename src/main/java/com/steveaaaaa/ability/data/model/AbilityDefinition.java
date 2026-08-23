package com.steveaaaaa.ability.data.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record AbilityDefinition(
        int schemaVersion,
        ResourceLocation skill,
        DisplayDefinition display,
        Purchase purchase,
        Ranks ranks,
        TypedConfig effect
) {
    private static final Codec<AbilityDefinition> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(AbilityDefinition::schemaVersion),
            ResourceLocation.CODEC.fieldOf("skill").forGetter(AbilityDefinition::skill),
            DisplayDefinition.CODEC.fieldOf("display").forGetter(AbilityDefinition::display),
            Purchase.CODEC.fieldOf("purchase").forGetter(AbilityDefinition::purchase),
            Ranks.CODEC.fieldOf("ranks").forGetter(AbilityDefinition::ranks),
            TypedConfig.CODEC.fieldOf("effect").forGetter(AbilityDefinition::effect)
    ).apply(instance, AbilityDefinition::new));

    public static final Codec<AbilityDefinition> CODEC = RAW_CODEC.flatXmap(
            AbilityDefinition::validate,
            AbilityDefinition::validate
    );

    private static DataResult<AbilityDefinition> validate(AbilityDefinition definition) {
        if (definition.schemaVersion != 1) {
            return DataResult.error(() -> "Unsupported ability schema_version: " + definition.schemaVersion);
        }
        return definition.ranks.validate().flatMap(ignored -> {
            if (definition.ranks.skillPointCosts().getFirst() != definition.purchase.skillPoints()) {
                return DataResult.error(() -> "The first rank skill point cost must equal purchase.skill_points");
            }
            return DataResult.success(definition);
        });
    }

    public record Purchase(int skillLevel, int skillPoints, List<TypedConfig> requirements) {
        public static final Codec<Purchase> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 10_000).fieldOf("skill_level").forGetter(Purchase::skillLevel),
                Codec.intRange(0, 1_000_000).fieldOf("skill_points").forGetter(Purchase::skillPoints),
                TypedConfig.CODEC.listOf().optionalFieldOf("requirements", List.of()).forGetter(Purchase::requirements)
        ).apply(instance, Purchase::new));

        public Purchase {
            requirements = List.copyOf(requirements);
        }
    }

    public record Ranks(
            List<Integer> unlockSkillLevels,
            List<Integer> skillPointCosts,
            List<Dynamic<?>> values
    ) {
        public static final Codec<Ranks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.listOf().fieldOf("unlock_skill_levels").forGetter(Ranks::unlockSkillLevels),
                Codec.intRange(0, 1_000_000).listOf().fieldOf("skill_point_costs").forGetter(Ranks::skillPointCosts),
                Codec.PASSTHROUGH.listOf().fieldOf("values").forGetter(Ranks::values)
        ).apply(instance, Ranks::new));

        public Ranks {
            unlockSkillLevels = List.copyOf(unlockSkillLevels);
            skillPointCosts = List.copyOf(skillPointCosts);
            values = List.copyOf(values);
        }

        private DataResult<Ranks> validate() {
            if (unlockSkillLevels.isEmpty()) {
                return DataResult.error(() -> "Ability must define at least one rank");
            }
            if (unlockSkillLevels.size() != values.size()) {
                return DataResult.error(() -> "unlock_skill_levels and values must have equal lengths");
            }
            if (skillPointCosts.size() != values.size()) {
                return DataResult.error(() -> "skill_point_costs and values must have equal lengths");
            }
            for (int index = 1; index < unlockSkillLevels.size(); index++) {
                if (unlockSkillLevels.get(index) < unlockSkillLevels.get(index - 1)) {
                    return DataResult.error(() -> "unlock_skill_levels must be non-decreasing");
                }
            }
            return DataResult.success(this);
        }

        public int rankForSkillLevel(int skillLevel) {
            int rank = 0;
            for (int unlockLevel : unlockSkillLevels) {
                if (skillLevel < unlockLevel) {
                    break;
                }
                rank++;
            }
            return rank;
        }
    }
}
