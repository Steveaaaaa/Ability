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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;

public final class RetaliatoryFlameEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("retaliatory_flame");
    private static final Set<String> RANK_KEYS = Set.of("radius");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private RetaliatoryFlameEffect() {
    }

    public static void processTick(ServerPlayer player) {
        if (player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return;
        }
        ArrayList<Activation> activations = new ArrayList<>();
        for (ActiveComponent component : activeComponents(player)) {
            if (player.tickCount % component.config().checkIntervalTicks() != 0) {
                continue;
            }
            Environment environment = detectEnvironment(player, component.config());
            if (environment != Environment.NONE) {
                activations.add(new Activation(component, environment));
            }
        }
        if (activations.isEmpty()) {
            return;
        }
        double radius = activations.stream()
                .mapToDouble(activation -> activation.component().rank().radius())
                .max()
                .orElse(0.0D);
        float bonusDamage = (float) activations.stream()
                .mapToDouble(activation -> damageFor(activation.environment(), activation.component().config()))
                .max()
                .orElse(0.0D);
        float burnSeconds = (float) activations.stream()
                .mapToDouble(activation -> activation.component().config().burnDurationSeconds())
                .max()
                .orElse(0.0D);

        AABB searchBox = player.getBoundingBox().inflate(radius);
        double radiusSquared = radius * radius;
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                target -> AbilityTargeting.canHarm(player, target)
                        && target.distanceToSqr(player) <= radiusSquared
        )) {
            target.igniteForSeconds(burnSeconds);
            if (bonusDamage > 0.0F) {
                target.hurt(fireDamageSource(target, player), bonusDamage);
            }
        }
    }

    static Environment environmentPriority(boolean normalFire, boolean soulFire, boolean lava) {
        if (lava) {
            return Environment.LAVA;
        }
        if (soulFire) {
            return Environment.SOUL_FIRE;
        }
        return normalFire ? Environment.NORMAL_FIRE : Environment.NONE;
    }

    static double damageFor(Environment environment, Config config) {
        return switch (environment) {
            case SOUL_FIRE -> config.soulFireDamage();
            case LAVA -> config.lavaDamage();
            case NONE, NORMAL_FIRE -> 0.0D;
        };
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static ResolvedRank resolve(RankValues values) {
        Double radius = values.values().get("radius");
        if (radius == null || !Double.isFinite(radius) || radius <= 0.0D || radius > 128.0D) {
            throw new IllegalArgumentException("radius must be finite, greater than 0, and at most 128");
        }
        return new ResolvedRank(radius);
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
                                        + ": unsupported retaliatory flame parameter"
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

    private static Environment detectEnvironment(ServerPlayer player, Config config) {
        TagKey<Block> normalFire = TagKey.create(Registries.BLOCK, config.normalFireBlockTag());
        TagKey<Block> soulFire = TagKey.create(Registries.BLOCK, config.soulFireBlockTag());
        TagKey<Fluid> lava = TagKey.create(Registries.FLUID, config.lavaFluidTag());
        boolean foundNormal = false;
        boolean foundSoul = false;
        boolean foundLava = false;
        AABB box = player.getBoundingBox().deflate(0.001D);
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX),
                Mth.floor(box.minY),
                Mth.floor(box.minZ),
                Mth.floor(box.maxX),
                Mth.floor(box.maxY),
                Mth.floor(box.maxZ)
        )) {
            var state = player.level().getBlockState(pos);
            foundNormal |= state.is(normalFire);
            foundSoul |= state.is(soulFire);
            foundLava |= state.getFluidState().is(lava);
        }
        return environmentPriority(foundNormal, foundSoul, foundLava);
    }

    private static DamageSource fireDamageSource(LivingEntity target, ServerPlayer player) {
        return new DamageSource(
                target.damageSources().damageTypes.getHolderOrThrow(DamageTypes.ON_FIRE),
                player,
                player
        );
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

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown retaliatory flame error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid retaliatory flame ability {}: {}", abilityId, message);
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
            int checkIntervalTicks,
            double burnDurationSeconds,
            double soulFireDamage,
            double lavaDamage,
            ResourceLocation normalFireBlockTag,
            ResourceLocation soulFireBlockTag,
            ResourceLocation lavaFluidTag
    ) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 1200).optionalFieldOf("check_interval_ticks", 20)
                        .forGetter(Config::checkIntervalTicks),
                Codec.doubleRange(0.05D, 60.0D).optionalFieldOf("burn_duration_seconds", 3.0D)
                        .forGetter(Config::burnDurationSeconds),
                Codec.doubleRange(0.0D, 1000.0D).optionalFieldOf("soul_fire_damage", 2.0D)
                        .forGetter(Config::soulFireDamage),
                Codec.doubleRange(0.0D, 1000.0D).optionalFieldOf("lava_damage", 4.0D)
                        .forGetter(Config::lavaDamage),
                ResourceLocation.CODEC.optionalFieldOf(
                        "normal_fire_block_tag",
                        AbilityMod.id("retaliatory_flame_normal_fire")
                ).forGetter(Config::normalFireBlockTag),
                ResourceLocation.CODEC.optionalFieldOf(
                        "soul_fire_block_tag",
                        AbilityMod.id("retaliatory_flame_soul_fire")
                ).forGetter(Config::soulFireBlockTag),
                ResourceLocation.CODEC.optionalFieldOf(
                        "lava_fluid_tag",
                        AbilityMod.id("retaliatory_flame_lava")
                ).forGetter(Config::lavaFluidTag)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public record ResolvedRank(double radius) {
    }

    enum Environment {
        NONE,
        NORMAL_FIRE,
        SOUL_FIRE,
        LAVA
    }

    private record ActiveComponent(Config config, ResolvedRank rank) {
    }

    private record Activation(ActiveComponent component, Environment environment) {
    }
}
