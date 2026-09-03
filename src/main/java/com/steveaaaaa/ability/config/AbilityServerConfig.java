package com.steveaaaaa.ability.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AbilityServerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue EXPERIENCE_MULTIPLIER;
    private static final ModConfigSpec.BooleanValue KEEP_SKILL_PROGRESS_ON_DEATH;
    private static final ModConfigSpec.BooleanValue KEEP_LEARNED_ABILITIES_ON_DEATH;
    private static final ModConfigSpec.BooleanValue KEEP_EXPERIENCE_LIMITS_ON_DEATH;
    private static final ModConfigSpec.BooleanValue KEEP_DAILY_ABILITY_STATE_ON_DEATH;
    private static final ModConfigSpec.BooleanValue KEEP_WORLD_TRAVELER_STATE_ON_DEATH;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("progression");
        EXPERIENCE_MULTIPLIER = builder
                .comment("Global multiplier applied to experience awarded by configured experience sources.")
                .defineInRange("experienceMultiplier", 1.0D, 0.0D, 100.0D);
        builder.pop();

        builder.push("death");
        KEEP_SKILL_PROGRESS_ON_DEATH = builder
                .comment("Keep per-skill experience and skill-point ledgers after death.")
                .define("keepSkillProgress", true);
        KEEP_LEARNED_ABILITIES_ON_DEATH = builder
                .comment(
                        "Keep purchased ability ranks after death.",
                        "When false, spent skill points are refunded if skill progress is retained."
                )
                .define("keepLearnedAbilities", true);
        KEEP_EXPERIENCE_LIMITS_ON_DEATH = builder
                .comment("Keep anti-abuse daily experience counters after death.")
                .define("keepExperienceLimits", true);
        KEEP_DAILY_ABILITY_STATE_ON_DEATH = builder
                .comment("Keep once-per-day ability usage, such as Well Prepared, after death.")
                .define("keepDailyAbilityState", true);
        KEEP_WORLD_TRAVELER_STATE_ON_DEATH = builder
                .comment("Keep the World Traveler container binding and filters after death.")
                .define("keepWorldTravelerState", true);
        builder.pop();

        SPEC = builder.build();
    }

    private AbilityServerConfig() {
    }

    public static double experienceMultiplier() {
        return SPEC.isLoaded() ? EXPERIENCE_MULTIPLIER.get() : 1.0D;
    }

    public static boolean keepSkillProgressOnDeath() {
        return !SPEC.isLoaded() || KEEP_SKILL_PROGRESS_ON_DEATH.get();
    }

    public static boolean keepLearnedAbilitiesOnDeath() {
        return !SPEC.isLoaded() || KEEP_LEARNED_ABILITIES_ON_DEATH.get();
    }

    public static boolean keepExperienceLimitsOnDeath() {
        return !SPEC.isLoaded() || KEEP_EXPERIENCE_LIMITS_ON_DEATH.get();
    }

    public static boolean keepDailyAbilityStateOnDeath() {
        return !SPEC.isLoaded() || KEEP_DAILY_ABILITY_STATE_ON_DEATH.get();
    }

    public static boolean keepWorldTravelerStateOnDeath() {
        return !SPEC.isLoaded() || KEEP_WORLD_TRAVELER_STATE_ON_DEATH.get();
    }
}
