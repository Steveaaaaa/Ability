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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class ExhaustionEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("exhaustion");
    private static final Set<String> RANK_KEYS = Set.of("poison_level_cap", "wither_level_cap");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private ExhaustionEffect() {
    }

    public static void process(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F
                || !(event.getEntity() instanceof LivingEntity target)
                || !target.isAlive()
                || target.isInvertedHealAndHarm()
                || !(event.getSource().getDirectEntity() instanceof Arrow arrow)
                || !(event.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer attacker)
                || !hasInstantDamage(arrow)) {
            return;
        }

        List<ActiveComponent> components = activeComponents(attacker);
        if (components.isEmpty()) {
            return;
        }
        int poisonLevelCap = components.stream().mapToInt(component -> component.rank().poisonLevelCap()).max().orElse(0);
        int witherLevelCap = components.stream().mapToInt(component -> component.rank().witherLevelCap()).max().orElse(0);
        int maximumDurationTicks = components.stream()
                .mapToInt(component -> component.config().maximumDurationTicks())
                .max()
                .orElse(0);

        MobEffectInstance poison = target.getEffect(MobEffects.POISON);
        MobEffectInstance wither = target.getEffect(MobEffects.WITHER);
        if (poison == null && wither == null) {
            return;
        }

        double poisonDamage = poison == null ? 0.0D : settledDamage(
                poison,
                poisonLevelCap,
                0.8D,
                maximumDurationTicks
        );
        double witherDamage = wither == null ? 0.0D : settledDamage(
                wither,
                witherLevelCap,
                0.5D,
                maximumDurationTicks
        );
        if (poison != null) {
            target.removeEffect(MobEffects.POISON);
        }
        if (wither != null) {
            target.removeEffect(MobEffects.WITHER);
        }

        if (poisonDamage > 0.0D && target.isAlive()) {
            target.invulnerableTime = 0;
            target.hurt(poisonDamageSource(target, arrow, attacker), safeDamage(poisonDamage));
        }
        if (witherDamage > 0.0D && target.isAlive()) {
            target.invulnerableTime = 0;
            target.hurt(witherDamageSource(target, arrow, attacker), safeDamage(witherDamage));
        }
    }

    static boolean hasInstantDamage(Arrow arrow) {
        PotionContents contents = arrow.getPickupItemStackOrigin()
                .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect().equals(MobEffects.HARM)) {
                return true;
            }
        }
        return false;
    }

    static double settledDamage(
            MobEffectInstance effect,
            int levelCap,
            double damagePerSecond,
            int maximumDurationTicks
    ) {
        if (levelCap <= 0 || damagePerSecond <= 0.0D || maximumDurationTicks <= 0) {
            return 0.0D;
        }
        int durationTicks = effect.isInfiniteDuration()
                ? maximumDurationTicks
                : Math.clamp(effect.getDuration(), 0, maximumDurationTicks);
        int visibleLevel = effect.getAmplifier() + 1;
        int appliedLevel = Math.min(visibleLevel, levelCap);
        return damagePerSecond * ((double) durationTicks / 20.0D) * Math.scalb(1.0D, appliedLevel);
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static ResolvedRank resolve(RankValues values) {
        Integer poisonLevelCap = values.values().get("poison_level_cap");
        Integer witherLevelCap = values.values().get("wither_level_cap");
        if (poisonLevelCap == null || poisonLevelCap < 1 || poisonLevelCap > 32) {
            throw new IllegalArgumentException("poison_level_cap must be between 1 and 32");
        }
        if (witherLevelCap == null || witherLevelCap < 1 || witherLevelCap > 32) {
            throw new IllegalArgumentException("wither_level_cap must be between 1 and 32");
        }
        return new ResolvedRank(poisonLevelCap, witherLevelCap);
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
                                        + ": unsupported exhaustion parameter"
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

    private static List<ActiveComponent> activeComponents(net.minecraft.server.level.ServerPlayer player) {
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

    private static DamageSource poisonDamageSource(
            LivingEntity target,
            Arrow arrow,
            net.minecraft.server.level.ServerPlayer attacker
    ) {
        var registry = target.damageSources().damageTypes;
        Holder<net.minecraft.world.damagesource.DamageType> type = registry
                .getHolder(NeoForgeMod.POISON_DAMAGE)
                .orElse(registry.getHolderOrThrow(DamageTypes.MAGIC));
        return new DamageSource(type, arrow, attacker);
    }

    private static DamageSource witherDamageSource(
            LivingEntity target,
            Arrow arrow,
            net.minecraft.server.level.ServerPlayer attacker
    ) {
        return new DamageSource(
                target.damageSources().damageTypes.getHolderOrThrow(DamageTypes.WITHER),
                arrow,
                attacker
        );
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
        String message = detail == null ? "Unknown exhaustion error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid exhaustion ability {}: {}", abilityId, message);
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

    public record Config(int maximumDurationTicks) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(20, 72000).optionalFieldOf("maximum_duration_ticks", 72000)
                        .forGetter(Config::maximumDurationTicks)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Integer> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public record ResolvedRank(int poisonLevelCap, int witherLevelCap) {
    }

    private record ActiveComponent(Config config, ResolvedRank rank) {
    }
}
