package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.ability.ActiveAbilityActionService;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.presentation.AbilityPresentationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.minecraft.resources.ResourceLocation;

public final class ChargedLeapEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("charged_leap");
    private static final Set<String> RANK_KEYS = Set.of("damage_multiplier", "stun_seconds");

    private ChargedLeapEffect() {
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
            if (!player.isAlive() || player.isSpectator() || CombatStatusTracker.isStunned(player)) {
                return ActiveAbilityActionService.ActivationResult.INVALID_STATE;
            }
            long gameTime = player.level().getGameTime();
            return switch (input) {
                case CHARGE_START -> beginCharge(player, gameTime);
                case SPACE_CHARGE_START -> player.isCreative()
                        ? ActiveAbilityActionService.ActivationResult.INVALID_STATE
                        : beginCharge(player, gameTime);
                case CHARGE_RELEASE -> releaseCharge(player, active, config, rank, gameTime);
                case CHARGE_CANCEL -> cancelCharge(player);
                case SECONDARY -> doubleJump(player, config, gameTime);
                default -> ActiveAbilityActionService.ActivationResult.UNSUPPORTED_ACTION;
            };
        } catch (RuntimeException exception) {
            AbilityMod.LOGGER.error("Invalid charged leap ability {}: {}", active.abilityId(), exception.getMessage());
            return ActiveAbilityActionService.ActivationResult.INVALID_DEFINITION;
        }
    }

    public static void processImpact(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || event.getSource().getDirectEntity() != player
                || player.onGround()
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ChargedLeapStateTracker.LeapState state = ChargedLeapStateTracker.consumeImpact(
                player.getUUID(),
                level.getGameTime()
        ).orElse(null);
        if (state == null) {
            return;
        }

        LivingEntity impactTarget = event.getEntity();
        float damage = safeDamage(player.getAttributeValue(Attributes.ATTACK_DAMAGE) * state.damageMultiplier());
        player.fallDistance = 0.0F;
        AbilityPresentationService.sendTracking(impactTarget, AbilityCue.pulse(
                AbilityMod.id("charged_leap"),
                AbilityMod.id("impact"),
                player.getId(),
                impactTarget.getId(),
                impactTarget.position(),
                net.minecraft.world.phys.Vec3.ZERO,
                0,
                level.getGameTime() ^ player.getUUID().getLeastSignificantBits() ^ impactTarget.getId()
        ));
        CombatStatusTracker.stun(impactTarget, state.stunTicks());
        level.getEntitiesOfClass(
                LivingEntity.class,
                impactTarget.getBoundingBox().inflate(state.impactRadius()),
                target -> target.isAlive()
                        && target != player
                        && target != impactTarget
                        && target.distanceToSqr(impactTarget) <= state.impactRadius() * state.impactRadius()
        ).forEach(target -> {
            target.invulnerableTime = 0;
            if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                CombatStatusTracker.stun(target, state.stunTicks());
            }
        });
    }

    public static void replaceImpactDamage(LivingIncomingDamageEvent event) {
        if (!isImpactAttack(event)) {
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getSource().getEntity();
        ChargedLeapStateTracker.LeapState state = ChargedLeapStateTracker.peekActiveLeap(
                player.getUUID(),
                player.level().getGameTime()
        ).orElse(null);
        if (state != null) {
            event.setAmount(safeDamage(
                    player.getAttributeValue(Attributes.ATTACK_DAMAGE) * state.damageMultiplier()
            ));
        }
    }

    public static boolean isImpactAttack(LivingIncomingDamageEvent event) {
        return event.getSource().getEntity() instanceof ServerPlayer player
                && event.getSource().getDirectEntity() == player
                && !player.onGround()
                && ChargedLeapStateTracker.peekActiveLeap(
                        player.getUUID(),
                        player.level().getGameTime()
                ).isPresent();
    }

    public static void preventFallDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getSource().is(DamageTypeTags.IS_FALL)) {
            return;
        }
        if (ChargedLeapStateTracker.consumeFallProtection(
                player.getUUID(),
                player.level().getGameTime()
        )) {
            event.setCanceled(true);
            player.fallDistance = 0.0F;
        }
    }

    static double chargedVerticalSpeed(long chargeTicks, Config config) {
        double progress = Math.clamp((double) chargeTicks / config.maximumChargeTicks(), 0.0D, 1.0D);
        return config.minimumVerticalSpeed()
                + (config.maximumVerticalSpeed() - config.minimumVerticalSpeed()) * progress;
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static ResolvedRank resolve(RankValues values) {
        Double damageMultiplier = values.values().get("damage_multiplier");
        Double stunSeconds = values.values().get("stun_seconds");
        if (damageMultiplier == null
                || !Double.isFinite(damageMultiplier)
                || damageMultiplier < 0.0D
                || damageMultiplier > 100.0D) {
            throw new IllegalArgumentException("damage_multiplier must be finite and between 0 and 100");
        }
        if (stunSeconds == null
                || !Double.isFinite(stunSeconds)
                || stunSeconds < 0.0D
                || stunSeconds > 60.0D) {
            throw new IllegalArgumentException("stun_seconds must be finite and between 0 and 60");
        }
        return new ResolvedRank(damageMultiplier, (int) Math.round(stunSeconds * 20.0D));
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        try {
            Config config = parse(Config.CODEC, definition.effect().config(), "effect.config");
            if (config.doubleJumpUnlockRank() > definition.ranks().values().size()) {
                errors.add("effect.config.double_jump_unlock_rank: exceeds the ability's maximum rank");
            }
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
                                        + ": unsupported charged leap parameter"
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
        if (!player.onGround()
                || player.isPassenger()
                || player.isInWater()
                || player.isInLava()
                || player.onClimbable()
                || player.getAbilities().flying) {
            return ActiveAbilityActionService.ActivationResult.INVALID_STATE;
        }
        ChargedLeapStateTracker.beginCharge(player.getUUID(), gameTime);
        return ActiveAbilityActionService.ActivationResult.SUCCESS;
    }

    private static ActiveAbilityActionService.ActivationResult releaseCharge(
            ServerPlayer player,
            AbilityService.ActiveAbility active,
            Config config,
            ResolvedRank rank,
            long gameTime
    ) {
        OptionalLong chargeTicks = ChargedLeapStateTracker.releaseCharge(player.getUUID(), gameTime);
        if (chargeTicks.isEmpty()) {
            return ActiveAbilityActionService.ActivationResult.INVALID_STATE;
        }
        if (!player.onGround()) {
            return ActiveAbilityActionService.ActivationResult.INVALID_STATE;
        }
        double verticalSpeed = chargedVerticalSpeed(chargeTicks.getAsLong(), config);
        player.setDeltaMovement(player.getDeltaMovement().x, verticalSpeed, player.getDeltaMovement().z);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        ChargedLeapStateTracker.armLeap(
                player.getUUID(),
                gameTime + config.leapTimeoutTicks(),
                config.impactRadius(),
                rank.damageMultiplier(),
                rank.stunTicks(),
                active.rank() >= config.doubleJumpUnlockRank()
        );
        return ActiveAbilityActionService.ActivationResult.SUCCESS;
    }

    private static ActiveAbilityActionService.ActivationResult cancelCharge(ServerPlayer player) {
        ChargedLeapStateTracker.cancelCharge(player.getUUID());
        return ActiveAbilityActionService.ActivationResult.SUCCESS;
    }

    private static ActiveAbilityActionService.ActivationResult doubleJump(
            ServerPlayer player,
            Config config,
            long gameTime
    ) {
        if (player.onGround() || !ChargedLeapStateTracker.useDoubleJump(player.getUUID(), gameTime)) {
            return ActiveAbilityActionService.ActivationResult.INVALID_STATE;
        }
        player.setDeltaMovement(
                player.getDeltaMovement().x,
                Math.max(player.getDeltaMovement().y, config.doubleJumpVerticalSpeed()),
                player.getDeltaMovement().z
        );
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        return ActiveAbilityActionService.ActivationResult.SUCCESS;
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

    public record Config(
            double impactRadius,
            int maximumChargeTicks,
            double minimumVerticalSpeed,
            double maximumVerticalSpeed,
            int leapTimeoutTicks,
            int doubleJumpUnlockRank,
            double doubleJumpVerticalSpeed
    ) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.doubleRange(0.0D, 64.0D).optionalFieldOf("impact_radius", 5.0D)
                        .forGetter(Config::impactRadius),
                Codec.intRange(1, 1200).optionalFieldOf("maximum_charge_ticks", 20)
                        .forGetter(Config::maximumChargeTicks),
                Codec.doubleRange(0.0D, 4.0D).optionalFieldOf("minimum_vertical_speed", 0.42D)
                        .forGetter(Config::minimumVerticalSpeed),
                Codec.doubleRange(0.0D, 4.0D).optionalFieldOf("maximum_vertical_speed", 0.74D)
                        .forGetter(Config::maximumVerticalSpeed),
                Codec.intRange(1, 1200).optionalFieldOf("leap_timeout_ticks", 100)
                        .forGetter(Config::leapTimeoutTicks),
                Codec.intRange(1, 100).optionalFieldOf("double_jump_unlock_rank", 6)
                        .forGetter(Config::doubleJumpUnlockRank),
                Codec.doubleRange(0.0D, 4.0D).optionalFieldOf("double_jump_vertical_speed", 0.42D)
                        .forGetter(Config::doubleJumpVerticalSpeed)
        ).apply(instance, Config::new));

        public Config {
            if (maximumVerticalSpeed < minimumVerticalSpeed) {
                throw new IllegalArgumentException(
                        "maximum_vertical_speed must be at least minimum_vertical_speed"
                );
            }
        }
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public record ResolvedRank(double damageMultiplier, int stunTicks) {
    }
}
