package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.registry.ModParticles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class PrimerPresentation {
    private static final ResourceLocation ABILITY = AbilityMod.id("primer");
    private static final ResourceLocation CHARGE = AbilityMod.id("charge");
    private static final ResourceLocation FIRE = AbilityMod.id("fire");
    private static final ResourceLocation IMPACT = AbilityMod.id("impact");
    private static final Map<Integer, ChargeState> CHARGES = new HashMap<>();
    private static final Map<Integer, ProjectileState> PROJECTILES = new HashMap<>();
    private static final Map<BakedModel, List<SurfacePoint>> SURFACE_CACHE = new WeakHashMap<>();
    private static final ThreadLocal<Float> CURRENT_CHARGE = new ThreadLocal<>();
    private static ClientLevel activeLevel;

    private PrimerPresentation() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(ABILITY)) {
            return;
        }
        if (activeLevel != level) {
            clear(level);
        }
        if (cue.cueId().equals(CHARGE)) {
            if (cue.action() == AbilityCue.Action.STOP) {
                CHARGES.remove(cue.targetEntityId());
            } else if (cue.action() == AbilityCue.Action.START) {
                CHARGES.put(cue.targetEntityId(), new ChargeState(
                        level.getGameTime(), Math.max(1, cue.durationTicks()), false));
            }
        } else if (cue.cueId().equals(FIRE) && cue.action() == AbilityCue.Action.PULSE) {
            PROJECTILES.put(cue.targetEntityId(), new ProjectileState(level.getGameTime(), cue.randomSeed()));
            emitFire(level, cue);
        } else if (cue.cueId().equals(IMPACT) && cue.action() == AbilityCue.Action.PULSE) {
            PROJECTILES.remove(cue.targetEntityId());
            emitImpact(level, cue);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != activeLevel) {
            clear(level);
            return;
        }
        long gameTime = level.getGameTime();
        CHARGES.entrySet().removeIf(entry -> {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof AbstractClientPlayer player) || player.isRemoved()) {
                return true;
            }
            ChargeState state = entry.getValue();
            if (!state.readySoundPlayed() && gameTime - state.startedAt() >= state.requiredTicks()) {
                level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                        SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                        0.42F, 1.55F, false);
                entry.setValue(state.withReadySoundPlayed());
            }
            return false;
        });

        RandomSource random = level.getRandom();
        Iterator<Map.Entry<Integer, ProjectileState>> iterator = PROJECTILES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ProjectileState> entry = iterator.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LargeFireball fireball)) {
                if (gameTime - entry.getValue().startedAt() > 20L) {
                    iterator.remove();
                }
                continue;
            }
            Vec3 movement = fireball.getDeltaMovement();
            Vec3 back = movement.lengthSqr() < 1.0E-6D ? Vec3.ZERO : movement.normalize().scale(-0.36D);
            for (int index = 0; index < 2; index++) {
                level.addParticle(ParticleTypes.SMALL_FLAME, true,
                        fireball.getX() + back.x + random.nextGaussian() * 0.07D,
                        fireball.getY() + back.y + random.nextGaussian() * 0.07D,
                        fireball.getZ() + back.z + random.nextGaussian() * 0.07D,
                        back.x * 0.12D + random.nextGaussian() * 0.025D,
                        back.y * 0.12D + random.nextGaussian() * 0.025D,
                        back.z * 0.12D + random.nextGaussian() * 0.025D);
            }
        }
    }

    public static void beginHeldItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext) {
        CURRENT_CHARGE.remove();
        if (!(entity instanceof AbstractClientPlayer player)
                || !isHeldContext(displayContext)
                || stack != player.getOffhandItem()
                || !stack.is(Items.FIRE_CHARGE)) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        ChargeState state = CHARGES.get(player.getId());
        if (level == null || state == null) {
            return;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float progress = Mth.clamp(
                (level.getGameTime() + partialTick - state.startedAt()) / state.requiredTicks(), 0.0F, 1.0F);
        CURRENT_CHARGE.set(progress);
    }

    public static void endHeldItem() {
        CURRENT_CHARGE.remove();
    }

    public static void renderCurrentItem(PoseStack poseStack, MultiBufferSource buffers,
            ItemStack stack, BakedModel model) {
        Float progressValue = CURRENT_CHARGE.get();
        if (progressValue == null || model.isCustomRenderer() || !stack.is(Items.FIRE_CHARGE)) {
            return;
        }
        float progress = progressValue;
        float time = (float) (System.nanoTime() / 1_000_000_000.0D);
        List<SurfacePoint> surface = SURFACE_CACHE.computeIfAbsent(model,
                ignored -> collectSurfacePoints(model, stack));
        if (surface.isEmpty()) {
            return;
        }
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        int count = 4 + Mth.floor(progress * 7.0F);
        Vec3 center = new Vec3(0.5D, 0.5D, 0.5D);
        for (int index = 0; index < count; index++) {
            SurfacePoint point = surface.get(Math.floorMod(index * 11 + Mth.floor(time * 8.0F), surface.size()));
            float inward = (time * (0.7F + progress * 0.65F) + index / (float) count) % 1.0F;
            Vec3 position = point.position().lerp(center, inward * inward)
                    .add(point.normal().scale(0.012D * (1.0F - inward)));
            float flicker = 0.6F + Mth.sin(time * 11.0F + index * 1.9F) * 0.25F;
            int green = progress >= 1.0F
                    ? Mth.floor(105.0F + flicker * 34.0F)
                    : 92 + Mth.floor(progress * 48.0F);
            renderPixelCube(poseStack, vertices, position, 0.007F + progress * 0.004F,
                    255, green, 12,
                    Mth.clamp((int) (150.0F + flicker * 90.0F), 0, 255));
        }
        if (progress >= 1.0F) {
            float pulse = 0.018F + (0.5F + 0.5F * Mth.sin(time * 8.0F)) * 0.016F;
            renderPixelCube(poseStack, vertices, center, pulse, 255, 122, 24, 225);
        }
    }

    private static void emitFire(ClientLevel level, AbilityCue cue) {
        Vec3 direction = cue.direction().lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D)
                : cue.direction().normalize();
        RandomSource random = RandomSource.create(cue.randomSeed());
        level.addParticle(ModParticles.PRIMER_IGNITION.get(), true,
                cue.position().x, cue.position().y, cue.position().z, 0.0D, 0.0D, 0.0D);
        for (int index = 0; index < 13; index++) {
            Vec3 velocity = direction.scale(-0.10D - random.nextDouble() * 0.17D)
                    .add(random.nextGaussian() * 0.055D,
                            random.nextGaussian() * 0.055D,
                            random.nextGaussian() * 0.055D);
            level.addParticle(ModParticles.PRIMER_EMBER.get(), true,
                    cue.position().x, cue.position().y, cue.position().z,
                    velocity.x, velocity.y, velocity.z);
        }
    }

    private static void emitImpact(ClientLevel level, AbilityCue cue) {
        RandomSource random = RandomSource.create(cue.randomSeed());
        level.addParticle(ModParticles.PRIMER_BURST.get(), true,
                cue.position().x, cue.position().y, cue.position().z, 0.0D, 0.0D, 0.0D);
        for (int index = 0; index < 28; index++) {
            Vec3 direction = randomDirection(random);
            double speed = 0.08D + random.nextDouble() * 0.30D;
            level.addParticle(ModParticles.PRIMER_EMBER.get(), true,
                    cue.position().x, cue.position().y, cue.position().z,
                    direction.x * speed, direction.y * speed, direction.z * speed);
        }
        for (int index = 0; index < 5; index++) {
            level.addParticle(ParticleTypes.SMOKE,
                    cue.position().x + random.nextGaussian() * 0.16D,
                    cue.position().y + random.nextDouble() * 0.22D,
                    cue.position().z + random.nextGaussian() * 0.16D,
                    random.nextGaussian() * 0.025D,
                    0.035D + random.nextDouble() * 0.045D,
                    random.nextGaussian() * 0.025D);
        }
    }

    private static List<SurfacePoint> collectSurfacePoints(BakedModel model, ItemStack stack) {
        List<SurfacePoint> result = new ArrayList<>();
        RandomSource random = RandomSource.create(71L);
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            random.setSeed(71L);
            addQuadPoints(result, pass.getQuads(null, null, random));
            for (Direction direction : Direction.values()) {
                random.setSeed(71L);
                addQuadPoints(result, pass.getQuads(null, direction, random));
            }
        }
        return result;
    }

    private static void addQuadPoints(List<SurfacePoint> result, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            Vec3 normal = Vec3.atLowerCornerOf(quad.getDirection().getNormal());
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * stride;
                result.add(new SurfacePoint(new Vec3(
                        Float.intBitsToFloat(data[offset]),
                        Float.intBitsToFloat(data[offset + 1]),
                        Float.intBitsToFloat(data[offset + 2])), normal));
            }
        }
    }

    private static void renderPixelCube(PoseStack poseStack, VertexConsumer vertices, Vec3 position,
            float size, int red, int green, int blue, int alpha) {
        float x0 = (float) position.x - size;
        float x1 = (float) position.x + size;
        float y0 = (float) position.y - size;
        float y1 = (float) position.y + size;
        float z0 = (float) position.z - size;
        float z1 = (float) position.z + size;
        cubeFace(poseStack, vertices, red, green, blue, alpha, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        cubeFace(poseStack, vertices, red, green, blue, alpha, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
        cubeFace(poseStack, vertices, red, green, blue, alpha, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
        cubeFace(poseStack, vertices, red, green, blue, alpha, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
    }

    private static void cubeFace(PoseStack poseStack, VertexConsumer vertices, int red, int green, int blue, int alpha,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3) {
        vertices.addVertex(poseStack.last(), x0, y0, z0).setColor(red, green, blue, alpha);
        vertices.addVertex(poseStack.last(), x1, y1, z1).setColor(red, green, blue, alpha);
        vertices.addVertex(poseStack.last(), x2, y2, z2).setColor(red, green, blue, alpha);
        vertices.addVertex(poseStack.last(), x3, y3, z3).setColor(red, green, blue, alpha);
    }

    private static Vec3 randomDirection(RandomSource random) {
        Vec3 direction;
        do {
            direction = new Vec3(random.nextDouble() * 2.0D - 1.0D,
                    random.nextDouble() * 1.6D - 0.5D,
                    random.nextDouble() * 2.0D - 1.0D);
        } while (direction.lengthSqr() < 1.0E-5D || direction.lengthSqr() > 1.0D);
        return direction.normalize();
    }

    private static boolean isHeldContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear(null);
    }

    private static void clear(ClientLevel level) {
        CHARGES.clear();
        PROJECTILES.clear();
        SURFACE_CACHE.clear();
        CURRENT_CHARGE.remove();
        activeLevel = level;
    }

    private record ChargeState(long startedAt, int requiredTicks, boolean readySoundPlayed) {
        private ChargeState withReadySoundPlayed() {
            return new ChargeState(startedAt, requiredTicks, true);
        }
    }

    private record ProjectileState(long startedAt, long seed) {
    }

    private record SurfacePoint(Vec3 position, Vec3 normal) {
    }
}
