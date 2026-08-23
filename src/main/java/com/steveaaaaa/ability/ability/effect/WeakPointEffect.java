package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
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
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class WeakPointEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("weak_point");
    private static final Set<String> RANK_KEYS = Set.of("mark_threshold", "damage_multiplier", "stun_ticks");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private WeakPointEffect() {
    }

    public static void process(LivingIncomingDamageEvent event) {
        if (event.isCanceled()
                || event.getAmount() <= 0.0F
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        LivingEntity target = event.getEntity();
        Registry<AbilityDefinition> abilities = attacker.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
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
                Optional<AbilityService.ActiveAbility> active = AbilityService.active(attacker, abilityId);
                if (active.isEmpty()) {
                    continue;
                }
                for (CompositeEffect.ComponentView component : components) {
                    AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active.get(), component);
                    Config config = parse(Config.CODEC, component.config(), "effect.config");
                    if (!matches(config, target, event.getSource())) {
                        continue;
                    }
                    ResolvedRank rank = resolve(mergeRanks(projected));
                    CombatStatusTracker.MarkResult result = CombatStatusTracker.addMark(
                            attacker.getUUID(),
                            target.getUUID(),
                            config.markId(),
                            config.marksPerHit(),
                            rank.markThreshold()
                    );
                    if (!result.triggered()) {
                        continue;
                    }
                    event.setAmount(safeDamage((double) event.getAmount() * rank.damageMultiplier()));
                    CombatStatusTracker.stun(target, rank.stunTicks());
                    if (config.foodCost() > 0) {
                        AbilityHungerCostService.applyFoodPointCost(
                                attacker,
                                FrugalityEffect.reduceAbilityHungerCost(attacker, config.foodCost())
                        );
                    }
                }
            } catch (RuntimeException exception) {
                logInvalidOnce(abilityId, exception.getMessage());
            }
        }
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> merged = new LinkedHashMap<>(earlier.values());
        merged.putAll(later.values());
        return new RankValues(merged);
    }

    static ResolvedRank resolve(RankValues values) {
        int threshold = exactInt(values.values().get("mark_threshold"), "mark_threshold", 1, 1000);
        double multiplier = requiredFinite(values.values().get("damage_multiplier"), "damage_multiplier", 0.0D, 100.0D);
        int stunTicks = exactInt(values.values().get("stun_ticks"), "stun_ticks", 0, 1200);
        return new ResolvedRank(threshold, multiplier, stunTicks);
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        try {
            parse(Config.CODEC, definition.effect().config(), "effect.config");
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
        }
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
                        .filter(key -> !RANK_KEYS.contains(key))
                        .forEach(key -> errors.add(
                                "ranks.values[" + rankIndex + "]." + key + ": unsupported weak point parameter"
                        ));
                merged = merge(merged, current);
                try {
                    resolve(merged);
                } catch (IllegalArgumentException exception) {
                    errors.add("ranks.values[" + index + "]: " + exception.getMessage());
                }
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
            }
        }
        return List.copyOf(errors);
    }

    private static boolean matches(Config config, LivingEntity target, DamageSource source) {
        if (config.directness() == DamageModifierEffect.Directness.DIRECT && !source.isDirect()) {
            return false;
        }
        if (config.directness() == DamageModifierEffect.Directness.INDIRECT && source.isDirect()) {
            return false;
        }
        return config.targetEntityTypeTags().isEmpty() || config.targetEntityTypeTags().stream().anyMatch(tag ->
                target.getType().is(TagKey.create(Registries.ENTITY_TYPE, tag)));
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

    private static int exactInt(Double value, String name, int minimum, int maximum) {
        if (value == null || !Double.isFinite(value) || value != Math.rint(value)
                || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be a whole number between " + minimum + " and " + maximum
            );
        }
        return value.intValue();
    }

    private static double requiredFinite(Double value, String name, double minimum, double maximum) {
        if (value == null || !Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be finite and between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static float safeDamage(double value) {
        return Double.isFinite(value) ? (float) Math.clamp(value, 0.0D, Float.MAX_VALUE) : Float.MAX_VALUE;
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown weak point error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid weak point ability {}: {}", abilityId, message);
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
            ResourceLocation markId,
            int marksPerHit,
            int foodCost,
            List<ResourceLocation> targetEntityTypeTags,
            DamageModifierEffect.Directness directness
    ) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("mark_id", AbilityMod.id("weak_point"))
                        .forGetter(Config::markId),
                Codec.intRange(1, 1000).optionalFieldOf("marks_per_hit", 1).forGetter(Config::marksPerHit),
                Codec.intRange(0, 20).optionalFieldOf("food_cost", 0).forGetter(Config::foodCost),
                ResourceLocation.CODEC.listOf().optionalFieldOf("target_entity_type_tags", List.of())
                        .forGetter(Config::targetEntityTypeTags),
                DamageModifierEffect.Directness.CODEC.optionalFieldOf(
                        "directness",
                        DamageModifierEffect.Directness.ANY
                ).forGetter(Config::directness)
        ).apply(instance, Config::new));

        public Config {
            targetEntityTypeTags = List.copyOf(targetEntityTypeTags);
        }
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public record ResolvedRank(int markThreshold, double damageMultiplier, int stunTicks) {
    }
}
