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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class GreedEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("greed");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Map<ResourceLocation, Holder<Attribute>>> APPLIED = new ConcurrentHashMap<>();

    private GreedEffect() {
    }

    public static void reconcile(ServerPlayer player) {
        Map<ResourceLocation, DesiredModifier> desired = desiredModifiers(player);
        Map<ResourceLocation, Holder<Attribute>> previous = APPLIED.getOrDefault(player.getUUID(), Map.of());
        previous.forEach((modifierId, attribute) -> {
            if (!desired.containsKey(modifierId)) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.removeModifier(modifierId);
                }
            }
        });

        LinkedHashMap<ResourceLocation, Holder<Attribute>> updated = new LinkedHashMap<>();
        desired.forEach((modifierId, desiredModifier) -> {
            AttributeInstance instance = player.getAttribute(desiredModifier.attribute());
            if (instance == null) {
                return;
            }
            AttributeModifier expected = new AttributeModifier(
                    modifierId,
                    desiredModifier.amount(),
                    AttributeModifier.Operation.ADD_VALUE
            );
            if (!expected.equals(instance.getModifier(modifierId))) {
                instance.removeModifier(modifierId);
                instance.addTransientModifier(expected);
            }
            updated.put(modifierId, desiredModifier.attribute());
        });
        if (updated.isEmpty()) {
            APPLIED.remove(player.getUUID());
        } else {
            APPLIED.put(player.getUUID(), Map.copyOf(updated));
        }
    }

    public static void forget(ServerPlayer player) {
        Map<ResourceLocation, Holder<Attribute>> applied = APPLIED.remove(player.getUUID());
        if (applied != null) {
            applied.forEach((modifierId, attribute) -> {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.removeModifier(modifierId);
                }
            });
        }
    }

    static boolean rankUnlocksTool(int rank, ResourceLocation heldItemTag, List<ToolTier> tiers) {
        return tiers.stream().anyMatch(tier -> tier.minimumRank() <= rank && tier.itemTag().equals(heldItemTag));
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        try {
            Config config = parse(Config.CODEC, definition.effect().config(), "effect.config");
            ArrayList<String> errors = new ArrayList<>();
            if (BuiltInRegistries.ATTRIBUTE.getHolder(config.attribute()).isEmpty()) {
                errors.add("effect.config.attribute: unknown attribute " + config.attribute());
            }
            int maximumRank = definition.ranks().values().size();
            for (int index = 0; index < config.toolTiers().size(); index++) {
                if (config.toolTiers().get(index).minimumRank() > maximumRank) {
                    errors.add("effect.config.tool_tiers[" + index + "].minimum_rank: exceeds ability rank count");
                }
            }
            return List.copyOf(errors);
        } catch (IllegalArgumentException exception) {
            return List.of(exception.getMessage());
        }
    }

    private static Map<ResourceLocation, DesiredModifier> desiredModifiers(ServerPlayer player) {
        LinkedHashMap<ResourceLocation, DesiredModifier> desired = new LinkedHashMap<>();
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
                    boolean matches = config.toolTiers().stream().anyMatch(tier ->
                            tier.minimumRank() <= projected.rank()
                                    && player.getMainHandItem().is(TagKey.create(Registries.ITEM, tier.itemTag()))
                    );
                    if (!matches) {
                        continue;
                    }
                    Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(config.attribute())
                            .orElseThrow(() -> new IllegalArgumentException("Unknown attribute: " + config.attribute()));
                    desired.put(modifierId(abilityId, component.index()), new DesiredModifier(attribute, config.amount()));
                }
            } catch (RuntimeException exception) {
                logInvalidOnce(abilityId, exception.getMessage());
            }
        }
        return Map.copyOf(desired);
    }

    private static ResourceLocation modifierId(ResourceLocation abilityId, int componentIndex) {
        return AbilityMod.id("greed/" + abilityId.getNamespace() + "/" + abilityId.getPath() + "/" + componentIndex);
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown greed error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid greed ability {}: {}", abilityId, message);
        }
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder();
        Optional<T> parsed = codec.parse(input).resultOrPartial(message -> error.append(message));
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }

    public record Config(ResourceLocation attribute, double amount, List<ToolTier> toolTiers) {
        private static final Codec<Config> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf(
                        "attribute",
                        ResourceLocation.withDefaultNamespace("player.block_interaction_range")
                ).forGetter(Config::attribute),
                Codec.doubleRange(0.0D, 64.0D).optionalFieldOf("amount", 3.0D).forGetter(Config::amount),
                ToolTier.CODEC.listOf().fieldOf("tool_tiers").forGetter(Config::toolTiers)
        ).apply(instance, Config::new));
        public static final Codec<Config> CODEC = RAW_CODEC.flatXmap(Config::validate, Config::validate);

        public Config {
            toolTiers = List.copyOf(toolTiers);
        }

        private static DataResult<Config> validate(Config config) {
            if (config.toolTiers().isEmpty()) {
                return DataResult.error(() -> "tool_tiers must contain at least one entry");
            }
            long distinct = config.toolTiers().stream().map(ToolTier::minimumRank).distinct().count();
            return distinct == config.toolTiers().size()
                    ? DataResult.success(config)
                    : DataResult.error(() -> "tool_tiers minimum_rank values must be unique");
        }
    }

    public record ToolTier(int minimumRank, ResourceLocation itemTag) {
        public static final Codec<ToolTier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 100).fieldOf("minimum_rank").forGetter(ToolTier::minimumRank),
                ResourceLocation.CODEC.fieldOf("item_tag").forGetter(ToolTier::itemTag)
        ).apply(instance, ToolTier::new));
    }

    private record DesiredModifier(Holder<Attribute> attribute, double amount) {
    }
}
