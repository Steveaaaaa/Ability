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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class BlastExcavationEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("blast_excavation");
    private static final Set<String> RANK_KEYS = Set.of("self_damage_reduction_percent");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, SpawnedCharge> CHARGES = new ConcurrentHashMap<>();

    private BlastExcavationEffect() {
    }

    public static void placeCharge(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.OFF_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Optional<ActiveComponent> selected = activeComponents(player).stream()
                .filter(component -> player.getMainHandItem().is(TagKey.create(
                        Registries.ITEM,
                        component.config().requiredMainHandToolTag()
                )))
                .filter(component -> event.getItemStack().is(component.config().explosiveItem()))
                .max(Comparator.comparingDouble(component -> component.rank().selfDamageReduction()));
        if (selected.isEmpty() || event.getFace() == null) {
            return;
        }
        ActiveComponent component = selected.get();
        Vec3 position = Vec3.atCenterOf(event.getPos().relative(event.getFace()));
        PrimedTnt charge = new PrimedTnt(level, position.x, position.y, position.z, player);
        charge.setFuse(component.config().fuseTicks());
        if (!level.addFreshEntity(charge)) {
            return;
        }
        CHARGES.put(charge.getUUID(), new SpawnedCharge(
                player.getUUID(),
                component.rank().selfDamageReduction(),
                level.getGameTime() + 200L
        ));
        if (!player.getAbilities().instabuild) {
            event.getItemStack().shrink(1);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    public static void reduceSelfDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSource().getDirectEntity() instanceof PrimedTnt charge)) {
            return;
        }
        SpawnedCharge state = CHARGES.get(charge.getUUID());
        if (state == null || !state.owner().equals(player.getUUID())) {
            return;
        }
        event.setAmount(reduceDamage(event.getAmount(), state.reduction()));
    }

    public static void cleanup(long gameTime) {
        CHARGES.entrySet().removeIf(entry -> entry.getValue().expiresAt() < gameTime);
    }

    static float reduceDamage(float damage, double reduction) {
        return (float) Math.max(0.0D, damage * (1.0D - Math.clamp(reduction, 0.0D, 1.0D)));
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        try {
            Config config = parse(Config.CODEC, definition.effect().config(), "effect.config");
            BuiltInRegistries.ITEM.getKey(config.explosiveItem());
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
        }
        RankValues merged = new RankValues(Map.of());
        for (int index = 0; index < definition.ranks().values().size(); index++) {
            int rankIndex = index;
            try {
                RankValues current = parse(RankValues.CODEC, definition.ranks().values().get(index),
                        "ranks.values[" + index + "]");
                current.values().keySet().stream().filter(key -> !RANK_KEYS.contains(key)).forEach(key ->
                        errors.add("ranks.values[" + rankIndex + "]." + key + ": unsupported blast parameter"));
                merged = merge(merged, current);
                requiredReduction(merged.values().get("self_damage_reduction_percent"));
            } catch (IllegalArgumentException exception) {
                errors.add("ranks.values[" + index + "]: " + exception.getMessage());
            }
        }
        return List.copyOf(errors);
    }

    private static List<ActiveComponent> activeComponents(ServerPlayer player) {
        ArrayList<ActiveComponent> result = new ArrayList<>();
        Registry<AbilityDefinition> abilities = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        for (Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition> entry : abilities.entrySet()) {
            ResourceLocation abilityId = entry.getKey().location();
            try {
                Optional<AbilityService.ActiveAbility> active = AbilityService.active(player, abilityId);
                if (active.isEmpty()) continue;
                for (CompositeEffect.ComponentView component : CompositeEffect.componentsOfType(entry.getValue(), TYPE)) {
                    AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active.get(), component);
                    Config config = parse(Config.CODEC, component.config(), "effect.config");
                    RankValues values = mergeRanks(projected);
                    result.add(new ActiveComponent(config, new ResolvedRank(requiredReduction(
                            values.values().get("self_damage_reduction_percent")))));
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
            merged = merge(merged, parse(RankValues.CODEC, active.unlockedRankValues().get(index),
                    "ranks.values[" + index + "]"));
        }
        return merged;
    }

    private static double requiredReduction(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0.0D || value > 100.0D) {
            throw new IllegalArgumentException("self_damage_reduction_percent must be between 0 and 100");
        }
        return value / 100.0D;
    }

    private static void logInvalidOnce(ResourceLocation id, String detail) {
        if (LOGGED_INVALID_DEFINITIONS.add(id + "|" + detail)) {
            AbilityMod.LOGGER.error("Invalid blast excavation ability {}: {}", id, detail);
        }
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder();
        Optional<T> parsed = codec.parse(input).resultOrPartial(error::append);
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }

    public record Config(ResourceLocation requiredMainHandToolTag, Item explosiveItem, int fuseTicks) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("required_main_hand_tool_tag")
                        .forGetter(Config::requiredMainHandToolTag),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("explosive_item").forGetter(Config::explosiveItem),
                Codec.intRange(0, 200).optionalFieldOf("fuse_ticks", 0).forGetter(Config::fuseTicks)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);
        public RankValues { values = Map.copyOf(values); }
    }

    public record ResolvedRank(double selfDamageReduction) {
    }

    private record ActiveComponent(Config config, ResolvedRank rank) {
    }

    private record SpawnedCharge(UUID owner, double reduction, long expiresAt) {
    }
}
