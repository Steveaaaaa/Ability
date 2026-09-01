package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.network.ClientboundColdCurrentPayload;
import com.steveaaaaa.ability.network.ClientboundCrushingBlowPayload;
import com.steveaaaaa.ability.network.ClientboundGolemReinforcementPayload;
import com.steveaaaaa.ability.registry.ModParticles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class GolemEnhancementEffect {
    public static final ResourceLocation CRUSHING_BLOW = AbilityMod.id("crushing_blow");
    public static final ResourceLocation OBSIDIAN_REINFORCEMENT = AbilityMod.id("obsidian_reinforcement");
    public static final ResourceLocation COLD_CURRENT = AbilityMod.id("cold_current");
    private static final String ROOT = "ability_enhancements";
    private static final ResourceLocation COLD_ATTACK_FLAT = AbilityMod.id("cold_current/attack_flat");
    private static final ResourceLocation COLD_ATTACK_PERCENT = AbilityMod.id("cold_current/attack_percent");
    private static final ResourceLocation COLD_HEALTH = AbilityMod.id("cold_current/health");
    private static final ResourceLocation COLD_ARMOR_FLAT = AbilityMod.id("cold_current/armor_flat");
    private static final ResourceLocation COLD_ARMOR_PERCENT = AbilityMod.id("cold_current/armor_percent");
    private static final ResourceLocation COLD_TOUGHNESS = AbilityMod.id("cold_current/toughness");
    private static final int OBSIDIAN_CHARGE_THRESHOLD = 9;
    private GolemEnhancementEffect() {}

    public static void enhance(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity target = event.getTarget();
        for (ActiveComponent component : activeComponents(player)) {
            boolean targetMatches = component.type().equals(COLD_CURRENT) ? target instanceof SnowGolem : target instanceof IronGolem;
            if (!targetMatches || component.config().items().stream().noneMatch(event.getItemStack()::is)
                    || event.getItemStack().getCount() < component.config().itemCost()) continue;
            CompoundTag root = target.getPersistentData().getCompound(ROOT);
            if ((component.type().equals(CRUSHING_BLOW) || component.type().equals(OBSIDIAN_REINFORCEMENT)
                    || component.type().equals(COLD_CURRENT))
                    && root.contains(component.type().getPath())) {
                if (target instanceof IronGolem golem && component.type().equals(CRUSHING_BLOW)) syncCrushingState(golem);
                if (target instanceof IronGolem golem && component.type().equals(OBSIDIAN_REINFORCEMENT)) syncObsidianState(golem);
                if (target instanceof SnowGolem golem) syncColdState(golem);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            CompoundTag state = new CompoundTag();
            state.putUUID("owner", player.getUUID());
            component.rank().forEach(state::putDouble);
            state.putInt("charge", 0); state.putInt("shields", 0); state.putInt("age", 0);
            root.put(component.type().getPath(), state);
            target.getPersistentData().put(ROOT, root);
            if (!player.getAbilities().instabuild) event.getItemStack().shrink(component.config().itemCost());
            if (component.type().equals(OBSIDIAN_REINFORCEMENT) && target instanceof IronGolem golem) {
                playActivationEffect(golem);
                syncObsidianState(golem, ClientboundGolemReinforcementPayload.VisualEvent.ACTIVATED, Vec3.ZERO);
            }
            if (component.type().equals(CRUSHING_BLOW) && target instanceof IronGolem golem) {
                playCrushingActivationEffect(golem);
                syncCrushingState(golem, ClientboundCrushingBlowPayload.VisualEvent.ACTIVATED, Vec3.ZERO);
            }
            if (component.type().equals(COLD_CURRENT) && target instanceof SnowGolem golem) {
                playColdActivationEffect(golem);
                syncColdState(golem);
            }
            event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true); return;
        }
    }

    public static void blockWithObsidianShield(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        CompoundTag state = state(entity, OBSIDIAN_REINFORCEMENT);
        if (state == null || state.getInt("shields") <= 0 || event.isCanceled() || event.getAmount() <= 0.0F) return;
        state.putInt("shields", state.getInt("shields") - 1);
        int maxShields = maxShields(state);
        if (state.getInt("shields") < maxShields) {
            state.putInt("charge", Math.min(OBSIDIAN_CHARGE_THRESHOLD - 1,
                    state.getInt("charge") + (int) state.getDouble("recharge")));
        }
        entity.heal((float) (entity.getMaxHealth() * state.getDouble("heal_percent") / 100.0D));
        event.setCanceled(true);
        if (entity instanceof IronGolem golem) {
            Vec3 source = event.getSource().getSourcePosition();
            Vec3 direction = source == null
                    ? Vec3.directionFromRotation(0.0F, golem.getYRot()).scale(-1.0D)
                    : source.subtract(golem.getX(), golem.getY() + golem.getBbHeight() * 0.5D, golem.getZ()).normalize();
            playShieldBreakEffect(golem, direction);
            syncObsidianState(golem, ClientboundGolemReinforcementPayload.VisualEvent.SHIELD_BLOCKED, direction);
        }
    }

    public static void blockDuringCrushingRelease(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem) || event.isCanceled()) return;
        CompoundTag state = state(golem, CRUSHING_BLOW);
        if (state != null && state.getInt("release_ticks") > 0) event.setCanceled(true);
    }

    public static void processFinalDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof IronGolem golem) || event.getNewDamage() <= 0) return;
        CompoundTag state = state(golem, CRUSHING_BLOW);
        if (state == null || state.getInt("release_ticks") > 0) return;
        int charge = state.getInt("charge") + 1;
        int threshold = Math.max(1, (int) state.getDouble("charge_threshold"));
        Vec3 source = event.getSource().getSourcePosition();
        Vec3 direction = source == null
                ? Vec3.directionFromRotation(0.0F, golem.getYRot()).scale(-1.0D)
                : source.subtract(golem.getX(), golem.getY() + golem.getBbHeight() * 0.55D, golem.getZ()).normalize();
        if (charge < threshold) {
            state.putInt("charge", charge);
            playCrushingChargeEffect(golem, direction, charge, threshold);
            syncCrushingState(golem, ClientboundCrushingBlowPayload.VisualEvent.CHARGED, direction);
            return;
        }
        beginCrushingRelease(golem, state);
    }

    public static void processTick(Entity entity) {
        if (entity instanceof Snowball snowball) {
            processColdSnowballTrail(snowball);
            return;
        }
        if (!(entity instanceof LivingEntity living) || !(entity instanceof IronGolem || entity instanceof SnowGolem)) return;
        boolean crushingReleasing = entity instanceof IronGolem golem && processCrushingTick(golem);
        if (entity instanceof IronGolem golem && !crushingReleasing
                && golem.tickCount % 20 == 0 && golem.getTarget() != null) {
            CompoundTag state = state(golem, OBSIDIAN_REINFORCEMENT);
            if (state != null) {
                int maxShields = maxShields(state);
                if (state.getInt("shields") < maxShields) {
                    int charge = state.getInt("charge") + 1;
                    if (charge >= OBSIDIAN_CHARGE_THRESHOLD) {
                        state.putInt("shields", state.getInt("shields") + 1);
                        charge = 0;
                        playShieldGainedEffect(golem);
                        syncObsidianState(golem, ClientboundGolemReinforcementPayload.VisualEvent.SHIELD_GAINED, Vec3.ZERO);
                    } else {
                        playChargeEffect(golem);
                    }
                    state.putInt("charge", charge);
                    if (charge != 0) syncObsidianState(golem);
                } else if (state.getInt("charge") != 0) {
                    state.putInt("charge", 0);
                    syncObsidianState(golem);
                }
            }
        }
        if (entity instanceof SnowGolem snow) processCold(snow);
    }

    private static boolean processCrushingTick(IronGolem golem) {
        CompoundTag state = state(golem, CRUSHING_BLOW);
        if (state == null || !(golem.level() instanceof ServerLevel level)) return false;
        int releaseTicks = state.getInt("release_ticks");
        int charge = state.getInt("charge");
        if (releaseTicks <= 0) {
            if (charge > 0 && golem.tickCount % 6 == 0) playCrushingSteam(golem, charge, false);
            return false;
        }

        golem.getNavigation().stop();
        golem.setDeltaMovement(Vec3.ZERO);
        golem.setPos(state.getDouble("release_x"), state.getDouble("release_y"), state.getDouble("release_z"));
        if (releaseTicks % 2 == 0) playCrushingSteam(golem, Math.max(charge, crushingThreshold(state)), true);
        if (releaseTicks == 40) releaseCrushingBlow(golem, state);
        if (releaseTicks >= 60) {
            state.putInt("release_ticks", 0);
            state.putInt("charge", 0);
            syncCrushingState(golem, ClientboundCrushingBlowPayload.VisualEvent.FINISHED, Vec3.ZERO);
            return false;
        }
        state.putInt("release_ticks", releaseTicks + 1);
        return true;
    }

    private static void beginCrushingRelease(IronGolem golem, CompoundTag state) {
        state.putInt("charge", crushingThreshold(state));
        state.putInt("release_ticks", 1);
        state.putDouble("release_x", golem.getX());
        state.putDouble("release_y", golem.getY());
        state.putDouble("release_z", golem.getZ());
        golem.getNavigation().stop();
        golem.setDeltaMovement(Vec3.ZERO);
        syncCrushingState(golem, ClientboundCrushingBlowPayload.VisualEvent.WINDUP, Vec3.ZERO);
    }

    private static void releaseCrushingBlow(IronGolem golem, CompoundTag state) {
        golem.heal(golem.getMaxHealth() * 0.05F);
        if (!(golem.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = owner(golem, state);
        if (owner != null) {
            float damage = (float) (owner.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    * state.getDouble("damage_percent") / 100.0D);
            List<LivingEntity> affected = level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(golem.blockPosition()).inflate(7.5D),
                    living -> living != golem && living.isAlive() && !(living instanceof IronGolem)
                            && !(living instanceof AbstractVillager));
            affected.forEach(living -> {
                living.invulnerableTime = 0;
                living.hurt(golem.damageSources().mobAttack(golem), damage);
                playCrushingTargetEffect(level, living);
            });
        }
        playCrushingReleaseEffect(golem);
        syncCrushingState(golem, ClientboundCrushingBlowPayload.VisualEvent.RELEASED, golem.position());
    }

    private static int crushingThreshold(CompoundTag state) {
        return Math.max(1, (int) state.getDouble("charge_threshold"));
    }

    public static void processSnowballImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Snowball snowball)
                || !(snowball.getOwner() instanceof SnowGolem golem)) return;
        CompoundTag state = state(golem, COLD_CURRENT);
        if (state == null) return;
        int reduction = (int) (state.getDouble("time_reduction_seconds") * 20);
        if (coldStage(state) >= 4) playColdProjectileImpact(snowball);
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity target)) return;
        if (state.getInt("age") < 600 - reduction) return;
        float damage = coldProjectileDamage(state.getInt("age"), reduction,
                state.getDouble("attack_bonus_percent") / 100.0D);
        if (damage > 0.0F) target.hurt(golem.damageSources().mobProjectile(snowball, golem), damage);
    }

    static float coldProjectileDamage(int ageTicks, int reductionTicks, double bonus) {
        if (ageTicks < Math.max(0, 600 - reductionTicks)) return 0.0F;
        return (float) (4.0D * (ageTicks >= Math.max(0, 1800 - reductionTicks)
                ? 1.0D + Math.max(0.0D, bonus) : 1.0D));
    }

    private static void processCold(SnowGolem golem) {
        CompoundTag state = state(golem, COLD_CURRENT);
        if (state == null) return;
        int age = state.getInt("age") + 1; state.putInt("age", age);
        int reduction = (int) (state.getDouble("time_reduction_seconds") * 20);
        int[] thresholds = coldThresholds(state);
        for (int stage = 1; stage <= thresholds.length; stage++) {
            if (age == thresholds[stage - 1]) playColdMilestoneEffect(golem, stage);
        }
        setModifier(golem, Attributes.ATTACK_DAMAGE, COLD_ATTACK_FLAT, age >= 600 - reduction ? 4.0D : 0.0D,
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(golem, Attributes.MAX_HEALTH, COLD_HEALTH, age >= 600 - reduction ? 6.0D : 0.0D,
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(golem, Attributes.ARMOR, COLD_ARMOR_FLAT, age >= 900 - reduction ? 5.0D : 0.0D,
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(golem, Attributes.ARMOR_TOUGHNESS, COLD_TOUGHNESS, age >= 900 - reduction ? 7.0D : 0.0D,
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(golem, Attributes.ARMOR, COLD_ARMOR_PERCENT, age >= 1200 - reduction
                ? state.getDouble("armor_percent") / 100.0D : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(golem, Attributes.ATTACK_DAMAGE, COLD_ATTACK_PERCENT, age >= 1800 - reduction
                ? state.getDouble("attack_bonus_percent") / 100.0D : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (golem.tickCount % 20 == 0) {
            playColdAmbientEffect(golem, coldStage(state));
            syncColdState(golem);
        }
    }

    private static void setModifier(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute); if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0.0D) instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    static List<String> validateCrushingDefinition(AbilityDefinition d) { return validate(d, Set.of("charge_threshold", "damage_percent")); }
    static List<String> validateObsidianDefinition(AbilityDefinition d) { return validate(d, Set.of("max_shields", "heal_percent", "recharge")); }
    static List<String> validateColdDefinition(AbilityDefinition d) { return validate(d, Set.of("attack_bonus_percent", "armor_percent", "time_reduction_seconds")); }
    private static List<String> validate(AbilityDefinition definition, Set<String> keys) {
        try {
            parse(Config.CODEC, definition.effect().config(), "effect.config");
            Map<String, Double> merged = new HashMap<>();
            for (int i = 0; i < definition.ranks().values().size(); i++) {
                merged.putAll(parse(Rank.CODEC, definition.ranks().values().get(i), "ranks.values[" + i + "]").values());
                if (!merged.keySet().equals(keys)) throw new IllegalArgumentException("rank values must define " + keys);
            }
            return List.of();
        } catch (RuntimeException e) { return List.of(e.getMessage()); }
    }

    private static List<ActiveComponent> activeComponents(ServerPlayer player) {
        ArrayList<ActiveComponent> result = new ArrayList<>();
        Registry<AbilityDefinition> registry = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        registry.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().location())).forEach(entry -> {
            Optional<AbilityService.ActiveAbility> active = AbilityService.active(player, entry.getKey().location());
            if (active.isEmpty()) return;
            for (ResourceLocation type : List.of(CRUSHING_BLOW, OBSIDIAN_REINFORCEMENT, COLD_CURRENT))
                for (CompositeEffect.ComponentView view : CompositeEffect.componentsOfType(entry.getValue(), type)) {
                    AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active.get(), view);
                    Map<String, Double> merged = new HashMap<>();
                    projected.unlockedRankValues().forEach(value -> merged.putAll(parse(Rank.CODEC, value, "rank").values()));
                    result.add(new ActiveComponent(type, parse(Config.CODEC, view.config(), "effect.config"), merged));
                }
        }); return result;
    }

    private static CompoundTag state(Entity entity, ResourceLocation type) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(ROOT)) return null;
        CompoundTag root = data.getCompound(ROOT);
        return root.contains(type.getPath()) ? root.getCompound(type.getPath()) : null;
    }
    public static void syncObsidianStateTo(ServerPlayer player, Entity entity) {
        if (entity instanceof IronGolem golem) {
            CompoundTag state = state(golem, OBSIDIAN_REINFORCEMENT);
            if (state != null && state.hasUUID("owner")) {
                PacketDistributor.sendToPlayer(player, payload(golem, state));
            }
        }
    }
    public static void syncColdStateTo(ServerPlayer player, Entity entity) {
        if (entity instanceof SnowGolem golem) {
            CompoundTag state = state(golem, COLD_CURRENT);
            if (state != null && state.hasUUID("owner")) {
                PacketDistributor.sendToPlayer(player, coldPayload(golem, state));
            }
        }
    }
    public static void syncCrushingStateTo(ServerPlayer player, Entity entity) {
        if (entity instanceof IronGolem golem) {
            CompoundTag state = state(golem, CRUSHING_BLOW);
            if (state != null && state.hasUUID("owner")) {
                PacketDistributor.sendToPlayer(player, crushingPayload(golem, state,
                        ClientboundCrushingBlowPayload.VisualEvent.SYNC, Vec3.ZERO));
            }
        }
    }
    private static void syncColdState(SnowGolem golem) {
        CompoundTag state = state(golem, COLD_CURRENT);
        if (state != null && state.hasUUID("owner")) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(golem, coldPayload(golem, state));
        }
    }
    private static ClientboundColdCurrentPayload coldPayload(SnowGolem golem, CompoundTag state) {
        int[] thresholds = coldThresholds(state);
        return new ClientboundColdCurrentPayload(
                golem.getUUID(), state.getUUID("owner"), state.getInt("age"), thresholds[3], coldStage(state)
        );
    }
    private static int[] coldThresholds(CompoundTag state) {
        int reduction = (int) (state.getDouble("time_reduction_seconds") * 20);
        return new int[] {
                Math.max(1, 600 - reduction),
                Math.max(1, 900 - reduction),
                Math.max(1, 1200 - reduction),
                Math.max(1, 1800 - reduction)
        };
    }
    private static int coldStage(CompoundTag state) {
        int age = state.getInt("age");
        int stage = 0;
        for (int threshold : coldThresholds(state)) if (age >= threshold) stage++;
        return stage;
    }
    private static void playColdActivationEffect(SnowGolem golem) {
        if (!(golem.level() instanceof ServerLevel level)) return;
        BlockParticleOption blueIce = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLUE_ICE.defaultBlockState());
        level.sendParticles(blueIce, golem.getX(), golem.getY() + 0.95D, golem.getZ(),
                22, 0.42D, 0.75D, 0.42D, 0.07D);
        level.sendParticles(ModParticles.COLD_CURRENT_SNOWFLAKE.get(), golem.getX(), golem.getY() + 0.9D,
                golem.getZ(), 14, 0.45D, 0.65D, 0.45D, 0.045D);
        level.playSound(null, golem.blockPosition(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.NEUTRAL, 0.8F, 0.82F);
    }
    private static void playColdMilestoneEffect(SnowGolem golem, int stage) {
        if (!(golem.level() instanceof ServerLevel level)) return;
        level.sendParticles(ModParticles.COLD_CURRENT_SNOWFLAKE.get(), golem.getX(), golem.getY() + 0.9D,
                golem.getZ(), 10 + stage * 3, 0.58D, 0.72D, 0.58D, 0.075D);
        level.playSound(null, golem.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL,
                0.55F + stage * 0.08F, 0.72F + stage * 0.13F);
        syncColdState(golem);
    }
    private static void playColdAmbientEffect(SnowGolem golem, int stage) {
        if (!(golem.level() instanceof ServerLevel level)) return;
        int count = stage == 0 ? 1 : Math.min(3, stage);
        double y = stage >= 3 ? golem.getY() + 0.12D : golem.getY() + 0.95D;
        double spreadY = stage >= 3 ? 0.08D : 0.48D;
        level.sendParticles(ModParticles.COLD_CURRENT_SNOWFLAKE.get(), golem.getX(), y, golem.getZ(),
                count, stage >= 3 ? 0.72D : 0.34D, spreadY, stage >= 3 ? 0.72D : 0.34D, 0.012D);
    }
    private static void processColdSnowballTrail(Snowball snowball) {
        if (snowball.tickCount % 2 != 0 || !(snowball.getOwner() instanceof SnowGolem golem)
                || !(snowball.level() instanceof ServerLevel level)) return;
        CompoundTag state = state(golem, COLD_CURRENT);
        if (state == null || coldStage(state) < 4) return;
        level.sendParticles(ModParticles.COLD_CURRENT_SNOWFLAKE.get(), snowball.getX(), snowball.getY(), snowball.getZ(),
                1, 0.015D, 0.015D, 0.015D, 0.004D);
    }
    private static void playColdProjectileImpact(Snowball snowball) {
        if (!(snowball.level() instanceof ServerLevel level)) return;
        level.sendParticles(ModParticles.COLD_CURRENT_SNOWFLAKE.get(), snowball.getX(), snowball.getY(), snowball.getZ(),
                9, 0.24D, 0.24D, 0.24D, 0.055D);
    }
    private static void syncObsidianState(IronGolem golem) {
        syncObsidianState(golem, ClientboundGolemReinforcementPayload.VisualEvent.SYNC, Vec3.ZERO);
    }
    private static void syncCrushingState(IronGolem golem) {
        syncCrushingState(golem, ClientboundCrushingBlowPayload.VisualEvent.SYNC, Vec3.ZERO);
    }
    private static void syncCrushingState(IronGolem golem,
            ClientboundCrushingBlowPayload.VisualEvent visualEvent, Vec3 impactDirection) {
        CompoundTag state = state(golem, CRUSHING_BLOW);
        if (state != null && state.hasUUID("owner")) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(golem,
                    crushingPayload(golem, state, visualEvent, impactDirection));
        }
    }
    private static ClientboundCrushingBlowPayload crushingPayload(IronGolem golem, CompoundTag state,
            ClientboundCrushingBlowPayload.VisualEvent visualEvent, Vec3 impactDirection) {
        int releaseTicks = state.getInt("release_ticks");
        ClientboundCrushingBlowPayload.VisualEvent resolvedEvent = visualEvent;
        if (visualEvent == ClientboundCrushingBlowPayload.VisualEvent.SYNC && releaseTicks > 0) {
            resolvedEvent = releaseTicks <= 40 ? ClientboundCrushingBlowPayload.VisualEvent.WINDUP
                    : ClientboundCrushingBlowPayload.VisualEvent.RELEASED;
        }
        Vec3 eventVector = resolvedEvent == ClientboundCrushingBlowPayload.VisualEvent.RELEASED
                && impactDirection.lengthSqr() < 1.0E-8D ? golem.position() : impactDirection;
        return new ClientboundCrushingBlowPayload(
                golem.getUUID(), state.getUUID("owner"), state.getInt("charge"),
                Math.max(1, (int) state.getDouble("charge_threshold")),
                Math.max(0, (int) state.getDouble("damage_percent")), releaseTicks, resolvedEvent,
                (float) eventVector.x, (float) eventVector.y, (float) eventVector.z
        );
    }
    private static void syncObsidianState(
            IronGolem golem,
            ClientboundGolemReinforcementPayload.VisualEvent visualEvent,
            Vec3 impactDirection
    ) {
        CompoundTag state = state(golem, OBSIDIAN_REINFORCEMENT);
        if (state != null && state.hasUUID("owner")) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(golem, payload(golem, state, visualEvent, impactDirection));
        }
    }
    private static ClientboundGolemReinforcementPayload payload(IronGolem golem, CompoundTag state) {
        return payload(golem, state, ClientboundGolemReinforcementPayload.VisualEvent.SYNC, Vec3.ZERO);
    }
    private static ClientboundGolemReinforcementPayload payload(
            IronGolem golem,
            CompoundTag state,
            ClientboundGolemReinforcementPayload.VisualEvent visualEvent,
            Vec3 impactDirection
    ) {
        return new ClientboundGolemReinforcementPayload(
                golem.getUUID(), state.getUUID("owner"), state.getInt("charge"), OBSIDIAN_CHARGE_THRESHOLD,
                state.getInt("shields"), maxShields(state), visualEvent,
                (float) impactDirection.x, (float) impactDirection.y, (float) impactDirection.z
        );
    }
    private static int maxShields(CompoundTag state) {
        return Math.max(1, (int) state.getDouble("max_shields"));
    }
    private static void playActivationEffect(IronGolem golem) {
        if (!(golem.level() instanceof ServerLevel level)) return;
        BlockParticleOption obsidian = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState());
        level.sendParticles(obsidian, golem.getX(), golem.getY() + 1.4D, golem.getZ(), 28, 0.55D, 0.8D, 0.55D, 0.08D);
        level.playSound(null, golem.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.NEUTRAL, 0.8F, 0.75F);
    }
    private static void playCrushingActivationEffect(IronGolem golem) {
        if (!(golem.level() instanceof ServerLevel level)) return;
        BlockParticleOption anvil = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ANVIL.defaultBlockState());
        level.sendParticles(anvil, golem.getX(), golem.getY() + 1.2D, golem.getZ(),
                24, 0.54D, 0.82D, 0.54D, 0.07D);
        level.sendParticles(ModParticles.CRUSHING_BLOW_FORGE_SPARK.get(), golem.getX(), golem.getY() + 1.35D,
                golem.getZ(), 18, 0.42D, 0.58D, 0.42D, 0.12D);
        level.playSound(null, golem.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.9F, 0.78F);
    }
    private static void playCrushingChargeEffect(IronGolem golem, Vec3 direction, int charge, int threshold) {
        if (!(golem.level() instanceof ServerLevel level)) return;
        double x = golem.getX() + direction.x * 0.72D;
        double y = golem.getY() + golem.getBbHeight() * 0.55D + direction.y * 0.35D;
        double z = golem.getZ() + direction.z * 0.72D;
        level.sendParticles(ModParticles.CRUSHING_BLOW_FORGE_SPARK.get(), x, y, z,
                13, 0.24D, 0.28D, 0.24D, 0.16D);
        float progress = charge / (float) Math.max(1, threshold);
        level.playSound(null, golem.blockPosition(), SoundEvents.ANVIL_HIT, SoundSource.NEUTRAL,
                0.34F + progress * 0.16F, 1.18F - progress * 0.46F);
    }
    private static void playCrushingSteam(IronGolem golem, int charge, boolean releasing) {
        if (!(golem.level() instanceof ServerLevel level)) return;
        int count = releasing ? Math.min(14, 5 + charge * 2) : Math.min(10, 1 + charge * 2);
        level.sendParticles(ParticleTypes.CLOUD, golem.getX(), golem.getY() + 1.18D, golem.getZ(),
                count, 0.56D, 0.78D, 0.56D, releasing ? 0.045D : 0.025D);
        if (releasing && golem.tickCount % 6 == 0) {
            level.sendParticles(ParticleTypes.POOF, golem.getX(), golem.getY() + 0.5D, golem.getZ(),
                    Math.max(3, charge), 0.48D, 0.62D, 0.48D, 0.035D);
        }
    }
    private static void playCrushingReleaseEffect(IronGolem golem) {
        if (!(golem.level() instanceof ServerLevel level)) return;
        BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        level.sendParticles(stone, golem.getX(), golem.getY() + 0.12D, golem.getZ(),
                42, 0.9D, 0.16D, 0.9D, 0.14D);
        level.sendParticles(ModParticles.CRUSHING_BLOW_PRESSURE.get(), golem.getX(), golem.getY() + 0.18D,
                golem.getZ(), 30, 0.95D, 0.16D, 0.95D, 0.14D);
        level.playSound(null, golem.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1.2F, 0.54F);
        level.playSound(null, golem.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.NEUTRAL, 0.42F, 0.62F);
    }
    private static void playCrushingTargetEffect(ServerLevel level, LivingEntity target) {
        level.sendParticles(ModParticles.CRUSHING_BLOW_PRESSURE.get(), target.getX(),
                target.getY() + target.getBbHeight() + 0.25D, target.getZ(),
                7, Math.max(0.14D, target.getBbWidth() * 0.3D), 0.12D,
                Math.max(0.14D, target.getBbWidth() * 0.3D), 0.025D);
        level.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + 0.08D, target.getZ(),
                3, target.getBbWidth() * 0.3D, 0.03D, target.getBbWidth() * 0.3D, 0.015D);
    }
    private static void playChargeEffect(IronGolem golem) {
        if (!(golem.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, golem.getX(), golem.getY() + 1.2D, golem.getZ(), 2,
                0.35D, 0.45D, 0.35D, 0.015D);
    }
    private static void playShieldGainedEffect(IronGolem golem) {
        if (!(golem.level() instanceof ServerLevel level)) return;
        BlockParticleOption obsidian = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState());
        level.sendParticles(obsidian, golem.getX(), golem.getY() + 1.35D, golem.getZ(), 12, 0.5D, 0.55D, 0.5D, 0.04D);
        level.playSound(null, golem.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.65F, 0.55F);
    }
    private static void playShieldBreakEffect(IronGolem golem, Vec3 direction) {
        if (!(golem.level() instanceof ServerLevel level)) return;
        BlockParticleOption obsidian = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState());
        double x = golem.getX() + direction.x * 0.72D;
        double y = golem.getY() + 1.25D + direction.y * 0.55D;
        double z = golem.getZ() + direction.z * 0.72D;
        level.sendParticles(obsidian, x, y, z, 24, 0.38D, 0.55D, 0.38D, 0.13D);
        level.playSound(null, golem.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 0.9F, 0.7F);
    }
    private static ServerPlayer owner(LivingEntity entity, CompoundTag state) {
        if (!(entity.level() instanceof ServerLevel level) || !state.hasUUID("owner")) return null;
        return level.getServer().getPlayerList().getPlayer(state.getUUID("owner"));
    }
    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder(); Optional<T> parsed = codec.parse(input).resultOrPartial(error::append);
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }
    public record Config(List<Item> items, int itemCost) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.byNameCodec().listOf().fieldOf("items").forGetter(Config::items),
                Codec.intRange(1, 64).optionalFieldOf("item_cost", 1).forGetter(Config::itemCost)
        ).apply(instance, Config::new));
        public Config {
            items = List.copyOf(items);
            if (items.isEmpty()) throw new IllegalArgumentException("items must not be empty");
        }
    }
    public record Rank(Map<String, Double> values) {
        public static final Codec<Rank> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).xmap(Rank::new, Rank::values);
    }
    private record ActiveComponent(ResourceLocation type, Config config, Map<String, Double> rank) {}
}
