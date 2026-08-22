package com.steveaaaaa.ability.data.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record ExperienceSourceDefinition(
        int schemaVersion,
        ResourceLocation skill,
        int baseXp,
        TypedConfig trigger,
        List<TypedConfig> conditions,
        AntiAbuse antiAbuse
) {
    private static final Codec<ExperienceSourceDefinition> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(ExperienceSourceDefinition::schemaVersion),
            ResourceLocation.CODEC.fieldOf("skill").forGetter(ExperienceSourceDefinition::skill),
            Codec.INT.fieldOf("base_xp").forGetter(ExperienceSourceDefinition::baseXp),
            TypedConfig.CODEC.fieldOf("trigger").forGetter(ExperienceSourceDefinition::trigger),
            TypedConfig.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(ExperienceSourceDefinition::conditions),
            AntiAbuse.CODEC.fieldOf("anti_abuse").forGetter(ExperienceSourceDefinition::antiAbuse)
    ).apply(instance, ExperienceSourceDefinition::new));

    public static final Codec<ExperienceSourceDefinition> CODEC = RAW_CODEC.flatXmap(
            ExperienceSourceDefinition::validate,
            ExperienceSourceDefinition::validate
    );

    public ExperienceSourceDefinition {
        conditions = List.copyOf(conditions);
    }

    private static DataResult<ExperienceSourceDefinition> validate(ExperienceSourceDefinition definition) {
        if (definition.schemaVersion != 1) {
            return DataResult.error(() -> "Unsupported experience source schema_version: " + definition.schemaVersion);
        }
        if (definition.baseXp < 0) {
            return DataResult.error(() -> "base_xp must be non-negative");
        }
        if (definition.antiAbuse.targetCooldownTicks < 0 || definition.antiAbuse.dailySoftCap < 0) {
            return DataResult.error(() -> "Anti-abuse cooldown and soft cap must be non-negative");
        }
        if (definition.antiAbuse.xpAfterSoftCapMultiplier < 0.0D
                || definition.antiAbuse.xpAfterSoftCapMultiplier > 1.0D) {
            return DataResult.error(() -> "xp_after_soft_cap_multiplier must be between 0 and 1");
        }
        return DataResult.success(definition);
    }

    public record AntiAbuse(
            boolean rejectPlayerPlaced,
            int targetCooldownTicks,
            int dailySoftCap,
            double xpAfterSoftCapMultiplier
    ) {
        public static final Codec<AntiAbuse> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("reject_player_placed").forGetter(AntiAbuse::rejectPlayerPlaced),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("target_cooldown_ticks")
                        .forGetter(AntiAbuse::targetCooldownTicks),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("daily_soft_cap", 0)
                        .forGetter(AntiAbuse::dailySoftCap),
                Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("xp_after_soft_cap_multiplier", 1.0D)
                        .forGetter(AntiAbuse::xpAfterSoftCapMultiplier)
        ).apply(instance, AntiAbuse::new));
    }
}
