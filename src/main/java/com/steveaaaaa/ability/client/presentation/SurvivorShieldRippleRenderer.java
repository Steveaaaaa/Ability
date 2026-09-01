package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class SurvivorShieldRippleRenderer {
    private static final net.minecraft.resources.ResourceLocation SURVIVOR = AbilityMod.id("survivor");
    private static final net.minecraft.resources.ResourceLocation SHIELD_IMPACT = AbilityMod.id("shield_impact");
    private static final int DURATION_TICKS = 15;
    private static final int SEGMENTS = 20;
    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static ClientLevel activeLevel;

    private SurvivorShieldRippleRenderer() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(SURVIVOR) || !cue.cueId().equals(SHIELD_IMPACT)
                || cue.action() == AbilityCue.Action.STOP) {
            return;
        }
        if (activeLevel != level) {
            RIPPLES.clear();
            activeLevel = level;
        }
        Vec3 normal = cue.direction().lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : cue.direction().normalize();
        if (RIPPLES.size() >= 32) {
            RIPPLES.remove(0);
        }
        RIPPLES.add(new Ripple(cue.targetEntityId(), level.getGameTime(), normal, cue.rank()));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || RIPPLES.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != activeLevel) {
            RIPPLES.clear();
            activeLevel = level;
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double visualTime = level.getGameTime() + partialTick;
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        Iterator<Ripple> iterator = RIPPLES.iterator();
        while (iterator.hasNext()) {
            Ripple ripple = iterator.next();
            float age = (float) (visualTime - ripple.startedAt());
            if (age >= DURATION_TICKS) {
                iterator.remove();
                continue;
            }
            Entity target = level.getEntity(ripple.targetEntityId());
            if (target == null || target.isRemoved()) continue;
            renderRipple(event.getPoseStack(), vertices, cameraPosition, target, ripple, age, partialTick);
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static void renderRipple(PoseStack poseStack, VertexConsumer vertices, Vec3 cameraPosition,
            Entity target, Ripple ripple, float age, float partialTick) {
        float progress = Mth.clamp(age / DURATION_TICKS, 0.0F, 1.0F);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        int rank = Mth.clamp(ripple.rank(), 1, 6);
        int alpha = Mth.clamp((int) ((1.0F - progress) * (145.0F + rank * 13.0F)), 0, 225);

        double x = Mth.lerp(partialTick, target.xo, target.getX()) - cameraPosition.x;
        double y = Mth.lerp(partialTick, target.yo, target.getY())
                + target.getBbHeight() * 0.5D - cameraPosition.y;
        double z = Mth.lerp(partialTick, target.zo, target.getZ()) - cameraPosition.z;
        float radiusX = Math.max(0.42F, target.getBbWidth() * 0.68F);
        float radiusY = Math.max(0.88F, target.getBbHeight() * 0.58F);
        float radiusZ = radiusX;
        Vec3 normal = ripple.normal();
        Vec3 reference = Math.abs(normal.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 tangent = reference.cross(normal).normalize();
        Vec3 bitangent = normal.cross(tangent).normalize();

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        float mainAngle = 0.08F + eased * 0.92F;
        sphereRing(poseStack, vertices, normal, tangent, bitangent,
                radiusX, radiusY, radiusZ, mainAngle, 0.055F,
                122, 211, 255, alpha, 1.0F, age * 0.035F);
        sphereRing(poseStack, vertices, normal, tangent, bitangent,
                radiusX, radiusY, radiusZ, Math.max(0.04F, mainAngle - 0.20F), 0.026F,
                188, 142, 255, alpha * 2 / 3, 0.68F, -age * 0.025F);
        if (rank >= 4) {
            sphereRing(poseStack, vertices, normal, tangent, bitangent,
                    radiusX, radiusY, radiusZ, Math.max(0.04F, mainAngle - 0.36F), 0.018F,
                    224, 213, 255, alpha / 2, 0.5F, age * 0.02F);
        }
        renderRunes(poseStack, vertices, normal, tangent, bitangent,
                radiusX, radiusY, radiusZ, mainAngle * 0.62F,
                alpha * 3 / 4, age * 0.028F);
        poseStack.popPose();
    }

    private static void sphereRing(PoseStack poseStack, VertexConsumer vertices,
            Vec3 normal, Vec3 tangent, Vec3 bitangent,
            float radiusX, float radiusY, float radiusZ,
            float angle, float thickness, int red, int green, int blue, int alpha,
            float fill, float rotation) {
        if (alpha <= 0) return;
        float inner = Math.max(0.015F, angle - thickness);
        float outer = angle + thickness;
        for (int segment = 0; segment < SEGMENTS; segment++) {
            if (fill < 0.99F && ((segment * 37) % 100) / 100.0F > fill) continue;
            float a0 = rotation + Mth.TWO_PI * segment / SEGMENTS;
            float a1 = rotation + Mth.TWO_PI * (segment + 1) / SEGMENTS;
            shieldVertex(poseStack, vertices, sphereDirection(normal, tangent, bitangent, inner, a0),
                    radiusX, radiusY, radiusZ, red, green, blue, alpha);
            shieldVertex(poseStack, vertices, sphereDirection(normal, tangent, bitangent, outer, a0),
                    radiusX, radiusY, radiusZ, red, green, blue, alpha);
            shieldVertex(poseStack, vertices, sphereDirection(normal, tangent, bitangent, outer, a1),
                    radiusX, radiusY, radiusZ, red, green, blue, alpha);
            shieldVertex(poseStack, vertices, sphereDirection(normal, tangent, bitangent, inner, a1),
                    radiusX, radiusY, radiusZ, red, green, blue, alpha);
        }
    }

    private static void renderRunes(PoseStack poseStack, VertexConsumer vertices,
            Vec3 normal, Vec3 tangent, Vec3 bitangent,
            float radiusX, float radiusY, float radiusZ,
            float angle, int alpha, float rotation) {
        for (int index = 0; index < 4; index++) {
            float center = rotation + Mth.HALF_PI * index;
            float halfWidth = 0.075F;
            float halfHeight = 0.11F;
            Vec3 inner = sphereDirection(normal, tangent, bitangent, Math.max(0.03F, angle - halfHeight), center);
            Vec3 right = sphereDirection(normal, tangent, bitangent, angle, center + halfWidth);
            Vec3 outer = sphereDirection(normal, tangent, bitangent, angle + halfHeight, center);
            Vec3 left = sphereDirection(normal, tangent, bitangent, angle, center - halfWidth);
            shieldVertex(poseStack, vertices, inner, radiusX, radiusY, radiusZ, 207, 184, 255, alpha);
            shieldVertex(poseStack, vertices, right, radiusX, radiusY, radiusZ, 207, 184, 255, alpha);
            shieldVertex(poseStack, vertices, outer, radiusX, radiusY, radiusZ, 207, 184, 255, alpha);
            shieldVertex(poseStack, vertices, left, radiusX, radiusY, radiusZ, 207, 184, 255, alpha);
        }
    }

    private static Vec3 sphereDirection(Vec3 normal, Vec3 tangent, Vec3 bitangent,
            float angle, float rotation) {
        Vec3 radial = tangent.scale(Mth.cos(rotation)).add(bitangent.scale(Mth.sin(rotation)));
        return normal.scale(Mth.cos(angle)).add(radial.scale(Mth.sin(angle))).normalize();
    }

    private static void shieldVertex(PoseStack poseStack, VertexConsumer vertices, Vec3 direction,
            float radiusX, float radiusY, float radiusZ,
            int red, int green, int blue, int alpha) {
        vertices.addVertex(poseStack.last(),
                        (float) direction.x * radiusX,
                        (float) direction.y * radiusY,
                        (float) direction.z * radiusZ)
                .setColor(red, green, blue, alpha);
    }

    private record Ripple(int targetEntityId, long startedAt, Vec3 normal, int rank) {
    }
}
