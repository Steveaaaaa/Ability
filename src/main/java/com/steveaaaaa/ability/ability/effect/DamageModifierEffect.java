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
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class DamageModifierEffect {
    public static final ResourceLocation DAMAGE_MODIFIER = AbilityMod.id("damage_modifier");
    public static final ResourceLocation DAMAGE_REDUCTION = AbilityMod.id("damage_reduction");
    private static final Set<String> OUTGOING_KEYS = Set.of("damage_multiplier", "flat_damage");
    private static final Set<String> INCOMING_KEYS = Set.of("damage_multiplier", "flat_reduction");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private DamageModifierEffect() {
    }

    public static void process(LivingIncomingDamageEvent event) {
        float damage = event.getAmount();
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer attacker) {
            Adjustment outgoing = collect(attacker, event.getEntity(), source, DAMAGE_MODIFIER);
            damage = applyOutgoing(damage, outgoing.multiplier(), outgoing.flatAmount());
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            Adjustment incoming = collect(victim, victim, source, DAMAGE_REDUCTION);
            damage = applyIncoming(damage, incoming.multiplier(), incoming.flatAmount());
        }
        event.setAmount(damage);
    }

    static float applyOutgoing(float damage, double multiplier, double flatDamage) {
        return safeDamage((double) damage * multiplier + flatDamage);
    }

    static float applyIncoming(float damage, double multiplier, double flatReduction) {
        return safeDamage((double) damage * multiplier - flatReduction);
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> merged = new LinkedHashMap<>(earlier.values());
        merged.putAll(later.values());
        return new RankValues(merged);
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        try {
            parse(Config.CODEC, definition.effect().config(), "effect.config");
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
        }

        Set<String> allowedKeys = definition.effect().type().equals(DAMAGE_MODIFIER)
                ? OUTGOING_KEYS
                : INCOMING_KEYS;
        RankValues merged = new RankValues(Map.of());
        for (int index = 0; index < definition.ranks().values().size(); index++) {
            try {
                RankValues current = parse(
                        RankValues.CODEC,
                        definition.ranks().values().get(index),
                        "ranks.values[" + index + "]"
                );
                if (current.values().isEmpty()) {
                    errors.add("ranks.values[" + index + "]: must define at least one damage parameter");
                }
                int rankIndex = index;
                current.values().keySet().stream()
                        .filter(key -> !allowedKeys.contains(key))
                        .forEach(key -> errors.add(
                                "ranks.values[" + rankIndex + "]." + key + ": unsupported damage parameter"
                        ));
                merged = merge(merged, current);
                validateMergedValues(merged, allowedKeys, index, errors);
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
            }
        }
        return List.copyOf(errors);
    }

    private static Adjustment collect(
            ServerPlayer owner,
            LivingEntity target,
            DamageSource source,
            ResourceLocation effectType
    ) {
        double multiplier = 1.0D;
        double flatAmount = 0.0D;
        Registry<AbilityDefinition> abilities = owner.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> sorted = abilities.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location()))
                .toList();
        for (Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition> entry : sorted) {
            ResourceLocation abilityId = entry.getKey().location();
            try {
                List<CompositeEffect.ComponentView> components =
                        CompositeEffect.componentsOfType(entry.getValue(), effectType);
                if (components.isEmpty()) {
                    continue;
                }
                Optional<AbilityService.ActiveAbility> active = AbilityService.active(owner, abilityId);
                if (active.isEmpty()) {
                    continue;
                }
                for (CompositeEffect.ComponentView component : components) {
                    AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active.get(), component);
                    Config config = parse(Config.CODEC, component.config(), "effect.config");
                    if (!matches(config, target, source)) {
                        continue;
                    }
                    RankValues values = mergeRanks(projected);
                    double abilityMultiplier = values.values().getOrDefault("damage_multiplier", 1.0D);
                    String flatKey = effectType.equals(DAMAGE_MODIFIER) ? "flat_damage" : "flat_reduction";
                    double abilityFlatAmount = values.values().getOrDefault(flatKey, 0.0D);
                    if (!Double.isFinite(abilityMultiplier)
                            || abilityMultiplier < 0.0D
                            || abilityMultiplier > 100.0D) {
                        throw new IllegalArgumentException("damage_multiplier must be finite and between 0 and 100");
                    }
                    if (!Double.isFinite(abilityFlatAmount)
                            || abilityFlatAmount < 0.0D
                            || abilityFlatAmount > 1_000_000.0D) {
                        throw new IllegalArgumentException(flatKey + " must be finite and between 0 and 1000000");
                    }
                    multiplier *= abilityMultiplier;
                    flatAmount += abilityFlatAmount;
                }
            } catch (RuntimeException exception) {
                logInvalidOnce(abilityId, exception.getMessage());
            }
        }
        return new Adjustment(multiplier, flatAmount);
    }

    private static RankValues mergeRanks(AbilityService.ActiveAbility active) {
        RankValues merged = new RankValues(Map.of());
        for (int index = 0; index < active.unlockedRankValues().size(); index++) {
            RankValues current = parse(
                    RankValues.CODEC,
                    active.unlockedRankValues().get(index),
                    "ranks.values[" + index + "]"
            );
            merged = merge(merged, current);
        }
        return merged;
    }

    private static boolean matches(Config config, LivingEntity target, DamageSource source) {
        if (config.directness() == Directness.DIRECT && !source.isDirect()) {
            return false;
        }
        if (config.directness() == Directness.INDIRECT && source.isDirect()) {
            return false;
        }
        if (!config.damageTypeTags().isEmpty() && config.damageTypeTags().stream().noneMatch(tag ->
                source.is(TagKey.create(Registries.DAMAGE_TYPE, tag)))) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (!config.attackerEntityTypeTags().isEmpty()
                && (attacker == null || config.attackerEntityTypeTags().stream().noneMatch(tag ->
                attacker.getType().is(TagKey.create(Registries.ENTITY_TYPE, tag))))) {
            return false;
        }
        if (!config.targetEntityTypeTags().isEmpty() && config.targetEntityTypeTags().stream().noneMatch(tag ->
                target.getType().is(TagKey.create(Registries.ENTITY_TYPE, tag)))) {
            return false;
        }
        if (healthRatio(target.getHealth(), target.getMaxHealth()) < config.minimumTargetHealthRatio()) {
            return false;
        }
        return matchesTargetState(config.targetState(), target);
    }

    static double healthRatio(float health, float maximumHealth) {
        return maximumHealth <= 0.0F ? 0.0D : Math.clamp((double) health / maximumHealth, 0.0D, 1.0D);
    }

    static boolean matchesTargetState(TargetState targetState, LivingEntity target) {
        return switch (targetState) {
            case ANY -> true;
            case MOB_WITHOUT_TARGET -> target instanceof Mob mob && mob.getTarget() == null;
        };
    }

    private static void validateMergedValues(
            RankValues values,
            Set<String> allowedKeys,
            int rankIndex,
            List<String> errors
    ) {
        Double multiplier = values.values().get("damage_multiplier");
        if (multiplier != null
                && (!Double.isFinite(multiplier) || multiplier < 0.0D || multiplier > 100.0D)) {
            errors.add("ranks.values[" + rankIndex + "].damage_multiplier: must be finite and between 0 and 100");
        }
        for (String key : allowedKeys) {
            if (!key.equals("damage_multiplier")) {
                Double amount = values.values().get(key);
                if (amount != null
                        && (!Double.isFinite(amount) || amount < 0.0D || amount > 1_000_000.0D)) {
                    errors.add(
                            "ranks.values[" + rankIndex + "]." + key
                                    + ": must be finite and between 0 and 1000000"
                    );
                }
            }
        }
    }

    private static float safeDamage(double value) {
        if (!Double.isFinite(value)) {
            return Float.MAX_VALUE;
        }
        return (float) Math.clamp(value, 0.0D, Float.MAX_VALUE);
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown damage modifier error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid damage modifier ability {}: {}", abilityId, message);
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
            List<ResourceLocation> damageTypeTags,
            List<ResourceLocation> attackerEntityTypeTags,
            List<ResourceLocation> targetEntityTypeTags,
            Directness directness,
            TargetState targetState,
            double minimumTargetHealthRatio
    ) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.listOf().optionalFieldOf("damage_type_tags", List.of())
                        .forGetter(Config::damageTypeTags),
                ResourceLocation.CODEC.listOf().optionalFieldOf("attacker_entity_type_tags", List.of())
                        .forGetter(Config::attackerEntityTypeTags),
                ResourceLocation.CODEC.listOf().optionalFieldOf("target_entity_type_tags", List.of())
                        .forGetter(Config::targetEntityTypeTags),
                Directness.CODEC.optionalFieldOf("directness", Directness.ANY).forGetter(Config::directness),
                TargetState.CODEC.optionalFieldOf("target_state", TargetState.ANY).forGetter(Config::targetState),
                Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("minimum_target_health_ratio", 0.0D)
                        .forGetter(Config::minimumTargetHealthRatio)
        ).apply(instance, Config::new));

        public Config {
            damageTypeTags = List.copyOf(damageTypeTags);
            attackerEntityTypeTags = List.copyOf(attackerEntityTypeTags);
            targetEntityTypeTags = List.copyOf(targetEntityTypeTags);
        }
    }

    public enum Directness implements StringRepresentable {
        ANY("any"),
        DIRECT("direct"),
        INDIRECT("indirect");

        public static final Codec<Directness> CODEC = StringRepresentable.fromEnum(Directness::values);
        private final String serializedName;

        Directness(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    public enum TargetState implements StringRepresentable {
        ANY("any"),
        MOB_WITHOUT_TARGET("mob_without_target");

        public static final Codec<TargetState> CODEC = StringRepresentable.fromEnum(TargetState::values);
        private final String serializedName;

        TargetState(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    private record Adjustment(double multiplier, double flatAmount) {
    }
}
