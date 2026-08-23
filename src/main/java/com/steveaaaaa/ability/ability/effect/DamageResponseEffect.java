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
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class DamageResponseEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("damage_response");
    private static final Pattern VALUE_KEY = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private DamageResponseEffect() {
    }

    public static void process(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getNewDamage() <= 0.0F
                || !player.isAlive()) {
            return;
        }
        Registry<AbilityDefinition> abilities = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
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
                    double threshold = requiredValue(values, config.healthRatioBelowKey());
                    int level = exactLevel(requiredValue(values, config.effectLevelKey()));
                    boolean livingAttacker = event.getSource().getEntity() instanceof LivingEntity;
                    if (!shouldTrigger(
                            event.getNewDamage(),
                            player.getHealth(),
                            player.getMaxHealth(),
                            threshold,
                            config.requireLivingAttacker(),
                            livingAttacker
                    )) {
                        continue;
                    }
                    Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(config.effect())
                            .orElseThrow(() -> new IllegalArgumentException("Unknown mob effect: " + config.effect()));
                    player.addEffect(new MobEffectInstance(
                            effect,
                            config.durationTicks(),
                            level - 1,
                            config.ambient(),
                            config.showParticles(),
                            config.showIcon()
                    ));
                }
            } catch (RuntimeException exception) {
                logInvalidOnce(abilityId, exception.getMessage());
            }
        }
    }

    static boolean shouldTrigger(
            float finalDamage,
            float health,
            float maximumHealth,
            double threshold,
            boolean requireLivingAttacker,
            boolean livingAttacker
    ) {
        return finalDamage > 0.0F
                && (!requireLivingAttacker || livingAttacker)
                && DamageModifierEffect.healthRatio(health, maximumHealth) < threshold;
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> merged = new LinkedHashMap<>(earlier.values());
        merged.putAll(later.values());
        return new RankValues(merged);
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        Config config;
        try {
            config = parse(Config.CODEC, definition.effect().config(), "effect.config");
        } catch (IllegalArgumentException exception) {
            return List.of(exception.getMessage());
        }
        if (BuiltInRegistries.MOB_EFFECT.getHolder(config.effect()).isEmpty()) {
            errors.add("effect.config.effect: unknown mob effect " + config.effect());
        }

        Set<String> allowedKeys = Set.of(config.healthRatioBelowKey(), config.effectLevelKey());
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
                        .filter(key -> !allowedKeys.contains(key))
                        .forEach(key -> errors.add(
                                "ranks.values[" + rankIndex + "]." + key + ": unsupported response parameter"
                        ));
                merged = merge(merged, current);
                Double threshold = merged.values().get(config.healthRatioBelowKey());
                Double level = merged.values().get(config.effectLevelKey());
                if (threshold == null) {
                    errors.add("ranks.values[" + index + "]: missing " + config.healthRatioBelowKey());
                } else if (!Double.isFinite(threshold) || threshold < 0.0D || threshold > 1.0D) {
                    errors.add("ranks.values[" + index + "]." + config.healthRatioBelowKey()
                            + ": must be between 0 and 1");
                }
                if (level == null) {
                    errors.add("ranks.values[" + index + "]: missing " + config.effectLevelKey());
                } else {
                    try {
                        exactLevel(level);
                    } catch (IllegalArgumentException exception) {
                        errors.add("ranks.values[" + index + "]." + config.effectLevelKey()
                                + ": " + exception.getMessage());
                    }
                }
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
            }
        }
        return List.copyOf(errors);
    }

    private static RankValues mergeRanks(AbilityService.ActiveAbility active) {
        RankValues merged = new RankValues(Map.of());
        for (int index = 0; index < active.unlockedRankValues().size(); index++) {
            merged = merge(merged, parse(
                    RankValues.CODEC,
                    active.unlockedRankValues().get(index),
                    "ranks.values[" + index + "]"
            ));
        }
        return merged;
    }

    private static double requiredValue(RankValues values, String key) {
        Double value = values.values().get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing rank value: " + key);
        }
        return value;
    }

    private static int exactLevel(double value) {
        if (!Double.isFinite(value) || value != Math.rint(value) || value < 1.0D || value > 256.0D) {
            throw new IllegalArgumentException("effect level must be a whole number between 1 and 256");
        }
        return (int) value;
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown damage response error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid damage response ability {}: {}", abilityId, message);
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

    public record Config(
            String healthRatioBelowKey,
            ResourceLocation effect,
            String effectLevelKey,
            int durationTicks,
            boolean requireLivingAttacker,
            boolean ambient,
            boolean showParticles,
            boolean showIcon
    ) {
        private static final Codec<String> KEY_CODEC = Codec.STRING.comapFlatMap(
                value -> VALUE_KEY.matcher(value).matches()
                        ? DataResult.success(value)
                        : DataResult.error(() -> "Invalid rank value key: " + value),
                value -> value
        );
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                KEY_CODEC.fieldOf("health_ratio_below_key").forGetter(Config::healthRatioBelowKey),
                ResourceLocation.CODEC.fieldOf("effect").forGetter(Config::effect),
                KEY_CODEC.fieldOf("effect_level_key").forGetter(Config::effectLevelKey),
                Codec.intRange(2, 1200).optionalFieldOf("duration_ticks", 60).forGetter(Config::durationTicks),
                Codec.BOOL.optionalFieldOf("require_living_attacker", true).forGetter(Config::requireLivingAttacker),
                Codec.BOOL.optionalFieldOf("ambient", false).forGetter(Config::ambient),
                Codec.BOOL.optionalFieldOf("show_particles", false).forGetter(Config::showParticles),
                Codec.BOOL.optionalFieldOf("show_icon", true).forGetter(Config::showIcon)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }
}
