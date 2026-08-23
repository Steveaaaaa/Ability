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
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class HarvestEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("harvest");
    private static final Set<String> RANK_KEYS = Set.of("rank_factor", "food_cap");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private HarvestEffect() {
    }

    public static void modifyDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()
                || event.getAmount() <= 0.0F
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || event.getSource().getDirectEntity() != attacker) {
            return;
        }
        List<ActiveComponent> components = activeComponents(attacker);
        double bonusDamage = 0.0D;
        double foodValue = attacker.getFoodData().getFoodLevel() + attacker.getFoodData().getSaturationLevel();
        for (ActiveComponent component : components) {
            if (matchesTool(attacker, component.config())) {
                bonusDamage += bonusDamage(component.rank(), foodValue, component.config().damageDivisor());
            }
        }
        if (bonusDamage > 0.0D) {
            event.setAmount(safeDamage((double) event.getAmount() + bonusDamage));
        }
    }

    public static void consumeFood(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || event.getSource().getDirectEntity() != attacker) {
            return;
        }
        double exhaustion = 0.0D;
        for (ActiveComponent component : activeComponents(attacker)) {
            if (matchesTool(attacker, component.config())) {
                exhaustion += component.config().exhaustionCost();
            }
        }
        if (exhaustion > 0.0D) {
            attacker.causeFoodExhaustion(safeDamage(
                    FrugalityEffect.reduceAbilityHungerCost(attacker, exhaustion)
            ));
        }
    }

    static double bonusDamage(ResolvedRank rank, double foodValue, double damageDivisor) {
        if (!Double.isFinite(foodValue) || !Double.isFinite(damageDivisor) || damageDivisor <= 0.0D) {
            return 0.0D;
        }
        return rank.rankFactor() * Math.clamp(foodValue, 0.0D, rank.foodCap()) / damageDivisor;
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static ResolvedRank resolve(RankValues values) {
        double rankFactor = requiredFinite(values.values().get("rank_factor"), "rank_factor", 0.0D, 1000.0D);
        double foodCap = requiredFinite(values.values().get("food_cap"), "food_cap", 0.0D, 1000.0D);
        return new ResolvedRank(rankFactor, foodCap);
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
                                "ranks.values[" + rankIndex + "]." + key
                                        + ": unsupported harvest parameter"
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

    private static boolean matchesTool(ServerPlayer attacker, Config config) {
        return attacker.getMainHandItem().is(TagKey.create(Registries.ITEM, config.toolTag()));
    }

    private static List<ActiveComponent> activeComponents(ServerPlayer player) {
        ArrayList<ActiveComponent> result = new ArrayList<>();
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
                    Config config = parse(Config.CODEC, projected.definition().effect().config(), "effect.config");
                    result.add(new ActiveComponent(config, resolve(mergeRanks(projected))));
                }
            } catch (RuntimeException exception) {
                logInvalidOnce(abilityId, exception.getMessage());
            }
        }
        return List.copyOf(result);
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

    private static double requiredFinite(
            Double value,
            String name,
            double minimumExclusive,
            double maximum
    ) {
        if (value == null || !Double.isFinite(value) || value <= minimumExclusive || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be finite, greater than " + minimumExclusive + ", and at most " + maximum
            );
        }
        return value;
    }

    private static float safeDamage(double value) {
        return Double.isFinite(value) ? (float) Math.clamp(value, 0.0D, Float.MAX_VALUE) : Float.MAX_VALUE;
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown harvest error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid harvest ability {}: {}", abilityId, message);
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

    public record Config(ResourceLocation toolTag, double exhaustionCost, double damageDivisor) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("tool_tag", ResourceLocation.withDefaultNamespace("hoes"))
                        .forGetter(Config::toolTag),
                Codec.doubleRange(0.0D, 1000.0D).optionalFieldOf("exhaustion_cost", 1.0D)
                        .forGetter(Config::exhaustionCost),
                Codec.doubleRange(0.001D, 1000000.0D).optionalFieldOf("damage_divisor", 40.0D)
                        .forGetter(Config::damageDivisor)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public record ResolvedRank(double rankFactor, double foodCap) {
    }

    private record ActiveComponent(Config config, ResolvedRank rank) {
    }
}
