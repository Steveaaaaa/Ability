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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class AttributeModifierEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("attribute_modifier");
    private static final Pattern AMOUNT_KEY = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Map<UUID, Map<ResourceLocation, AppliedModifier>> APPLIED = new HashMap<>();
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private AttributeModifierEffect() {
    }

    public static void reconcile(ServerPlayer player) {
        Map<ResourceLocation, DesiredModifier> desired = desiredModifiers(player);
        Map<ResourceLocation, AppliedModifier> previous = APPLIED.getOrDefault(player.getUUID(), Map.of());

        previous.forEach((modifierId, applied) -> {
            DesiredModifier replacement = desired.get(modifierId);
            if (replacement == null || !replacement.attribute().equals(applied.attribute())) {
                AttributeInstance instance = player.getAttribute(applied.attribute());
                if (instance != null) {
                    instance.removeModifier(modifierId);
                }
            }
        });

        LinkedHashMap<ResourceLocation, AppliedModifier> updated = new LinkedHashMap<>();
        desired.forEach((modifierId, modifier) -> {
            AttributeInstance instance = player.getAttribute(modifier.attribute());
            if (instance == null) {
                return;
            }
            AttributeModifier current = instance.getModifier(modifierId);
            AttributeModifier expected = new AttributeModifier(modifierId, modifier.amount(), modifier.operation());
            if (!expected.equals(current)) {
                instance.removeModifier(modifierId);
                instance.addTransientModifier(expected);
            }
            updated.put(modifierId, new AppliedModifier(modifier.attribute()));
        });

        if (updated.isEmpty()) {
            APPLIED.remove(player.getUUID());
        } else {
            APPLIED.put(player.getUUID(), Map.copyOf(updated));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void forget(ServerPlayer player) {
        APPLIED.remove(player.getUUID());
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        Config config;
        try {
            config = parse(Config.CODEC, definition.effect().config(), "effect.config");
        } catch (IllegalArgumentException exception) {
            return List.of(exception.getMessage());
        }

        for (int index = 0; index < config.modifiers().size(); index++) {
            ModifierConfig modifier = config.modifiers().get(index);
            if (BuiltInRegistries.ATTRIBUTE.getHolder(modifier.attribute()).isEmpty()) {
                errors.add("effect.config.modifiers[" + index + "].attribute: unknown attribute " + modifier.attribute());
            }
        }

        Set<String> configuredKeys = config.modifiers().stream()
                .map(ModifierConfig::amountKey)
                .collect(java.util.stream.Collectors.toSet());
        LinkedHashMap<String, Double> merged = new LinkedHashMap<>();
        for (int index = 0; index < definition.ranks().values().size(); index++) {
            int rankIndex = index;
            try {
                RankValues values = parse(
                        RankValues.CODEC,
                        definition.ranks().values().get(index),
                        "ranks.values[" + index + "]"
                );
                values.amounts().keySet().stream()
                        .filter(key -> !configuredKeys.contains(key))
                        .forEach(key -> errors.add(
                                "ranks.values[" + rankIndex + "]." + key + ": no modifier uses this amount key"
                        ));
                merged.putAll(values.amounts());
                for (String key : configuredKeys) {
                    if (!merged.containsKey(key)) {
                        errors.add("ranks.values[" + index + "]: missing amount key " + key);
                    }
                }
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
            }
        }
        return List.copyOf(errors);
    }

    static ResourceLocation modifierId(ResourceLocation abilityId, int index) {
        return AbilityMod.id("attribute/" + abilityId.getNamespace() + "/" + abilityId.getPath() + "/" + index);
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> merged = new LinkedHashMap<>(earlier.amounts());
        merged.putAll(later.amounts());
        return new RankValues(merged);
    }

    private static Map<ResourceLocation, DesiredModifier> desiredModifiers(ServerPlayer player) {
        LinkedHashMap<ResourceLocation, DesiredModifier> desired = new LinkedHashMap<>();
        Registry<AbilityDefinition> abilities = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        for (Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition> entry : abilities.entrySet()) {
            ResourceLocation abilityId = entry.getKey().location();
            AbilityDefinition definition = entry.getValue();
            if (!definition.effect().type().equals(TYPE)) {
                continue;
            }
            try {
                Optional<AbilityService.ActiveAbility> active = AbilityService.active(player, abilityId);
                if (active.isEmpty()) {
                    continue;
                }
                Config config = parse(Config.CODEC, definition.effect().config(), "effect.config");
                RankValues values = null;
                for (int rank = 0; rank < active.get().unlockedRankValues().size(); rank++) {
                    RankValues current = parse(
                            RankValues.CODEC,
                            active.get().unlockedRankValues().get(rank),
                            "ranks.values[" + rank + "]"
                    );
                    values = values == null ? current : merge(values, current);
                }
                if (values == null) {
                    continue;
                }
                for (int index = 0; index < config.modifiers().size(); index++) {
                    ModifierConfig modifier = config.modifiers().get(index);
                    Double amount = values.amounts().get(modifier.amountKey());
                    Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(modifier.attribute())
                            .orElseThrow(() -> new IllegalArgumentException("Unknown attribute: " + modifier.attribute()));
                    if (amount == null) {
                        throw new IllegalArgumentException("Missing amount key: " + modifier.amountKey());
                    }
                    desired.put(
                            modifierId(abilityId, index),
                            new DesiredModifier(attribute, modifier.operation(), amount)
                    );
                }
            } catch (RuntimeException exception) {
                logInvalidOnce(abilityId, exception.getMessage());
            }
        }
        return Map.copyOf(desired);
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown attribute modifier error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid attribute modifier ability {}: {}", abilityId, message);
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

    public record Config(List<ModifierConfig> modifiers) {
        private static final Codec<Config> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ModifierConfig.CODEC.listOf().fieldOf("modifiers").forGetter(Config::modifiers)
        ).apply(instance, Config::new));
        public static final Codec<Config> CODEC = RAW_CODEC.flatXmap(Config::validate, Config::validate);

        public Config {
            modifiers = List.copyOf(modifiers);
        }

        private static DataResult<Config> validate(Config config) {
            return config.modifiers().isEmpty()
                    ? DataResult.error(() -> "modifiers must contain at least one entry")
                    : DataResult.success(config);
        }
    }

    public record ModifierConfig(
            ResourceLocation attribute,
            AttributeModifier.Operation operation,
            String amountKey
    ) {
        private static final Codec<String> AMOUNT_KEY_CODEC = Codec.STRING.comapFlatMap(
                value -> AMOUNT_KEY.matcher(value).matches()
                        ? DataResult.success(value)
                        : DataResult.error(() -> "Invalid amount_key: " + value),
                value -> value
        );
        public static final Codec<ModifierConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("attribute").forGetter(ModifierConfig::attribute),
                AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(ModifierConfig::operation),
                AMOUNT_KEY_CODEC.fieldOf("amount_key").forGetter(ModifierConfig::amountKey)
        ).apply(instance, ModifierConfig::new));
    }

    public record RankValues(Map<String, Double> amounts) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(
                Codec.STRING,
                Codec.doubleRange(-1_000_000.0D, 1_000_000.0D)
        ).xmap(RankValues::new, RankValues::amounts);

        public RankValues {
            amounts = Map.copyOf(amounts);
        }
    }

    private record DesiredModifier(
            Holder<Attribute> attribute,
            AttributeModifier.Operation operation,
            double amount
    ) {
    }

    private record AppliedModifier(Holder<Attribute> attribute) {
    }
}
