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
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class StealthEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("stealth");
    private static final Set<String> RANK_KEYS = Set.of("wait_seconds", "damage_multiplier");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private StealthEffect() {
    }

    public static void processTick(ServerPlayer player) {
        long elapsedTicks = InactivityTracker.elapsedTicks(player);
        for (ActiveComponent component : activeComponents(player)) {
            Config config = component.config();
            if (!CombatStatusTracker.hasMark(player.getUUID(), player.getUUID(), config.markId())
                    && elapsedTicks >= component.rank().waitTicks()) {
                CombatStatusTracker.setMark(player.getUUID(), player.getUUID(), config.markId(), false);
            }
            if (CombatStatusTracker.hasMark(player.getUUID(), player.getUUID(), config.markId())) {
                Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(config.effect())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown mob effect: " + config.effect()));
                player.addEffect(new MobEffectInstance(
                        effect,
                        config.effectDurationTicks(),
                        0,
                        false,
                        config.showParticles(),
                        config.showIcon()
                ));
            }
        }
    }

    public static void modifyOutgoingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()
                || event.getAmount() <= 0.0F
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        for (ActiveComponent component : activeComponents(attacker)) {
            if (CombatStatusTracker.hasMark(
                    attacker.getUUID(),
                    attacker.getUUID(),
                    component.config().markId()
            )) {
                event.setAmount(safeDamage((double) event.getAmount() * component.rank().damageMultiplier()));
            }
        }
    }

    public static void processFinalDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            InactivityTracker.recordActivity(victim);
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        InactivityTracker.recordActivity(attacker);
        for (ActiveComponent component : activeComponents(attacker)) {
            CombatStatusTracker.consumeMark(
                    attacker.getUUID(),
                    attacker.getUUID(),
                    component.config().markId()
            );
        }
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> merged = new LinkedHashMap<>(earlier.values());
        merged.putAll(later.values());
        return new RankValues(merged);
    }

    static ResolvedRank resolve(RankValues values) {
        Double seconds = values.values().get("wait_seconds");
        Double multiplier = values.values().get("damage_multiplier");
        if (seconds == null || !Double.isFinite(seconds) || seconds < 0.0D || seconds > 3600.0D) {
            throw new IllegalArgumentException("wait_seconds must be finite and between 0 and 3600");
        }
        if (multiplier == null || !Double.isFinite(multiplier) || multiplier < 0.0D || multiplier > 100.0D) {
            throw new IllegalArgumentException("damage_multiplier must be finite and between 0 and 100");
        }
        long waitTicks = Math.round(seconds * 20.0D);
        return new ResolvedRank(waitTicks, multiplier);
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        Config config;
        try {
            config = parse(Config.CODEC, definition.effect().config(), "effect.config");
            if (BuiltInRegistries.MOB_EFFECT.getHolder(config.effect()).isEmpty()) {
                errors.add("effect.config.effect: unknown mob effect " + config.effect());
            }
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
                                "ranks.values[" + rankIndex + "]." + key + ": unsupported stealth parameter"
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
        String message = detail == null ? "Unknown stealth error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid stealth ability {}: {}", abilityId, message);
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
            ResourceLocation effect,
            int effectDurationTicks,
            boolean showParticles,
            boolean showIcon
    ) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("mark_id", AbilityMod.id("stealth"))
                        .forGetter(Config::markId),
                ResourceLocation.CODEC.optionalFieldOf("effect", ResourceLocation.withDefaultNamespace("invisibility"))
                        .forGetter(Config::effect),
                Codec.intRange(2, 1200).optionalFieldOf("effect_duration_ticks", 12)
                        .forGetter(Config::effectDurationTicks),
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

    public record ResolvedRank(long waitTicks, double damageMultiplier) {
    }

    private record ActiveComponent(Config config, ResolvedRank rank) {
    }
}
