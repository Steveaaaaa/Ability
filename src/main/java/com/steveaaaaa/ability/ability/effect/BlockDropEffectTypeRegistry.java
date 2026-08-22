package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

public final class BlockDropEffectTypeRegistry {
    private static final Map<ResourceLocation, RegisteredType<?, ?>> TYPES = new LinkedHashMap<>();
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    static {
        register(
                AbilityMod.id("associated_ore"),
                AssociatedOreEffect.Config.CODEC,
                AssociatedOreEffect.RankValues.CODEC,
                AssociatedOreEffect.RankValues::merge,
                AssociatedOreEffect::apply
        );
    }

    private BlockDropEffectTypeRegistry() {
    }

    public static synchronized <C, R> void register(
            ResourceLocation id,
            Codec<C> configCodec,
            Codec<R> rankCodec,
            RankMerger<R> rankMerger,
            BlockDropEffect<C, R> effect
    ) {
        if (TYPES.putIfAbsent(id, new RegisteredType<>(configCodec, rankCodec, rankMerger, effect)) != null) {
            throw new IllegalArgumentException("Duplicate block drop effect type: " + id);
        }
    }

    public static void process(BlockDropsEvent event, ServerPlayer player) {
        Registry<AbilityDefinition> abilities = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        for (Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition> entry : abilities.entrySet()) {
            ResourceLocation abilityId = entry.getKey().location();
            AbilityDefinition definition = entry.getValue();
            RegisteredType<?, ?> type = TYPES.get(definition.effect().type());
            if (type == null) {
                continue;
            }

            try {
                AbilityService.active(player, abilityId)
                        .ifPresent(active -> type.apply(event, active));
            } catch (RuntimeException exception) {
                logInvalidOnce(abilityId, exception.getMessage());
            }
        }
    }

    public static boolean isRegistered(ResourceLocation id) {
        return TYPES.containsKey(id);
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown ability effect error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid ability effect {}: {}", abilityId, message);
        }
    }

    @FunctionalInterface
    public interface RankMerger<R> {
        R merge(R earlier, R later);
    }

    @FunctionalInterface
    public interface BlockDropEffect<C, R> {
        void apply(BlockDropsEvent event, C config, R rankValues);
    }

    private record RegisteredType<C, R>(
            Codec<C> configCodec,
            Codec<R> rankCodec,
            RankMerger<R> rankMerger,
            BlockDropEffect<C, R> effect
    ) {
        private void apply(BlockDropsEvent event, AbilityService.ActiveAbility active) {
            C config = parse(configCodec, active.definition().effect().config(), "effect.config");
            R merged = null;
            for (int index = 0; index < active.unlockedRankValues().size(); index++) {
                R current = parse(rankCodec, active.unlockedRankValues().get(index), "ranks.values[" + index + "]");
                merged = merged == null ? current : rankMerger.merge(merged, current);
            }
            if (merged != null) {
                effect.apply(event, config, merged);
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
    }
}
