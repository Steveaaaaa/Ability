package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.ability.ActiveAbilityActionService;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class CeilingWireEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("ceiling_wire");
    private static final Set<String> RANK_KEYS = Set.of("damage_percent", "stun_ticks", "detach_chance_percent");
    private static final Map<UUID, State> CLINGING = new HashMap<>();
    private static final Map<UUID, FallingAttack> FALLING_ATTACKS = new HashMap<>();

    private CeilingWireEffect() {}

    public static boolean supports(AbilityDefinition definition) {
        return !CompositeEffect.componentsOfType(definition, TYPE).isEmpty();
    }

    public static ActiveAbilityActionService.ActivationResult activate(ServerPlayer player,
            AbilityService.ActiveAbility active, ActiveAbilityInput input) {
        if (input != ActiveAbilityInput.SECONDARY || !player.isAlive() || player.isSpectator()) {
            return ActiveAbilityActionService.ActivationResult.UNSUPPORTED_ACTION;
        }
        if (CLINGING.remove(player.getUUID()) != null) {
            player.setNoGravity(false);
            return ActiveAbilityActionService.ActivationResult.SUCCESS;
        }
        BlockPos ceiling = player.blockPosition().above(2);
        BlockState state = player.level().getBlockState(ceiling);
        if (ceilingSupport(state, player.level(), ceiling) != CeilingSupport.SOLID) {
            return ActiveAbilityActionService.ActivationResult.INVALID_STATE;
        }
        CompositeEffect.ComponentView component = CompositeEffect.componentsOfType(active.definition(), TYPE).getFirst();
        AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active, component);
        ResolvedRank rank = resolve(projected);
        Config config = parse(Config.CODEC, component.config(), "effect.config");
        CLINGING.put(player.getUUID(), new State(
                ceiling,
                rank,
                config.releaseIntervalTicks(),
                Long.MIN_VALUE,
                player.getX(),
                player.getZ()
        ));
        player.setNoGravity(true);
        player.setDeltaMovement(player.getDeltaMovement().x, 0.0D, player.getDeltaMovement().z);
        return ActiveAbilityActionService.ActivationResult.SUCCESS;
    }

    public static void processTick(ServerPlayer player) {
        State state = CLINGING.get(player.getUUID());
        if (state == null) return;
        if (!player.isAlive()) {
            detach(player);
            return;
        }

        BlockPos overhead = player.blockPosition().above(2);
        BlockState ceiling = player.level().getBlockState(overhead);
        CeilingSupport support = ceilingSupport(ceiling, player.level(), overhead);
        if (support == CeilingSupport.AIR) {
            if (overhead.equals(state.ceiling())) {
                detach(player);
                return;
            }
            player.teleportTo(state.safeX(), player.getY(), state.safeZ());
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            player.hurtMarked = true;
        } else if (support == CeilingSupport.NON_SOLID) {
            detach(player);
            return;
        } else {
            state = new State(
                    overhead,
                    state.rank(),
                    state.releaseIntervalTicks(),
                    state.lastRelease(),
                    player.getX(),
                    player.getZ()
            );
            CLINGING.put(player.getUUID(), state);
            player.setDeltaMovement(player.getDeltaMovement().x, 0.0D, player.getDeltaMovement().z);
        }
        player.setNoGravity(true);
        player.fallDistance = 0.0F;
    }

    public static void releaseDripstone(PlayerInteractEvent.RightClickItem event) {
        if (tryReleaseDripstone(event, event.getEntity().blockPosition())) {
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    public static void releaseDripstone(PlayerInteractEvent.RightClickBlock event) {
        BlockPlaceContext placement = new BlockPlaceContext(
                event.getEntity(),
                event.getHand(),
                event.getItemStack(),
                event.getHitVec()
        );
        if (tryReleaseDripstone(event, placement.getClickedPos())) {
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static boolean tryReleaseDripstone(PlayerInteractEvent event, BlockPos horizontalTarget) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.OFF_HAND
                || !event.getItemStack().is(Items.POINTED_DRIPSTONE)) return false;
        State state = CLINGING.get(player.getUUID());
        long gameTime = player.level().getGameTime();
        if (state == null || !isReleaseReady(gameTime, state.lastRelease(), state.releaseIntervalTicks())) return false;
        BlockPos origin = releaseOrigin(player.blockPosition(), horizontalTarget);
        if (!player.level().getBlockState(origin).canBeReplaced()) return false;
        BlockState dripstone = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN);
        player.level().setBlock(origin, dripstone, 3);
        FallingBlockEntity falling = FallingBlockEntity.fall((ServerLevel) player.level(), origin, dripstone);
        falling.setHurtsEntities(1.0F, 40);
        player.swing(InteractionHand.OFF_HAND, true);
        player.level().playSound(
                null,
                falling.getX(),
                falling.getY(),
                falling.getZ(),
                SoundEvents.POINTED_DRIPSTONE_FALL,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
        FALLING_ATTACKS.put(falling.getUUID(), new FallingAttack(player.getUUID(), state.rank(),
                gameTime + 200));
        CLINGING.put(player.getUUID(), new State(
                state.ceiling(),
                state.rank(),
                state.releaseIntervalTicks(),
                gameTime,
                state.safeX(),
                state.safeZ()
        ));
        if (!player.getAbilities().instabuild) event.getItemStack().shrink(1);
        return true;
    }

    static BlockPos releaseOrigin(BlockPos playerPosition, BlockPos horizontalTarget) {
        return new BlockPos(horizontalTarget.getX(), playerPosition.getY() - 1, horizontalTarget.getZ());
    }

    static boolean isReleaseReady(long gameTime, long lastRelease, int intervalTicks) {
        return lastRelease == Long.MIN_VALUE || gameTime - lastRelease >= intervalTicks;
    }

    static CeilingSupport ceilingSupport(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) {
            return CeilingSupport.AIR;
        }
        return state.isFaceSturdy(level, pos, Direction.DOWN)
                ? CeilingSupport.SOLID
                : CeilingSupport.NON_SOLID;
    }

    public static void modifyFallingDripstoneDamage(LivingIncomingDamageEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof FallingBlockEntity)) return;
        FallingAttack attack = FALLING_ATTACKS.remove(direct.getUUID());
        if (attack == null || !(direct.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(attack.owner());
        if (owner == null) return;
        float damage = (float) (owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * attack.rank().damageMultiplier());
        event.setAmount(Math.max(event.getAmount(), damage));
        CombatStatusTracker.stun(event.getEntity(), attack.rank().stunTicks());
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, Blocks.POINTED_DRIPSTONE.defaultBlockState()),
                event.getEntity().getX(),
                event.getEntity().getY() + event.getEntity().getBbHeight() * 0.5D,
                event.getEntity().getZ(),
                18,
                0.35D,
                0.35D,
                0.35D,
                0.08D
        );
        level.playSound(
                null,
                event.getEntity().blockPosition(),
                SoundEvents.POINTED_DRIPSTONE_LAND,
                SoundSource.PLAYERS,
                1.2F,
                0.9F
        );
        if (owner.getRandom().nextDouble() < attack.rank().detachChance()) detach(owner);
    }

    public static void forget(ServerPlayer player) { detach(player); }

    public static void cleanup(long gameTime) {
        FALLING_ATTACKS.values().removeIf(attack -> gameTime >= attack.expiresAt());
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        try {
            parse(Config.CODEC, definition.effect().config(), "effect.config");
            ResolvedRank previous = null;
            for (int i = 1; i <= definition.ranks().values().size(); i++) previous = resolve(definition, i);
            return List.of();
        } catch (RuntimeException exception) {
            return List.of(exception.getMessage());
        }
    }

    private static void detach(ServerPlayer player) {
        CLINGING.remove(player.getUUID());
        player.setNoGravity(false);
    }

    private static ResolvedRank resolve(AbilityService.ActiveAbility active) {
        return resolve(active.definition(), active.rank());
    }

    private static ResolvedRank resolve(AbilityDefinition definition, int rank) {
        Map<String, Double> merged = new HashMap<>();
        for (int i = 0; i < rank; i++) merged.putAll(parse(RankValues.CODEC,
                definition.ranks().values().get(i), "ranks.values[" + i + "]").values());
        if (!RANK_KEYS.containsAll(merged.keySet())) throw new IllegalArgumentException("unsupported ceiling wire rank key");
        return new ResolvedRank(percent(merged, "damage_percent"), whole(merged, "stun_ticks"),
                percent(merged, "detach_chance_percent"));
    }

    private static double percent(Map<String, Double> values, String key) {
        Double value = values.get(key);
        if (value == null || !Double.isFinite(value) || value < 0 || value > 1000)
            throw new IllegalArgumentException(key + " must be between 0 and 1000");
        return value / 100.0D;
    }

    private static int whole(Map<String, Double> values, String key) {
        Double value = values.get(key);
        if (value == null || value != Math.rint(value) || value < 0 || value > 1200)
            throw new IllegalArgumentException(key + " must be a whole number between 0 and 1200");
        return value.intValue();
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder();
        Optional<T> parsed = codec.parse(input).resultOrPartial(error::append);
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }

    public record Config(int releaseIntervalTicks) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 200).optionalFieldOf("release_interval_ticks", 10)
                        .forGetter(Config::releaseIntervalTicks)).apply(instance, Config::new));
    }
    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);
    }
    record ResolvedRank(double damageMultiplier, int stunTicks, double detachChance) {}
    enum CeilingSupport { AIR, SOLID, NON_SOLID }
    private record State(
            BlockPos ceiling,
            ResolvedRank rank,
            int releaseIntervalTicks,
            long lastRelease,
            double safeX,
            double safeZ
    ) {}
    private record FallingAttack(UUID owner, ResolvedRank rank, long expiresAt) {}
}
