package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.effect.HarvestEffect;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.registry.ModParticles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class HarvestPresentation {
    private static final ResourceLocation SWEEP = AbilityMod.id("sweep");
    private static final RenderType TRAIL_TYPE = RenderType.lightning();
    private static final int DURATION_TICKS = 14;
    private static final int SEGMENTS = 14;
    private static final List<Slash> SLASHES = new ArrayList<>();
    private static ClientLevel activeLevel;

    private HarvestPresentation() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(HarvestEffect.TYPE) || !cue.cueId().equals(SWEEP)
                || cue.action() == AbilityCue.Action.STOP) return;
        if (activeLevel != level) clear(level);
        Vec3 encodedDirection = cue.direction();
        float intensity = Mth.clamp((float) encodedDirection.length(), 0.4F, 1.0F);
        Vec3 forward = encodedDirection.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : encodedDirection.normalize();
        Entity source = level.getEntity(cue.sourceEntityId());
        int handSign = source instanceof LivingEntity living && living.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        if (SLASHES.size() >= 32) SLASHES.removeFirst();
        SLASHES.add(new Slash(
                cue.position(), forward, level.getGameTime(), intensity,
                cue.targetEntityId(), handSign
        ));
        emitChaff(level, cue.position(), forward, intensity, cue.randomSeed());
    }

    private static void emitChaff(ClientLevel level, Vec3 position, Vec3 forward,
            float intensity, long seed) {
        RandomSource random = RandomSource.create(seed);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        int count = 4 + Math.round(intensity * 5.0F);
        for (int index = 0; index < count; index++) {
            Vec3 spawn = position.add(
                    side.scale((random.nextDouble() - 0.5D) * (0.65D + intensity * 0.45D))
            ).add(0.0D, (random.nextDouble() - 0.5D) * 0.75D, 0.0D);
            double sideSpeed = (random.nextDouble() - 0.5D) * (0.08D + intensity * 0.07D);
            Vec3 velocity = side.scale(sideSpeed).add(forward.scale(0.018D + random.nextDouble() * 0.035D));
            level.addParticle(ModParticles.HARVEST_CHAFF.get(),
                    spawn.x, spawn.y, spawn.z,
                    velocity.x, 0.035D + random.nextDouble() * 0.075D, velocity.z);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SLASHES.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != activeLevel) {
            clear(level);
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double visualTime = level.getGameTime() + partialTick;
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(TRAIL_TYPE);
        Iterator<Slash> iterator = SLASHES.iterator();
        while (iterator.hasNext()) {
            Slash slash = iterator.next();
            float age = (float) (visualTime - slash.startedAt());
            if (age >= DURATION_TICKS) {
                iterator.remove();
                continue;
            }
            renderSlash(level, poseStack, vertices, cameraPosition, slash, age, partialTick);
        }
        buffers.endBatch(TRAIL_TYPE);
    }

    private static void renderSlash(ClientLevel level, PoseStack poseStack, VertexConsumer vertices,
            Vec3 cameraPosition, Slash slash, float age, float partialTick) {
        float progress = Mth.clamp(age / DURATION_TICKS, 0.0F, 1.0F);
        float head = easeOut(Mth.clamp(progress / 0.46F, 0.0F, 1.0F));
        float tail = smoothStep(Mth.clamp((progress - 0.13F) / 0.70F, 0.0F, 1.0F));
        if (tail >= head - 0.005F) return;
        Vec3 forward = slash.forward();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        Entity target = level.getEntity(slash.targetEntityId());
        Vec3 center = slash.position();
        double horizontalRadius = 0.68D + slash.intensity() * 0.34D;
        double verticalRadius = 0.56D + slash.intensity() * 0.25D;
        if (target != null && !target.isRemoved()) {
            center = new Vec3(
                    Mth.lerp(partialTick, target.xo, target.getX()),
                    Mth.lerp(partialTick, target.yo, target.getY()) + target.getBbHeight() * 0.52D,
                    Mth.lerp(partialTick, target.zo, target.getZ())
            ).subtract(forward.scale(target.getBbWidth() * 0.56D + 0.04D));
            horizontalRadius = Math.max(horizontalRadius, target.getBbWidth() * 0.72D);
            verticalRadius = Math.max(verticalRadius, target.getBbHeight() * 0.38D);
        }

        float globalFade = Mth.clamp((1.0F - progress) / 0.58F, 0.38F, 1.0F);
        renderRibbon(poseStack, vertices, cameraPosition, center, forward, side, slash.handSign(),
                horizontalRadius, verticalRadius, tail, head,
                0.13F + slash.intensity() * 0.045F,
                61, 66, 20, Math.round(175.0F * globalFade), Vec3.ZERO);
        renderRibbon(poseStack, vertices, cameraPosition, center, forward, side, slash.handSign(),
                horizontalRadius, verticalRadius, tail, head,
                0.050F + slash.intensity() * 0.025F,
                239, 184, 54, Math.round(245.0F * globalFade), forward.scale(-0.006D));
    }

    private static void renderRibbon(PoseStack poseStack, VertexConsumer vertices, Vec3 cameraPosition,
            Vec3 center, Vec3 forward, Vec3 side, int handSign,
            double horizontalRadius, double verticalRadius, float tail, float head,
            float width, int red, int green, int blue, int alpha, Vec3 layerOffset) {
        float span = head - tail;
        for (int segment = 0; segment < SEGMENTS; segment++) {
            float fraction0 = segment / (float) SEGMENTS;
            float fraction1 = (segment + 1) / (float) SEGMENTS;
            float t0 = Mth.lerp(fraction0, tail, head);
            float t1 = Mth.lerp(fraction1, tail, head);
            Vec3 p0 = trajectoryPoint(center, forward, side, handSign,
                    horizontalRadius, verticalRadius, t0).add(layerOffset);
            Vec3 p1 = trajectoryPoint(center, forward, side, handSign,
                    horizontalRadius, verticalRadius, t1).add(layerOffset);
            Vec3 w0 = ribbonWidth(center, forward, side, handSign,
                    horizontalRadius, verticalRadius, t0, width * endpointWidth(t0));
            Vec3 w1 = ribbonWidth(center, forward, side, handSign,
                    horizontalRadius, verticalRadius, t1, width * endpointWidth(t1));
            float leading = 0.30F + 0.70F * fraction1;
            int segmentAlpha = Mth.clamp(Math.round(alpha * leading * Math.min(1.0F, span * 5.0F)), 0, 255);
            vertex(poseStack, vertices, p0.subtract(w0).subtract(cameraPosition), red, green, blue, segmentAlpha);
            vertex(poseStack, vertices, p1.subtract(w1).subtract(cameraPosition), red, green, blue, segmentAlpha);
            vertex(poseStack, vertices, p1.add(w1).subtract(cameraPosition), red, green, blue, segmentAlpha);
            vertex(poseStack, vertices, p0.add(w0).subtract(cameraPosition), red, green, blue, segmentAlpha);
        }
    }

    private static Vec3 trajectoryPoint(Vec3 center, Vec3 forward, Vec3 side, int handSign,
            double horizontalRadius, double verticalRadius, float t) {
        double inverse = 1.0D - t;
        Vec3 start = side.scale(handSign * horizontalRadius).add(0.0D, verticalRadius * 0.62D, 0.0D)
                .subtract(forward.scale(0.20D));
        Vec3 control = side.scale(handSign * horizontalRadius * 0.18D).add(0.0D, verticalRadius, 0.0D);
        Vec3 end = side.scale(-handSign * horizontalRadius).add(0.0D, -verticalRadius * 0.62D, 0.0D)
                .add(forward.scale(0.22D));
        return center.add(start.scale(inverse * inverse))
                .add(control.scale(2.0D * inverse * t))
                .add(end.scale(t * t));
    }

    private static Vec3 ribbonWidth(Vec3 center, Vec3 forward, Vec3 side, int handSign,
            double horizontalRadius, double verticalRadius, float t, float width) {
        float before = Math.max(0.0F, t - 0.012F);
        float after = Math.min(1.0F, t + 0.012F);
        Vec3 tangent = trajectoryPoint(center, forward, side, handSign,
                horizontalRadius, verticalRadius, after).subtract(
                trajectoryPoint(center, forward, side, handSign, horizontalRadius, verticalRadius, before)
        );
        Vec3 perpendicular = forward.cross(tangent);
        if (perpendicular.lengthSqr() < 1.0E-8D) perpendicular = new Vec3(0.0D, 1.0D, 0.0D);
        return perpendicular.normalize().scale(width);
    }

    private static float endpointWidth(float t) {
        return 0.22F + 0.78F * Mth.sin(Mth.PI * Mth.clamp(t, 0.0F, 1.0F));
    }

    private static float easeOut(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static void vertex(PoseStack poseStack, VertexConsumer vertices, Vec3 position,
            int red, int green, int blue, int alpha) {
        vertices.addVertex(poseStack.last(), (float) position.x, (float) position.y, (float) position.z)
                .setColor(red, green, blue, alpha);
    }

    private static void clear(ClientLevel level) {
        SLASHES.clear();
        activeLevel = level;
    }

    private record Slash(
            Vec3 position,
            Vec3 forward,
            long startedAt,
            float intensity,
            int targetEntityId,
            int handSign
    ) {
    }
}
