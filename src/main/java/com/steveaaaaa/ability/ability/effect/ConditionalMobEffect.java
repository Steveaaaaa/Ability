package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public final class ConditionalMobEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("conditional_mob_effect");
    private static final Pattern VALUE_KEY = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private ConditionalMobEffect() {
    }

    public static void process(ServerPlayer player) {
        Registry<AbilityDefinition> abilities = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        boolean energeticActive = false;
        int energeticRank = 0;
        List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> sorted = abilities.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location()))
                .toList();
        for (Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition> entry : sorted) {
            ResourceLocation abilityId = entry.getKey().location();
            try {
                List<CompositeEffect.ComponentView> components =
                        CompositeEffect.componentsOfType(entry.getValue(), TYPE);
                if (components.isEmpty()) {
                    continue;
                }
                Optional<AbilityService.ActiveAbility> active = AbilityService.active(player, abilityId);
                if (active.isEmpty()) {
                    continue;
                }
                for (CompositeEffect.ComponentView component : components) {
                    AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active.get(), component);
                    Config config = parse(Config.CODEC, component.config(), "effect.config");
                    RankValues values = mergeRanks(projected);
                    if (matches(player, config, values)) {
                        applyEffects(player, config.effects());
                        if (abilityId.equals(EnergeticPresentationTracker.ABILITY_ID)) {
                            energeticActive = true;
                            energeticRank = Math.max(energeticRank, projected.rank());
                        }
                    }
                }
            } catch (RuntimeException exception) {
                logInvalidOnce(abilityId, exception.getMessage());
            }
        }
        EnergeticPresentationTracker.sync(player, energeticActive, energeticRank);
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> merged = new LinkedHashMap<>(earlier.values());
        merged.putAll(later.values());
        return new RankValues(merged);
    }

    static boolean isFoodTotalAbove(int foodLevel, float saturationLevel, double threshold) {
        return foodLevel + saturationLevel > threshold;
    }

    static boolean isOutsideRange(double value, double lowerBound, double upperBound) {
        return value < lowerBound || value > upperBound;
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        Config config;
        try {
            config = parse(Config.CODEC, definition.effect().config(), "effect.config");
        } catch (IllegalArgumentException exception) {
            return List.of(exception.getMessage());
        }

        for (int index = 0; index < config.effects().size(); index++) {
            EffectConfig effect = config.effects().get(index);
            if (BuiltInRegistries.MOB_EFFECT.getHolder(effect.effect()).isEmpty()) {
                errors.add("effect.config.effects[" + index + "].effect: unknown mob effect " + effect.effect());
            }
        }

        Set<String> configuredKeys = config.conditions().stream()
                .flatMap(condition -> condition.valueKeys().stream())
                .collect(Collectors.toSet());
        RankValues merged = new RankValues(Map.of());
        for (int index = 0; index < definition.ranks().values().size(); index++) {
            int rankIndex = index;
            try {
                RankValues current = parse(
                        RankValues.CODEC,
                        definition.ranks().values().get(index),
                        "ranks.values[" + index + "]"
                );
                current.values().keySet().stream()
                        .filter(key -> !configuredKeys.contains(key))
                        .forEach(key -> errors.add(
                                "ranks.values[" + rankIndex + "]." + key + ": no condition uses this value key"
                        ));
                merged = merge(merged, current);
                for (String key : configuredKeys) {
                    if (!merged.values().containsKey(key)) {
                        errors.add("ranks.values[" + index + "]: missing condition value key " + key);
                    }
                }
                validateResolvedConditions(config.conditions(), merged, index, errors);
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
            }
        }
        return List.copyOf(errors);
    }

    private static RankValues mergeRanks(AbilityService.ActiveAbility active) {
        RankValues merged = new RankValues(Map.of());
        for (int index = 0; index < active.unlockedRankValues().size(); index++) {
            RankValues current = parse(
                    RankValues.CODEC,
                    active.unlockedRankValues().get(index),
                    "ranks.values[" + index + "]"
            );
            merged = merge(merged, current);
        }
        return merged;
    }

    private static boolean matches(ServerPlayer player, Config config, RankValues values) {
        return switch (config.match()) {
            case ALL -> config.conditions().stream().allMatch(condition -> matches(player, condition, values));
            case ANY -> config.conditions().stream().anyMatch(condition -> matches(player, condition, values));
        };
    }

    private static boolean matches(ServerPlayer player, ConditionConfig condition, RankValues values) {
        return switch (condition.type()) {
            case FOOD_TOTAL_ABOVE -> isFoodTotalAbove(
                    player.getFoodData().getFoodLevel(),
                    player.getFoodData().getSaturationLevel(),
                    requiredValue(values, condition.valueKey(), "value_key")
            );
            case Y_OUTSIDE_RANGE -> isOutsideRange(
                    player.getY(),
                    requiredValue(values, condition.lowerBoundKey(), "lower_bound_key"),
                    requiredValue(values, condition.upperBoundKey(), "upper_bound_key")
            );
        };
    }

    private static double requiredValue(RankValues values, Optional<String> key, String field) {
        String resolvedKey = key.orElseThrow(() -> new IllegalArgumentException("Missing " + field));
        Double value = values.values().get(resolvedKey);
        if (value == null) {
            throw new IllegalArgumentException("Missing rank value: " + resolvedKey);
        }
        return value;
    }

    private static void applyEffects(ServerPlayer player, List<EffectConfig> effects) {
        for (EffectConfig config : effects) {
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(config.effect())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown mob effect: " + config.effect()));
            player.addEffect(new MobEffectInstance(
                    effect,
                    config.durationTicks(),
                    config.amplifier(),
                    config.ambient(),
                    config.showParticles(),
                    config.showIcon()
            ));
        }
    }

    private static void validateResolvedConditions(
            List<ConditionConfig> conditions,
            RankValues values,
            int rankIndex,
            List<String> errors
    ) {
        for (int index = 0; index < conditions.size(); index++) {
            ConditionConfig condition = conditions.get(index);
            if (condition.type() != ConditionType.Y_OUTSIDE_RANGE) {
                continue;
            }
            Optional<Double> lower = condition.lowerBoundKey().map(values.values()::get);
            Optional<Double> upper = condition.upperBoundKey().map(values.values()::get);
            if (lower.isPresent() && upper.isPresent() && lower.get() >= upper.get()) {
                errors.add(
                        "ranks.values[" + rankIndex + "]: condition " + index
                                + " lower bound must be less than upper bound"
                );
            }
        }
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown conditional mob effect error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid conditional mob effect ability {}: {}", abilityId, message);
        }
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder();
        Optional<T> parsed = codec.parse(input).resultOrPartial(message -> {
            if (!error.isEmpty()) {
                error.append("; ");
            }
            error.append(message);
        });
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }

    public record Config(MatchMode match, List<ConditionConfig> conditions, List<EffectConfig> effects) {
        private static final Codec<Config> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MatchMode.CODEC.optionalFieldOf("match", MatchMode.ALL).forGetter(Config::match),
                ConditionConfig.CODEC.listOf().fieldOf("conditions").forGetter(Config::conditions),
                EffectConfig.CODEC.listOf().fieldOf("effects").forGetter(Config::effects)
        ).apply(instance, Config::new));
        public static final Codec<Config> CODEC = RAW_CODEC.flatXmap(Config::validate, Config::validate);

        public Config {
            conditions = List.copyOf(conditions);
            effects = List.copyOf(effects);
        }

        private static DataResult<Config> validate(Config config) {
            if (config.conditions().isEmpty()) {
                return DataResult.error(() -> "conditions must contain at least one entry");
            }
            if (config.effects().isEmpty()) {
                return DataResult.error(() -> "effects must contain at least one entry");
            }
            return DataResult.success(config);
        }
    }

    public record ConditionConfig(
            ConditionType type,
            Optional<String> valueKey,
            Optional<String> lowerBoundKey,
            Optional<String> upperBoundKey
    ) {
        private static final Codec<String> KEY_CODEC = Codec.STRING.comapFlatMap(
                value -> VALUE_KEY.matcher(value).matches()
                        ? DataResult.success(value)
                        : DataResult.error(() -> "Invalid rank value key: " + value),
                value -> value
        );
        private static final Codec<ConditionConfig> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ConditionType.CODEC.fieldOf("type").forGetter(ConditionConfig::type),
                KEY_CODEC.optionalFieldOf("value_key").forGetter(ConditionConfig::valueKey),
                KEY_CODEC.optionalFieldOf("lower_bound_key").forGetter(ConditionConfig::lowerBoundKey),
                KEY_CODEC.optionalFieldOf("upper_bound_key").forGetter(ConditionConfig::upperBoundKey)
        ).apply(instance, ConditionConfig::new));
        public static final Codec<ConditionConfig> CODEC = RAW_CODEC.flatXmap(
                ConditionConfig::validate,
                ConditionConfig::validate
        );

        private static DataResult<ConditionConfig> validate(ConditionConfig condition) {
            return switch (condition.type()) {
                case FOOD_TOTAL_ABOVE -> condition.valueKey().isPresent()
                        && condition.lowerBoundKey().isEmpty()
                        && condition.upperBoundKey().isEmpty()
                        ? DataResult.success(condition)
                        : DataResult.error(() -> "food_total_above requires only value_key");
                case Y_OUTSIDE_RANGE -> condition.valueKey().isEmpty()
                        && condition.lowerBoundKey().isPresent()
                        && condition.upperBoundKey().isPresent()
                        ? DataResult.success(condition)
                        : DataResult.error(() -> "y_outside_range requires lower_bound_key and upper_bound_key");
            };
        }

        private List<String> valueKeys() {
            return java.util.stream.Stream.of(valueKey, lowerBoundKey, upperBoundKey)
                    .flatMap(Optional::stream)
                    .toList();
        }
    }

    public record EffectConfig(
            ResourceLocation effect,
            int amplifier,
            int durationTicks,
            boolean ambient,
            boolean showParticles,
            boolean showIcon
    ) {
        public static final Codec<EffectConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("effect").forGetter(EffectConfig::effect),
                Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(EffectConfig::amplifier),
                Codec.intRange(2, 1200).optionalFieldOf("duration_ticks", 30).forGetter(EffectConfig::durationTicks),
                Codec.BOOL.optionalFieldOf("ambient", false).forGetter(EffectConfig::ambient),
                Codec.BOOL.optionalFieldOf("show_particles", false).forGetter(EffectConfig::showParticles),
                Codec.BOOL.optionalFieldOf("show_icon", true).forGetter(EffectConfig::showIcon)
        ).apply(instance, EffectConfig::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(
                Codec.STRING,
                Codec.doubleRange(-1_000_000.0D, 1_000_000.0D)
        ).xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public enum ConditionType implements StringRepresentable {
        FOOD_TOTAL_ABOVE("food_total_above"),
        Y_OUTSIDE_RANGE("y_outside_range");

        public static final Codec<ConditionType> CODEC = StringRepresentable.fromEnum(ConditionType::values);
        private final String serializedName;

        ConditionType(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    public enum MatchMode implements StringRepresentable {
        ALL("all"),
        ANY("any");

        public static final Codec<MatchMode> CODEC = StringRepresentable.fromEnum(MatchMode::values);
        private final String serializedName;

        MatchMode(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
