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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class FineFeedEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("fine_feed");
    private static final Set<String> RANK_KEYS = Set.of(
            "maximum_movement_speed",
            "maximum_jump_strength"
    );
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private FineFeedEffect() {
    }

    public static void processTick(ServerPlayer player) {
        if (!(player.getVehicle() instanceof LivingEntity mount)) {
            return;
        }
        for (ActiveComponent component : activeComponents(player)) {
            if (player.tickCount % component.config().checkIntervalTicks() != 0) {
                continue;
            }
            TagKey<EntityType<?>> mountTypes = TagKey.create(
                    Registries.ENTITY_TYPE,
                    component.config().mountEntityTypeTag()
            );
            if (!mount.getType().is(mountTypes)) {
                continue;
            }
            AttributeInstance movementSpeedAttribute = mount.getAttribute(Attributes.MOVEMENT_SPEED);
            AttributeInstance jumpStrengthAttribute = mount.getAttribute(Attributes.JUMP_STRENGTH);
            if (movementSpeedAttribute == null || jumpStrengthAttribute == null) {
                continue;
            }
            double movementSpeed = movementSpeedAttribute.getBaseValue();
            double jumpStrength = jumpStrengthAttribute.getBaseValue();
            if (qualifies(movementSpeed, jumpStrength, component.rank())) {
                applyEffect(mount, component.config().speedEffect(), component.config().speedAmplifier(),
                        component.config().effectDurationTicks());
                applyEffect(mount, component.config().jumpEffect(), component.config().jumpAmplifier(),
                        component.config().effectDurationTicks());
            }
        }
    }

    static boolean qualifies(double movementSpeed, double jumpStrength, ResolvedRank rank) {
        return Double.isFinite(movementSpeed)
                && Double.isFinite(jumpStrength)
                && (movementSpeed <= rank.maximumMovementSpeed()
                || jumpStrength <= rank.maximumJumpStrength());
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static ResolvedRank resolve(RankValues values) {
        return new ResolvedRank(
                requiredFinite(values.values().get("maximum_movement_speed"), "maximum_movement_speed"),
                requiredFinite(values.values().get("maximum_jump_strength"), "maximum_jump_strength")
        );
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        try {
            Config config = parse(Config.CODEC, definition.effect().config(), "effect.config");
            validateMobEffect(config.speedEffect(), "effect.config.speed_effect", errors);
            validateMobEffect(config.jumpEffect(), "effect.config.jump_effect", errors);
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
                                        + ": unsupported fine feed parameter"
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

    private static void applyEffect(LivingEntity mount, ResourceLocation effectId, int amplifier, int durationTicks) {
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(effectId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown mob effect: " + effectId));
        mount.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, false, false, true));
    }

    private static void validateMobEffect(ResourceLocation effectId, String path, List<String> errors) {
        if (BuiltInRegistries.MOB_EFFECT.getHolder(effectId).isEmpty()) {
            errors.add(path + ": unknown mob effect " + effectId);
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

    private static double requiredFinite(Double value, String name) {
        if (value == null || !Double.isFinite(value) || value < 0.0D || value > 1024.0D) {
            throw new IllegalArgumentException(name + " must be finite and between 0 and 1024");
        }
        return value;
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown fine feed error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid fine feed ability {}: {}", abilityId, message);
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
            ResourceLocation mountEntityTypeTag,
            ResourceLocation speedEffect,
            int speedAmplifier,
            ResourceLocation jumpEffect,
            int jumpAmplifier,
            int checkIntervalTicks,
            int effectDurationTicks
    ) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("mount_entity_type_tag").forGetter(Config::mountEntityTypeTag),
                ResourceLocation.CODEC.optionalFieldOf("speed_effect", ResourceLocation.withDefaultNamespace("speed"))
                        .forGetter(Config::speedEffect),
                Codec.intRange(0, 255).optionalFieldOf("speed_amplifier", 2).forGetter(Config::speedAmplifier),
                ResourceLocation.CODEC.optionalFieldOf("jump_effect", ResourceLocation.withDefaultNamespace("jump_boost"))
                        .forGetter(Config::jumpEffect),
                Codec.intRange(0, 255).optionalFieldOf("jump_amplifier", 1).forGetter(Config::jumpAmplifier),
                Codec.intRange(1, 1200).optionalFieldOf("check_interval_ticks", 10)
                        .forGetter(Config::checkIntervalTicks),
                Codec.intRange(2, 1200).optionalFieldOf("effect_duration_ticks", 30)
                        .forGetter(Config::effectDurationTicks)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public record ResolvedRank(double maximumMovementSpeed, double maximumJumpStrength) {
    }

    private record ActiveComponent(Config config, ResolvedRank rank) {
    }
}
