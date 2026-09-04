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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class DodgeEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("dodge");
    private static final Set<String> RANK_KEYS = Set.of("damage_reduction");
    private static final Map<UUID, RollState> ROLLS = new ConcurrentHashMap<>();
    private static final double[] MOTION_TIMES = {
            0.0D, 0.105D, 0.211D, 0.368D, 0.553D, 0.684D, 0.789D, 0.895D, 1.0D
    };
    private static final double[] FORWARD_MOTION = {
            0.0D, 0.142D, 0.291D, 0.471D, 0.756D, 0.908D, 0.977D, 0.997D, 1.0D
    };
    private static final double[] BACKWARD_MOTION = {
            0.0D, 0.141D, 0.282D, 0.619D, 0.785D, 0.881D, 0.935D, 0.992D, 1.0D
    };
    public static final EntityDimensions ROLL_DIMENSIONS = EntityDimensions.scalable(0.6F, 0.8F)
            .withEyeHeight(0.68F);

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
            if (!canDodge(player, config) || isRolling(player)) {
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

            Vec3 direction = directionalMotion(player.getLookAngle(), input, 1.0D);
            boolean backward = isBackward(input);
            RollState roll = new RollState(
                    gameTime,
                    gameTime + config.durationTicks(),
                    direction,
                    config.horizontalSpeed(),
                    backward
            );
            ROLLS.put(player.getUUID(), roll);
            player.refreshDimensions();
            player.level().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                    SoundSource.PLAYERS,
                    0.7F,
                    0.88F + player.getRandom().nextFloat() * 0.08F
            );
            applyMotion(player, roll, gameTime);
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

    public static void processTick(ServerPlayer player) {
        RollState roll = ROLLS.get(player.getUUID());
        if (roll == null) {
            return;
        }
        long gameTime = player.level().getGameTime();
        if (!player.isAlive() || gameTime >= roll.endsAt()) {
            ROLLS.remove(player.getUUID(), roll);
            if (player.isAlive()) {
                player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
                player.hurtMarked = true;
                player.refreshDimensions();
            }
            return;
        }
        applyMotion(player, roll, gameTime);
    }

    public static boolean isRolling(ServerPlayer player) {
        RollState roll = ROLLS.get(player.getUUID());
        if (roll == null) {
            return false;
        }
        if (player.level().getGameTime() >= roll.endsAt()) {
            ROLLS.remove(player.getUUID(), roll);
            return false;
        }
        return true;
    }

    public static RollPresentation presentation(ServerPlayer player) {
        RollState roll = ROLLS.get(player.getUUID());
        if (roll == null || player.level().getGameTime() >= roll.endsAt()) {
            return null;
        }
        return new RollPresentation(
                (float) roll.direction().x,
                (float) roll.direction().z,
                Math.toIntExact(roll.endsAt() - roll.startedAt()),
                roll.backward()
        );
    }

    public static void forget(UUID playerId) {
        ROLLS.remove(playerId);
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

    private static boolean isBackward(ActiveAbilityInput input) {
        return input == ActiveAbilityInput.BACKWARD
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
                && !player.isInWaterOrBubble()
                && !player.onClimbable()
                && !player.isFallFlying()
                && (!config.requireOnGround() || player.onGround());
    }

    private static void applyMotion(ServerPlayer player, RollState roll, long gameTime) {
        if (roll.lastMotionTick == gameTime) {
            return;
        }
        int elapsed = Math.toIntExact(gameTime - roll.startedAt());
        int duration = Math.toIntExact(roll.endsAt() - roll.startedAt());
        double speed = motionForTick(roll.totalDistance(), duration, elapsed, roll.backward());
        if (player.horizontalCollision) {
            roll.blocked = true;
        }
        Vec3 direction = roll.blocked ? Vec3.ZERO : roll.direction();
        player.setSprinting(false);
        player.setDeltaMovement(
                direction.x * speed,
                player.getDeltaMovement().y,
                direction.z * speed
        );
        player.hurtMarked = true;
        roll.lastMotionTick = gameTime;
    }

    private static double motionForTick(double totalDistance, int duration, int elapsed, boolean backward) {
        if (elapsed < 0 || elapsed >= duration) {
            return 0.0D;
        }
        double from = motionProgress((double) elapsed / duration, backward);
        double to = motionProgress((double) (elapsed + 1) / duration, backward);
        return totalDistance * Math.max(0.0D, to - from);
    }

    private static double motionProgress(double progress, boolean backward) {
        double clamped = Math.clamp(progress, 0.0D, 1.0D);
        double[] values = backward ? BACKWARD_MOTION : FORWARD_MOTION;
        for (int index = 1; index < MOTION_TIMES.length; index++) {
            if (clamped <= MOTION_TIMES[index]) {
                double local = (clamped - MOTION_TIMES[index - 1])
                        / (MOTION_TIMES[index] - MOTION_TIMES[index - 1]);
                return values[index - 1] + (values[index] - values[index - 1]) * local;
            }
        }
        return 1.0D;
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
                Codec.intRange(1, 100).optionalFieldOf("duration_ticks", 13).forGetter(Config::durationTicks),
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

    public record RollPresentation(float directionX, float directionZ, int durationTicks, boolean backward) {
    }

    private static final class RollState {
        private final long startedAt;
        private final long endsAt;
        private final Vec3 direction;
        private final double totalDistance;
        private final boolean backward;
        private long lastMotionTick = Long.MIN_VALUE;
        private boolean blocked;

        private RollState(long startedAt, long endsAt, Vec3 direction, double totalDistance, boolean backward) {
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.direction = direction;
            this.totalDistance = totalDistance;
            this.backward = backward;
        }

        private long startedAt() {
            return startedAt;
        }

        private long endsAt() {
            return endsAt;
        }

        private Vec3 direction() {
            return direction;
        }

        private double totalDistance() {
            return totalDistance;
        }

        private boolean backward() {
            return backward;
        }
    }
}
