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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Wolf;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class WolfPackEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("wolf_pack");
    private static final Set<String> RANK_KEYS = Set.of(
            "damage_bonus_percent",
            "dodge_chance_percent",
            "cooldown_seconds"
    );
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, WolfState> STATES = new ConcurrentHashMap<>();

    private WolfPackEffect() {
    }

    public static void processTick(Wolf wolf) {
        if (wolf.level().isClientSide()) {
            return;
        }
        if (!wolf.isAlive() || wolf.isRemoved()) {
            STATES.remove(wolf.getUUID());
            return;
        }
        long now = wolf.level().getGameTime();
        WolfState previous = STATES.getOrDefault(wolf.getUUID(), WolfState.EMPTY);
        boolean attacking = wolf.getTarget() != null;
        if (!(wolf.getOwner() instanceof ServerPlayer owner)) {
            STATES.remove(wolf.getUUID());
            return;
        }
        WolfState updated = previous.withAttacking(attacking);
        if (attacking && !previous.wasAttacking() && now >= previous.cooldownEndsAt()) {
            updated = activate(owner, now).orElse(updated);
        }
        if (!attacking && now >= updated.buffEndsAt() && now >= updated.cooldownEndsAt()) {
            STATES.remove(wolf.getUUID());
        } else {
            STATES.put(wolf.getUUID(), updated);
            emitAngryState(wolf, updated, now);
        }
    }

    private static void emitAngryState(Wolf wolf, WolfState state, long gameTime) {
        if (!isBuffActive(state, gameTime) || !(wolf.level() instanceof ServerLevel level)) {
            return;
        }
        int interval = 8;
        int phase = Math.floorMod(wolf.getId(), interval);
        if (Math.floorMod(gameTime, interval) != phase) {
            return;
        }
        level.sendParticles(
                ParticleTypes.ANGRY_VILLAGER,
                wolf.getX(),
                wolf.getEyeY() + 0.18D,
                wolf.getZ(),
                1,
                0.18D,
                0.06D,
                0.18D,
                0.0D
        );
    }

    public static void modifyDamage(LivingIncomingDamageEvent event) {
        long gameTime = event.getEntity().level().getGameTime();
        if (event.getSource().getEntity() instanceof Wolf attacker) {
            WolfState state = ensureCombatBuff(attacker, gameTime);
            if (isBuffActive(state, gameTime)) {
                event.setAmount(applyDamageBonus(event.getAmount(), state.damageBonus()));
            }
        }
        if (!event.isCanceled() && event.getEntity() instanceof Wolf victim) {
            WolfState state = victim.getTarget() == null
                    ? STATES.get(victim.getUUID())
                    : ensureCombatBuff(victim, gameTime);
            if (shouldDodge(state, gameTime, victim.getRandom().nextDouble(),
                    event.getSource().getEntity() != null)) {
                event.setCanceled(true);
            }
        }
    }

    public static void forgetOwner(UUID ownerId) {
        STATES.entrySet().removeIf(entry -> entry.getValue().owner().equals(ownerId));
    }

    static boolean isBuffActive(WolfState state, long gameTime) {
        return state != null && gameTime < state.buffEndsAt();
    }

    static float applyDamageBonus(float damage, double bonus) {
        return (float) Math.clamp(damage * (1.0D + Math.max(0.0D, bonus)), 0.0D, Float.MAX_VALUE);
    }

    static boolean shouldDodge(WolfState state, long gameTime, double roll, boolean hasAttacker) {
        return hasAttacker && isBuffActive(state, gameTime) && roll < state.dodgeChance();
    }

    private static WolfState ensureCombatBuff(Wolf wolf, long gameTime) {
        WolfState current = STATES.getOrDefault(wolf.getUUID(), WolfState.EMPTY);
        if (isBuffActive(current, gameTime) || gameTime < current.cooldownEndsAt()
                || !(wolf.getOwner() instanceof ServerPlayer owner)) {
            return current;
        }
        WolfState activated = activate(owner, gameTime).orElse(current);
        if (activated != current) {
            STATES.put(wolf.getUUID(), activated);
        }
        return activated;
    }

    private static Optional<WolfState> activate(ServerPlayer owner, long gameTime) {
        return activeComponents(owner).stream()
                .max(Comparator.comparingDouble((ActiveComponent component) -> component.rank().damageBonus())
                        .thenComparingDouble(component -> component.rank().dodgeChance()))
                .map(component -> new WolfState(
                        true,
                        gameTime + component.config().durationTicks(),
                        gameTime + component.rank().cooldownTicks(),
                        component.rank().damageBonus(),
                        component.rank().dodgeChance(),
                        owner.getUUID()
                ));
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static ResolvedRank resolve(RankValues values) {
        double damage = requiredFinite(
                values.values().get("damage_bonus_percent"),
                "damage_bonus_percent",
                0.0D,
                1000.0D
        );
        double dodge = requiredPercent(values.values().get("dodge_chance_percent"), "dodge_chance_percent");
        double cooldown = requiredFinite(values.values().get("cooldown_seconds"), "cooldown_seconds", 0.0D, 3600.0D);
        return new ResolvedRank(damage / 100.0D, dodge / 100.0D, (int) Math.round(cooldown * 20.0D));
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
                        errors.add("ranks.values[" + rankIndex + "]." + key + ": unsupported wolf pack parameter"));
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

    private static double requiredPercent(Double value, String name) {
        return requiredFinite(value, name, 0.0D, 100.0D);
    }

    private static double requiredFinite(Double value, String name, double minimum, double maximum) {
        if (value == null || !Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be finite and between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static void logInvalidOnce(ResourceLocation id, String detail) {
        if (LOGGED_INVALID_DEFINITIONS.add(id + "|" + detail)) {
            AbilityMod.LOGGER.error("Invalid wolf pack ability {}: {}", id, detail);
        }
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder();
        Optional<T> parsed = codec.parse(input).resultOrPartial(error::append);
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }

    public record Config(int durationTicks) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 12000).optionalFieldOf("duration_ticks", 200).forGetter(Config::durationTicks)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);
        public RankValues { values = Map.copyOf(values); }
    }

    public record ResolvedRank(double damageBonus, double dodgeChance, int cooldownTicks) {
    }

    public record WolfState(
            boolean wasAttacking,
            long buffEndsAt,
            long cooldownEndsAt,
            double damageBonus,
            double dodgeChance,
            UUID owner
    ) {
        private static final WolfState EMPTY = new WolfState(false, 0L, 0L, 0.0D, 0.0D, new UUID(0L, 0L));

        private WolfState withAttacking(boolean attacking) {
            return new WolfState(attacking, buffEndsAt, cooldownEndsAt, damageBonus, dodgeChance, owner);
        }
    }

    private record ActiveComponent(Config config, ResolvedRank rank) {
    }
}
