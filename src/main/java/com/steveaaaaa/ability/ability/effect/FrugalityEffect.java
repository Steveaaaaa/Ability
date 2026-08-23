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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;

public final class FrugalityEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("frugality");
    private static final Set<String> RANK_KEYS = Set.of(
            "healing_hunger_reduction_percent",
            "ability_hunger_reduction_percent"
    );
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, HealingSnapshot> HEALING_SNAPSHOTS = new ConcurrentHashMap<>();

    private FrugalityEffect() {
    }

    public static double reduceAbilityHungerCost(ServerPlayer player, double baseCost) {
        if (!Double.isFinite(baseCost) || baseCost <= 0.0D) {
            return 0.0D;
        }
        double reduction = activeComponents(player).stream()
                .mapToDouble(component -> component.rank().abilityHungerReduction())
                .max()
                .orElse(0.0D);
        return baseCost * (1.0D - reduction);
    }

    public static void captureBeforeTick(ServerPlayer player) {
        FoodData food = player.getFoodData();
        HEALING_SNAPSHOTS.put(player.getUUID(), new HealingSnapshot(
                player.getHealth(),
                food.getExhaustionLevel()
        ));
    }

    public static void refundNaturalHealingCost(ServerPlayer player) {
        HealingSnapshot before = HEALING_SNAPSHOTS.remove(player.getUUID());
        if (before == null
                || !player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            return;
        }
        double healthGained = player.getHealth() - before.health();
        if (healthGained <= 0.0D) {
            return;
        }
        FoodData food = player.getFoodData();
        double refund = 0.0D;
        for (ActiveComponent component : activeComponents(player)) {
            double incurred = naturalHealingExhaustion(
                    before.exhaustion(),
                    food.getExhaustionLevel(),
                    healthGained,
                    component.config().exhaustionCycle(),
                    component.config().healingExhaustionPerHealth()
            );
            refund = Math.max(refund, incurred * component.rank().healingHungerReduction());
        }
        if (refund > 0.0D) {
            food.setExhaustion((float) Math.max(0.0D, food.getExhaustionLevel() - refund));
        }
    }

    public static void forget(UUID playerId) {
        HEALING_SNAPSHOTS.remove(playerId);
    }

    static double naturalHealingExhaustion(
            double beforeExhaustion,
            double afterExhaustion,
            double healthGained,
            double exhaustionCycle,
            double exhaustionPerHealth
    ) {
        if (!Double.isFinite(beforeExhaustion)
                || !Double.isFinite(afterExhaustion)
                || !Double.isFinite(healthGained)
                || !Double.isFinite(exhaustionCycle)
                || !Double.isFinite(exhaustionPerHealth)
                || healthGained <= 0.0D
                || exhaustionCycle <= 0.0D
                || exhaustionPerHealth <= 0.0D) {
            return 0.0D;
        }
        double processedCycle = beforeExhaustion > exhaustionCycle ? exhaustionCycle : 0.0D;
        double addedExhaustion = Math.max(0.0D, afterExhaustion - beforeExhaustion + processedCycle);
        return Math.min(addedExhaustion, healthGained * exhaustionPerHealth);
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static ResolvedRank resolve(RankValues values) {
        double healing = requiredPercent(
                values.values().get("healing_hunger_reduction_percent"),
                "healing_hunger_reduction_percent"
        );
        double ability = requiredPercent(
                values.values().get("ability_hunger_reduction_percent"),
                "ability_hunger_reduction_percent"
        );
        return new ResolvedRank(healing / 100.0D, ability / 100.0D);
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
                                        + ": unsupported frugality parameter"
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

    private static double requiredPercent(Double value, String name) {
        if (value == null || !Double.isFinite(value) || value < 0.0D || value > 100.0D) {
            throw new IllegalArgumentException(name + " must be finite and between 0 and 100");
        }
        return value;
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown frugality error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid frugality ability {}: {}", abilityId, message);
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

    public record Config(double exhaustionCycle, double healingExhaustionPerHealth) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.doubleRange(0.001D, 40.0D).optionalFieldOf("exhaustion_cycle", 4.0D)
                        .forGetter(Config::exhaustionCycle),
                Codec.doubleRange(0.001D, 1000.0D).optionalFieldOf("healing_exhaustion_per_health", 6.0D)
                        .forGetter(Config::healingExhaustionPerHealth)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public record ResolvedRank(double healingHungerReduction, double abilityHungerReduction) {
    }

    private record ActiveComponent(Config config, ResolvedRank rank) {
    }

    private record HealingSnapshot(float health, float exhaustion) {
    }
}
