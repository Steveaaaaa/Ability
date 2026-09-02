package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.presentation.AbilityPresentationService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class CounterSniperEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("counter_sniper");
    private static final ResourceLocation TARGET_MARK_CUE = AbilityMod.id("target_mark");
    private static final Set<String> RANK_KEYS = Set.of("damage_multiplier");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private CounterSniperEffect() {
    }

    public static void modifyOutgoingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()
                || event.getAmount() <= 0.0F
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        LivingEntity target = event.getEntity();
        for (ActiveComponent component : activeComponents(attacker)) {
            if (!CombatStatusTracker.hasMark(
                    attacker.getUUID(),
                    target.getUUID(),
                    component.config().markId()
            )) {
                continue;
            }
            event.setAmount(safeDamage((double) event.getAmount() * component.rank().damageMultiplier()));
        }
    }

    public static void processFinalDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F) {
            return;
        }
        LivingEntity damaged = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            for (ActiveComponent component : activeComponents(attacker)) {
                if (CombatStatusTracker.consumeMark(
                        attacker.getUUID(),
                        damaged.getUUID(),
                        component.config().markId()
                )) {
                    AbilityPresentationService.sendToPlayer(attacker, targetMarkCue(attacker, damaged).asStop());
                }
            }
        }
        if (!(damaged instanceof ServerPlayer victim)
                || !(event.getSource().getEntity() instanceof LivingEntity source)
                || source == victim) {
            return;
        }
        for (ActiveComponent component : activeComponents(victim)) {
            if (isDistant(victim.distanceToSqr(source), component.config().minimumDistance())) {
                CombatStatusTracker.setMark(
                        victim.getUUID(),
                        source.getUUID(),
                        component.config().markId(),
                        component.config().glowing()
                );
                AbilityPresentationService.sendToPlayer(victim, targetMarkCue(victim, source));
            }
        }
    }

    public static void syncMarkToOwner(ServerPlayer player, LivingEntity target) {
        for (ActiveComponent component : activeComponents(player)) {
            if (CombatStatusTracker.hasMark(player.getUUID(), target.getUUID(), component.config().markId())) {
                AbilityPresentationService.sendToPlayer(player, targetMarkCue(player, target));
                return;
            }
        }
    }

    private static AbilityCue targetMarkCue(ServerPlayer owner, LivingEntity target) {
        return AbilityCue.start(
                TYPE,
                TARGET_MARK_CUE,
                owner.getId(),
                target.getId(),
                target.getBoundingBox().getCenter(),
                target.position().subtract(owner.position()).normalize(),
                1,
                0,
                owner.getUUID().getLeastSignificantBits() ^ target.getUUID().getMostSignificantBits(),
                target.getUUID().getLeastSignificantBits()
        );
    }

    static boolean isDistant(double squaredDistance, double minimumDistance) {
        return squaredDistance > minimumDistance * minimumDistance;
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> merged = new LinkedHashMap<>(earlier.values());
        merged.putAll(later.values());
        return new RankValues(merged);
    }

    static ResolvedRank resolve(RankValues values) {
        Double multiplier = values.values().get("damage_multiplier");
        if (multiplier == null || !Double.isFinite(multiplier) || multiplier < 0.0D || multiplier > 100.0D) {
            throw new IllegalArgumentException("damage_multiplier must be finite and between 0 and 100");
        }
        return new ResolvedRank(multiplier);
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
                                        + ": unsupported counter sniper parameter"
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
                    Config config = parse(Config.CODEC, component.config(), "effect.config");
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

    private static float safeDamage(double value) {
        return Double.isFinite(value) ? (float) Math.clamp(value, 0.0D, Float.MAX_VALUE) : Float.MAX_VALUE;
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown counter sniper error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid counter sniper ability {}: {}", abilityId, message);
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

    public record Config(ResourceLocation markId, double minimumDistance, boolean glowing) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("mark_id", AbilityMod.id("prey"))
                        .forGetter(Config::markId),
                Codec.doubleRange(0.0D, 1024.0D).optionalFieldOf("minimum_distance", 8.0D)
                        .forGetter(Config::minimumDistance),
                Codec.BOOL.optionalFieldOf("glowing", true).forGetter(Config::glowing)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public record ResolvedRank(double damageMultiplier) {
    }

    private record ActiveComponent(Config config, ResolvedRank rank) {
    }
}
