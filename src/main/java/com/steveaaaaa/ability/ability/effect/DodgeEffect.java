package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.ability.ActiveAbilityActionService;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import com.steveaaaaa.ability.ability.ActiveAbilityRuntime;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class DodgeEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("dodge");
    private static final Set<String> RANK_KEYS = Set.of("damage_reduction");

    private DodgeEffect() {
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
            if (!isDodgeDirection(input)) {
                return ActiveAbilityActionService.ActivationResult.UNSUPPORTED_ACTION;
            }
            List<CompositeEffect.ComponentView> components =
                    CompositeEffect.componentsOfType(active.definition(), TYPE);
            if (components.size() != 1) {
                return ActiveAbilityActionService.ActivationResult.INVALID_DEFINITION;
            }
            AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active, components.getFirst());
            Config config = parse(Config.CODEC, projected.definition().effect().config(), "effect.config");
            ResolvedRank rank = resolve(mergeRanks(projected));
            if (!canDodge(player, config)) {
                return ActiveAbilityActionService.ActivationResult.INVALID_STATE;
            }

            long gameTime = player.level().getGameTime();
            boolean activated = ActiveAbilityRuntime.tryActivate(
                    player.getUUID(),
                    active.abilityId(),
                    gameTime,
                    config.cooldownTicks(),
                    config.durationTicks(),
                    1.0D - rank.damageReduction()
            );
            if (!activated) {
                return ActiveAbilityActionService.ActivationResult.COOLDOWN;
            }

            Vec3 motion = directionalMotion(player.getLookAngle(), input, config.horizontalSpeed());
            player.setDeltaMovement(motion.x, player.getDeltaMovement().y, motion.z);
            player.hurtMarked = true;
            return ActiveAbilityActionService.ActivationResult.SUCCESS;
        } catch (RuntimeException exception) {
            AbilityMod.LOGGER.error("Invalid dodge ability {}: {}", active.abilityId(), exception.getMessage());
            return ActiveAbilityActionService.ActivationResult.INVALID_DEFINITION;
        }
    }

    public static void reduceIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()
                || event.getAmount() <= 0.0F
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        double multiplier = ActiveAbilityRuntime.incomingDamageMultiplier(
                player.getUUID(),
                player.level().getGameTime()
        );
        event.setAmount(safeDamage((double) event.getAmount() * multiplier));
    }

    static boolean isDodgeDirection(ActiveAbilityInput input) {
        return input == ActiveAbilityInput.FORWARD
                || input == ActiveAbilityInput.BACKWARD
                || input == ActiveAbilityInput.LEFT
                || input == ActiveAbilityInput.RIGHT
                || input == ActiveAbilityInput.FORWARD_LEFT
                || input == ActiveAbilityInput.FORWARD_RIGHT
                || input == ActiveAbilityInput.BACKWARD_LEFT
                || input == ActiveAbilityInput.BACKWARD_RIGHT;
    }

    static Vec3 directionalMotion(Vec3 look, ActiveAbilityInput input, double speed) {
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        if (forward.lengthSqr() < 1.0E-8D || !isDodgeDirection(input)) {
            return Vec3.ZERO;
        }
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        return switch (input) {
            case FORWARD -> forward.scale(speed);
            case BACKWARD -> forward.scale(-speed);
            case LEFT -> right.scale(-speed);
            case RIGHT -> right.scale(speed);
            case FORWARD_LEFT -> forward.subtract(right).normalize().scale(speed);
            case FORWARD_RIGHT -> forward.add(right).normalize().scale(speed);
            case BACKWARD_LEFT -> forward.scale(-1.0D).subtract(right).normalize().scale(speed);
            case BACKWARD_RIGHT -> forward.scale(-1.0D).add(right).normalize().scale(speed);
            default -> Vec3.ZERO;
        };
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static ResolvedRank resolve(RankValues values) {
        Double reduction = values.values().get("damage_reduction");
        if (reduction == null || !Double.isFinite(reduction) || reduction < 0.0D || reduction > 1.0D) {
            throw new IllegalArgumentException("damage_reduction must be finite and between 0 and 1");
        }
        return new ResolvedRank(reduction);
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
                                "ranks.values[" + rankIndex + "]." + key + ": unsupported dodge parameter"
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

    private static boolean canDodge(ServerPlayer player, Config config) {
        return player.isAlive()
                && !player.isSpectator()
                && !CombatStatusTracker.isStunned(player)
                && !player.isPassenger()
                && (!config.requireOnGround() || player.onGround());
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
            int cooldownTicks,
            int durationTicks,
            double horizontalSpeed,
            boolean requireOnGround
    ) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 1200).optionalFieldOf("cooldown_ticks", 12).forGetter(Config::cooldownTicks),
                Codec.intRange(1, 100).optionalFieldOf("duration_ticks", 4).forGetter(Config::durationTicks),
                Codec.doubleRange(0.0D, 4.0D).optionalFieldOf("horizontal_speed", 0.9D)
                        .forGetter(Config::horizontalSpeed),
                Codec.BOOL.optionalFieldOf("require_on_ground", true).forGetter(Config::requireOnGround)
        ).apply(instance, Config::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public record ResolvedRank(double damageReduction) {
    }
}
