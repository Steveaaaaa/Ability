package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.progress.AbilityDailyState;
import com.steveaaaaa.ability.progress.ModAttachments;
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
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class WellPreparedEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("well_prepared");
    private static final Set<String> RANK_KEYS = Set.of(
            "absorption_health_percent",
            "invulnerability_seconds"
    );
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private WellPreparedEffect() {
    }

    public static void preventDamage(LivingIncomingDamageEvent event) {
        if (!event.isCanceled()
                && event.getEntity() instanceof ServerPlayer player
                && WellPreparedTracker.isInvulnerable(player.getUUID(), player.level().getGameTime())) {
            event.setCanceled(true);
        }
    }

    public static void preventDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AbilityDailyState dailyState = player.getData(ModAttachments.ABILITY_DAILY_STATE);
        Optional<ActiveComponent> selected = activeComponents(player).stream()
                .filter(component -> dailyState.available(
                        component.abilityId(),
                        gameDay(player.level().getDayTime(), component.config().dayLengthTicks())
                ))
                .max(Comparator.comparingDouble((ActiveComponent component) ->
                                component.rank().absorptionHealthFraction())
                        .thenComparingInt(component -> component.rank().invulnerabilityTicks())
                        .thenComparing(component -> component.abilityId().toString()));
        if (selected.isEmpty()) {
            return;
        }
        ActiveComponent component = selected.get();
        long currentDay = gameDay(player.level().getDayTime(), component.config().dayLengthTicks());
        player.setData(
                ModAttachments.ABILITY_DAILY_STATE,
                dailyState.consume(component.abilityId(), currentDay)
        );

        event.setCanceled(true);
        clearHarmfulEffects(player);
        float restoredHealth = (float) (player.getMaxHealth() * component.config().restoredHealthFraction());
        player.setHealth(Math.max(1.0F, restoredHealth));
        float absorption = (float) (player.getMaxHealth() * component.rank().absorptionHealthFraction());
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), absorption));
        WellPreparedTracker.grant(
                player.getUUID(),
                player.level().getGameTime() + component.rank().invulnerabilityTicks()
        );
        long gameTime = player.level().getGameTime();
        AbilityPresentationService.sendTracking(player, AbilityCue.start(
                component.abilityId(),
                AbilityMod.id("salvation"),
                player.getId(),
                player.getId(),
                player.position(),
                player.getLookAngle(),
                component.abilityRank(),
                component.rank().invulnerabilityTicks(),
                (gameTime << 20) ^ player.getId(),
                player.getRandom().nextLong()
        ));
    }

    static long gameDay(long dayTime, long dayLengthTicks) {
        if (dayLengthTicks <= 0L) {
            throw new IllegalArgumentException("dayLengthTicks must be positive");
        }
        return Math.floorDiv(dayTime, dayLengthTicks);
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static ResolvedRank resolve(RankValues values) {
        double absorptionPercent = requiredFinite(
                values.values().get("absorption_health_percent"),
                "absorption_health_percent",
                0.0D,
                1000.0D
        );
        double invulnerabilitySeconds = requiredFinite(
                values.values().get("invulnerability_seconds"),
                "invulnerability_seconds",
                0.0D,
                60.0D
        );
        return new ResolvedRank(
                absorptionPercent / 100.0D,
                (int) Math.round(invulnerabilitySeconds * 20.0D)
        );
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
                                        + ": unsupported well prepared parameter"
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

    private static void clearHarmfulEffects(ServerPlayer player) {
        for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                player.removeEffect(effect.getEffect());
            }
        }
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
                    result.add(new ActiveComponent(
                            abilityId,
                            config,
                            resolve(mergeRanks(projected)),
                            projected.rank()
                    ));
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
            double minimum,
            double maximum
    ) {
        if (value == null || !Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be finite and between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown well prepared error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid well prepared ability {}: {}", abilityId, message);
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

    public record Config(double restoredHealthFraction, int dayLengthTicks) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("restored_health_fraction", 0.5D)
                        .forGetter(Config::restoredHealthFraction),
                Codec.intRange(1, 2400000).optionalFieldOf("day_length_ticks", 24000)
                        .forGetter(Config::dayLengthTicks)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public record ResolvedRank(double absorptionHealthFraction, int invulnerabilityTicks) {
    }

    private record ActiveComponent(
            ResourceLocation abilityId,
            Config config,
            ResolvedRank rank,
            int abilityRank
    ) {
    }
}
