package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.ability.ActiveAbilityActionService;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class PrimerEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("primer");
    private static final Set<String> RANK_KEYS = Set.of("charge_seconds", "explosion_damage_bonus");

    private PrimerEffect() {
    }

    public static boolean supports(AbilityDefinition definition) {
        return !CompositeEffect.componentsOfType(definition, TYPE).isEmpty();
    }

    public static ActiveAbilityActionService.ActivationResult activate(
            ServerPlayer player,
            AbilityService.ActiveAbility active,
            ActiveAbilityInput input
    ) {
        try {
            List<CompositeEffect.ComponentView> components =
                    CompositeEffect.componentsOfType(active.definition(), TYPE);
            if (components.size() != 1) {
                return ActiveAbilityActionService.ActivationResult.INVALID_DEFINITION;
            }
            AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active, components.getFirst());
            Config config = parse(Config.CODEC, projected.definition().effect().config(), "effect.config");
            ResolvedRank rank = resolve(mergeRanks(projected));
            if (!player.isAlive()
                    || player.isSpectator()
                    || CombatStatusTracker.isStunned(player)
                    || !hasFireCharge(player)) {
                return ActiveAbilityActionService.ActivationResult.INVALID_STATE;
            }
            long gameTime = player.level().getGameTime();
            return switch (input) {
                case CHARGE_START -> beginCharge(player, gameTime);
                case CHARGE_RELEASE -> releaseAndFire(player, config, rank, gameTime);
                default -> ActiveAbilityActionService.ActivationResult.UNSUPPORTED_ACTION;
            };
        } catch (RuntimeException exception) {
            AbilityMod.LOGGER.error("Invalid primer ability {}: {}", active.abilityId(), exception.getMessage());
            return ActiveAbilityActionService.ActivationResult.INVALID_DEFINITION;
        }
    }

    public static void modifyExplosionDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()
                || event.getAmount() <= 0.0F
                || !event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }
        Entity directEntity = event.getSource().getDirectEntity();
        if (!(directEntity instanceof LargeFireball)) {
            return;
        }
        double multiplier = PrimerStateTracker.explosionDamageMultiplier(
                directEntity.getUUID(),
                event.getEntity().level().getGameTime()
        );
        event.setAmount(safeDamage((double) event.getAmount() * multiplier));
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static ResolvedRank resolve(RankValues values) {
        Double chargeSeconds = values.values().get("charge_seconds");
        Double explosionDamageBonus = values.values().get("explosion_damage_bonus");
        if (chargeSeconds == null
                || !Double.isFinite(chargeSeconds)
                || chargeSeconds < 0.05D
                || chargeSeconds > 60.0D) {
            throw new IllegalArgumentException("charge_seconds must be finite and between 0.05 and 60");
        }
        if (explosionDamageBonus == null
                || !Double.isFinite(explosionDamageBonus)
                || explosionDamageBonus < 0.0D
                || explosionDamageBonus > 100.0D) {
            throw new IllegalArgumentException(
                    "explosion_damage_bonus must be finite and between 0 and 100"
            );
        }
        return new ResolvedRank(
                (int) Math.ceil(chargeSeconds * 20.0D),
                1.0D + explosionDamageBonus / 100.0D
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
                RankValues current = parse(
                        RankValues.CODEC,
                        definition.ranks().values().get(index),
                        "ranks.values[" + index + "]"
                );
                current.values().keySet().stream()
                        .filter(key -> !RANK_KEYS.contains(key))
                        .forEach(key -> errors.add(
                                "ranks.values[" + rankIndex + "]." + key
                                        + ": unsupported primer parameter"
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

    private static ActiveAbilityActionService.ActivationResult beginCharge(ServerPlayer player, long gameTime) {
        PrimerStateTracker.beginCharge(player.getUUID(), gameTime);
        return ActiveAbilityActionService.ActivationResult.SUCCESS;
    }

    private static ActiveAbilityActionService.ActivationResult releaseAndFire(
            ServerPlayer player,
            Config config,
            ResolvedRank rank,
            long gameTime
    ) {
        OptionalLong elapsed = PrimerStateTracker.releaseCharge(player.getUUID(), gameTime);
        if (elapsed.isEmpty() || elapsed.getAsLong() < rank.requiredChargeTicks() || !hasFireCharge(player)) {
            return ActiveAbilityActionService.ActivationResult.INVALID_STATE;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return ActiveAbilityActionService.ActivationResult.INVALID_STATE;
        }

        Vec3 direction = player.getLookAngle().normalize();
        LargeFireball fireball = new LargeFireball(level, player, direction, config.explosionPower());
        Vec3 spawn = player.getEyePosition().add(direction.scale(config.spawnOffset()));
        fireball.setPos(spawn.x, spawn.y, spawn.z);
        if (!level.addFreshEntity(fireball)) {
            return ActiveAbilityActionService.ActivationResult.INVALID_STATE;
        }
        PrimerStateTracker.trackProjectile(
                fireball.getUUID(),
                gameTime + config.projectileStateTicks(),
                rank.explosionDamageMultiplier()
        );

        if (!player.getAbilities().instabuild) {
            player.getOffhandItem().shrink(1);
        }
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS,
                1.0F,
                0.9F + player.getRandom().nextFloat() * 0.2F
        );
        return ActiveAbilityActionService.ActivationResult.SUCCESS;
    }

    private static boolean hasFireCharge(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        return !offhand.isEmpty() && offhand.is(Items.FIRE_CHARGE);
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

    public record Config(int explosionPower, int projectileStateTicks, double spawnOffset) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 16).optionalFieldOf("explosion_power", 1).forGetter(Config::explosionPower),
                Codec.intRange(20, 72000).optionalFieldOf("projectile_state_ticks", 1200)
                        .forGetter(Config::projectileStateTicks),
                Codec.doubleRange(0.0D, 8.0D).optionalFieldOf("spawn_offset", 0.5D)
                        .forGetter(Config::spawnOffset)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public record ResolvedRank(int requiredChargeTicks, double explosionDamageMultiplier) {
    }
}
