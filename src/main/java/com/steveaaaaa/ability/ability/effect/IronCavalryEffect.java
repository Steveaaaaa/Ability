package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.presentation.AbilityPresentationService;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class IronCavalryEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("iron_cavalry");
    private static final ResourceLocation PIG_ARMOR_MODIFIER = AbilityMod.id("iron_cavalry/pig_armor");
    private static final ResourceLocation PIG_ARMOR_CUE = AbilityMod.id("pig_armor");
    private static final Set<String> RANK_KEYS = Set.of("damage_bonus_percent", "pig_armor_share_percent");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, LivingEntity> MODIFIED_MOUNTS = new ConcurrentHashMap<>();

    private IronCavalryEffect() {
    }

    public static void modifyOutgoingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || event.getSource().getDirectEntity() != player
                || !(player.getVehicle() instanceof LivingEntity mount)) {
            return;
        }
        double bonus = activeComponents(player).stream()
                .filter(component -> mount.getType().is(entityTag(component.config().mountEntityTypeTag())))
                .mapToDouble(component -> component.rank().damageBonus())
                .max()
                .orElse(0.0D);
        event.setAmount(applyDamageBonus(event.getAmount(), bonus));
    }

    public static void reconcileMountArmor(ServerPlayer player) {
        LivingEntity previousMount = MODIFIED_MOUNTS.remove(player.getUUID());
        LivingEntity currentMount = player.getVehicle() instanceof LivingEntity living ? living : null;
        if (previousMount != null && previousMount != currentMount) {
            removeModifier(previousMount);
            stopPigArmor(player, previousMount);
        }
        if (currentMount == null) {
            return;
        }
        double share = activeComponents(player).stream()
                .filter(component -> currentMount.getType().is(entityTag(component.config().pigEntityTypeTag())))
                .mapToDouble(component -> component.rank().pigArmorShare())
                .max()
                .orElse(0.0D);
        if (share <= 0.0D) {
            removeModifier(currentMount);
            if (previousMount == currentMount) {
                stopPigArmor(player, currentMount);
            }
            return;
        }
        AttributeInstance armor = currentMount.getAttribute(Attributes.ARMOR);
        if (armor == null) {
            return;
        }
        AttributeModifier expected = new AttributeModifier(
                PIG_ARMOR_MODIFIER,
                sharedArmor(player.getArmorValue(), share),
                AttributeModifier.Operation.ADD_VALUE
        );
        if (!expected.equals(armor.getModifier(PIG_ARMOR_MODIFIER))) {
            armor.removeModifier(PIG_ARMOR_MODIFIER);
            armor.addTransientModifier(expected);
        }
        MODIFIED_MOUNTS.put(player.getUUID(), currentMount);
        syncPigArmor(player, currentMount);
    }

    public static void forget(ServerPlayer player) {
        LivingEntity mount = MODIFIED_MOUNTS.remove(player.getUUID());
        if (mount != null) {
            removeModifier(mount);
            stopPigArmor(player, mount);
        }
    }

    private static void syncPigArmor(ServerPlayer player, LivingEntity mount) {
        if (!(mount instanceof Pig pig)) {
            return;
        }
        AbilityPresentationService.sendTracking(pig, AbilityCue.start(
                AbilityMod.id("iron_cavalry"),
                PIG_ARMOR_CUE,
                player.getId(),
                pig.getId(),
                pig.position(),
                net.minecraft.world.phys.Vec3.ZERO,
                0,
                30,
                pig.getUUID().getMostSignificantBits() ^ pig.getUUID().getLeastSignificantBits(),
                pig.getUUID().getLeastSignificantBits()
        ));
    }

    private static void stopPigArmor(ServerPlayer player, LivingEntity mount) {
        if (!(mount instanceof Pig pig)) {
            return;
        }
        AbilityCue cue = AbilityCue.start(
                AbilityMod.id("iron_cavalry"),
                PIG_ARMOR_CUE,
                player.getId(),
                pig.getId(),
                pig.position(),
                net.minecraft.world.phys.Vec3.ZERO,
                0,
                0,
                pig.getUUID().getMostSignificantBits() ^ pig.getUUID().getLeastSignificantBits(),
                pig.getUUID().getLeastSignificantBits()
        );
        AbilityPresentationService.sendTracking(pig, cue.asStop());
    }

    static float applyDamageBonus(float damage, double bonus) {
        return (float) Math.clamp(damage * (1.0D + Math.max(0.0D, bonus)), 0.0D, Float.MAX_VALUE);
    }

    static double sharedArmor(double playerArmor, double share) {
        return Math.max(0.0D, playerArmor) * Math.clamp(share, 0.0D, 10.0D);
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static ResolvedRank resolve(RankValues values) {
        return new ResolvedRank(
                requiredPercent(values.values().get("damage_bonus_percent"), "damage_bonus_percent") / 100.0D,
                requiredPercent(values.values().get("pig_armor_share_percent"), "pig_armor_share_percent") / 100.0D
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
                RankValues current = parse(RankValues.CODEC, definition.ranks().values().get(index),
                        "ranks.values[" + index + "]");
                current.values().keySet().stream().filter(key -> !RANK_KEYS.contains(key)).forEach(key ->
                        errors.add("ranks.values[" + rankIndex + "]." + key + ": unsupported iron cavalry parameter"));
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

    private static void removeModifier(Entity entity) {
        if (entity instanceof LivingEntity living) {
            AttributeInstance armor = living.getAttribute(Attributes.ARMOR);
            if (armor != null) {
                armor.removeModifier(PIG_ARMOR_MODIFIER);
            }
        }
    }

    private static List<ActiveComponent> activeComponents(ServerPlayer player) {
        ArrayList<ActiveComponent> result = new ArrayList<>();
        Registry<AbilityDefinition> abilities = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> sorted = abilities.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location())).toList();
        for (Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition> entry : sorted) {
            ResourceLocation abilityId = entry.getKey().location();
            try {
                Optional<AbilityService.ActiveAbility> active = AbilityService.active(player, abilityId);
                if (active.isEmpty()) continue;
                for (CompositeEffect.ComponentView component : CompositeEffect.componentsOfType(entry.getValue(), TYPE)) {
                    AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active.get(), component);
                    result.add(new ActiveComponent(
                            parse(Config.CODEC, component.config(), "effect.config"),
                            resolve(mergeRanks(projected))
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
            merged = merge(merged, parse(RankValues.CODEC, active.unlockedRankValues().get(index),
                    "ranks.values[" + index + "]"));
        }
        return merged;
    }

    private static TagKey<EntityType<?>> entityTag(ResourceLocation id) {
        return TagKey.create(Registries.ENTITY_TYPE, id);
    }

    private static double requiredPercent(Double value, String name) {
        if (value == null || !Double.isFinite(value) || value < 0.0D || value > 1000.0D) {
            throw new IllegalArgumentException(name + " must be finite and between 0 and 1000");
        }
        return value;
    }

    private static void logInvalidOnce(ResourceLocation id, String detail) {
        if (LOGGED_INVALID_DEFINITIONS.add(id + "|" + detail)) {
            AbilityMod.LOGGER.error("Invalid iron cavalry ability {}: {}", id, detail);
        }
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder();
        Optional<T> parsed = codec.parse(input).resultOrPartial(error::append);
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }

    public record Config(ResourceLocation mountEntityTypeTag, ResourceLocation pigEntityTypeTag) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("mount_entity_type_tag").forGetter(Config::mountEntityTypeTag),
                ResourceLocation.CODEC.fieldOf("pig_entity_type_tag").forGetter(Config::pigEntityTypeTag)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);
        public RankValues { values = Map.copyOf(values); }
    }

    public record ResolvedRank(double damageBonus, double pigArmorShare) {
    }

    private record ActiveComponent(Config config, ResolvedRank rank) {
    }
}
