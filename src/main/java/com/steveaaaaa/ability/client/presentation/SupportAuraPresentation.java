package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class SupportAuraPresentation {
    private static final ResourceLocation ABILITY = AbilityMod.id("support_aura");
    private static final ResourceLocation ACTIVATE = AbilityMod.id("activate");
    private static final ResourceLocation LINK = AbilityMod.id("support_link");
    private static final ResourceLocation HEAL_PULSE = AbilityMod.id("heal_pulse");
    private static final ResourceLocation GOLDEN_SHIELD = AbilityMod.id("golden_shield");
    private static final ResourceLocation SHIELD_TEXTURE =
            AbilityMod.id("textures/entity/support_aura/golden_shield.png");
    private static final int SEGMENTS = 24;
    private static final int RINGS = 12;
    private static final Map<UUID, ShieldState> SHIELDS = new HashMap<>();
    private static ClientLevel activeLevel;

    private SupportAuraPresentation() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(ABILITY) || cue.action() == AbilityCue.Action.STOP) return;
        if (activeLevel != level) clear(level);
        if (cue.cueId().equals(ACTIVATE)) {
            emitActivation(level, cue);
        } else if (cue.cueId().equals(LINK)) {
            emitLink(level, cue);
        } else if (cue.cueId().equals(HEAL_PULSE)) {
            emitHealing(level, cue);
        } else if (cue.cueId().equals(GOLDEN_SHIELD) && cue.action() == AbilityCue.Action.START) {
            Entity target = level.getEntity(cue.targetEntityId());
            if (target != null) {
                long now = level.getGameTime();
                int duration = cue.durationTicks() < 0 ? 500 : cue.durationTicks();
                SHIELDS.put(target.getUUID(), new ShieldState(now, now + duration));
            }
        }
    }

    private static void emitActivation(ClientLevel level, AbilityCue cue) {
        Entity source = level.getEntity(cue.sourceEntityId());
        Vec3 center = source == null ? cue.position() : source.getBoundingBox().getCenter();
        Vec3 color = cue.direction();
        RandomSource random = RandomSource.create(cue.randomSeed());
        for (int index = 0; index < 9; index++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double radius = 0.18D + random.nextDouble() * 0.28D;
            addMote(level, center.add(
                    Mth.cos((float) angle) * radius,
                    (random.nextDouble() - 0.5D) * 0.65D,
                    Mth.sin((float) angle) * radius
            ), new Vec3(
                    Mth.cos((float) angle) * 0.016D,
                    0.012D + random.nextDouble() * 0.025D,
                    Mth.sin((float) angle) * 0.016D
            ), color);
        }
    }

    private static void emitLink(ClientLevel level, AbilityCue cue) {
        Entity source = level.getEntity(cue.sourceEntityId());
        Entity target = level.getEntity(cue.targetEntityId());
        if (source == null || target == null) return;
        Vec3 start = source.getBoundingBox().getCenter().add(0.0D, 0.18D, 0.0D);
        Vec3 end = target.getBoundingBox().getCenter();
        Vec3 direction = end.subtract(start);
        Vec3 velocity = direction.lengthSqr() < 1.0E-8D
                ? Vec3.ZERO
                : direction.normalize().scale(0.024D);
        Vec3 color = cue.direction();
        RandomSource random = RandomSource.create(cue.randomSeed());
        int points = Mth.clamp((int) Math.ceil(direction.length() * 2.4D), 5, 18);
        for (int index = 0; index < points; index++) {
            double progress = (index + 0.35D) / points;
            Vec3 position = start.lerp(end, progress).add(
                    random.nextGaussian() * 0.025D,
                    random.nextGaussian() * 0.025D,
                    random.nextGaussian() * 0.025D
            );
            addMote(level, position, velocity, color);
        }
    }

    private static void emitHealing(ClientLevel level, AbilityCue cue) {
        Entity target = level.getEntity(cue.targetEntityId());
        if (!(target instanceof LivingEntity living)) return;
        RandomSource random = RandomSource.create(cue.randomSeed());
        double radius = Math.max(0.25D, living.getBbWidth() * 0.58D);
        for (int index = 0; index < 10; index++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double horizontal = radius * (0.65D + random.nextDouble() * 0.45D);
            double x = living.getX() + Mth.cos((float) angle) * horizontal;
            double y = living.getY() + 0.08D
                    + random.nextDouble() * Math.max(0.35D, living.getBbHeight() * 0.82D);
            double z = living.getZ() + Mth.sin((float) angle) * horizontal;
            level.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z,
                    Mth.cos((float) angle) * 0.008D + random.nextGaussian() * 0.004D,
                    0.016D + random.nextDouble() * 0.034D,
                    Mth.sin((float) angle) * 0.008D + random.nextGaussian() * 0.004D);
        }
    }

    private static void addMote(ClientLevel level, Vec3 position, Vec3 velocity, Vec3 color) {
        SupportAuraParticle.addMote(level,
                position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z,
                (float) Mth.clamp(color.x, 0.0D, 1.0D),
                (float) Mth.clamp(color.y, 0.0D, 1.0D),
                (float) Mth.clamp(color.z, 0.0D, 1.0D));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != activeLevel) {
            clear(level);
            return;
        }
        long now = level.getGameTime();
        SHIELDS.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
    }

    @SubscribeEvent
    public static void renderShieldBack(RenderLivingEvent.Pre<?, ?> event) {
        ShieldState state = SHIELDS.get(event.getEntity().getUUID());
        if (state != null) renderShieldHalf(event, event.getEntity(), state, false);
    }

    @SubscribeEvent
    public static void renderShieldFront(RenderLivingEvent.Post<?, ?> event) {
        ShieldState state = SHIELDS.get(event.getEntity().getUUID());
        if (state != null) renderShieldHalf(event, event.getEntity(), state, true);
    }

    private static void renderShieldHalf(RenderLivingEvent<?, ?> event, LivingEntity entity,
            ShieldState state, boolean front) {
        float partialTick = event.getPartialTick();
        float time = entity.tickCount + partialTick;
        float age = Math.max(0.0F, entity.level().getGameTime() + partialTick - state.startedAt());
        float remaining = Math.max(0.0F, state.expiresAt() - entity.level().getGameTime() - partialTick);
        float appear = Mth.clamp(age / 13.0F, 0.0F, 1.0F);
        float disappear = Mth.clamp(remaining / 13.0F, 0.0F, 1.0F);
        float animation = Math.min(appear, disappear);
        float breathing = 1.0F + Mth.sin(time * 0.085F) * 0.018F;
        float halfWidth = entity.getBbWidth() * 0.5F;
        float halfHeight = entity.getBbHeight() * 0.5F;
        float enclosingRadius = Mth.sqrt(halfHeight * halfHeight + 2.0F * halfWidth * halfWidth)
                * 1.12F + 0.12F;
        float radius = enclosingRadius * breathing * (0.68F + easeOut(appear) * 0.32F);
        int alpha = Mth.clamp((int) ((170.0F + Mth.sin(time * 0.12F) * 22.0F) * animation), 0, 218);
        if (alpha <= 0) return;

        Vector3f look = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
        Vector3f left = Minecraft.getInstance().gameRenderer.getMainCamera().getLeftVector();
        Vector3f up = Minecraft.getInstance().gameRenderer.getMainCamera().getUpVector();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        pose.scale(radius, radius, radius);
        VertexConsumer vertices = event.getMultiBufferSource().getBuffer(
                RenderType.entityTranslucent(SHIELD_TEXTURE)
        );
        for (int ring = 0; ring < RINGS; ring++) {
            float lat0 = -Mth.HALF_PI + Mth.PI * ring / RINGS;
            float lat1 = -Mth.HALF_PI + Mth.PI * (ring + 1) / RINGS;
            float centerLat = (lat0 + lat1) * 0.5F;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                float lon0 = Mth.TWO_PI * segment / SEGMENTS;
                float lon1 = Mth.TWO_PI * (segment + 1) / SEGMENTS;
                float centerLon = (lon0 + lon1) * 0.5F;
                float centerCos = Mth.cos(centerLat);
                float facing = centerCos * Mth.cos(centerLon) * look.x
                        + Mth.sin(centerLat) * look.y
                        + centerCos * Mth.sin(centerLon) * look.z;
                if (front != (facing < 0.0F)) continue;
                sphereVertex(pose, vertices, lat0, lon0, left, up, alpha, event.getPackedLight());
                sphereVertex(pose, vertices, lat1, lon0, left, up, alpha, event.getPackedLight());
                sphereVertex(pose, vertices, lat1, lon1, left, up, alpha, event.getPackedLight());
                sphereVertex(pose, vertices, lat0, lon1, left, up, alpha, event.getPackedLight());
            }
        }
        pose.popPose();
    }

    private static void sphereVertex(PoseStack pose, VertexConsumer vertices,
            float latitude, float longitude, Vector3f left, Vector3f up, int alpha, int packedLight) {
        float cosLat = Mth.cos(latitude);
        float nx = cosLat * Mth.cos(longitude);
        float ny = Mth.sin(latitude);
        float nz = cosLat * Mth.sin(longitude);
        float u = 0.5F + (nx * left.x + ny * left.y + nz * left.z) * 0.5F;
        float v = 0.5F - (nx * up.x + ny * up.y + nz * up.z) * 0.5F;
        vertices.addVertex(pose.last(), nx, ny, nz)
                .setColor(255, 218, 112, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose.last(), nx, ny, nz);
    }

    private static float easeOut(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static void clear(ClientLevel level) {
        SHIELDS.clear();
        activeLevel = level;
    }

    private record ShieldState(long startedAt, long expiresAt) {
    }
}
