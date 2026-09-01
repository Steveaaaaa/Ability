package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class WellPreparedSalvationRenderer {
    private static final ResourceLocation SALVATION = AbilityMod.id("salvation");
    private static final ResourceLocation ANGEL_MATERIAL =
            AbilityMod.id("textures/effect/well_prepared_angel_material.png");
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final WellPreparedAngelModel ANGEL_MODEL =
            new WellPreparedAngelModel(WellPreparedAngelModel.createLayer().bakeRoot());
    private static final Map<Integer, ActiveSalvation> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    private WellPreparedSalvationRenderer() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.cueId().equals(SALVATION)) return;
        if (activeLevel != level) {
            ACTIVE.clear();
            activeLevel = level;
        }
        if (cue.action() == AbilityCue.Action.STOP) {
            ACTIVE.remove(cue.targetEntityId());
            return;
        }
        long start = level.getGameTime();
        int duration = cue.durationTicks() > 0 ? cue.durationTicks() : 40;
        ACTIVE.put(cue.targetEntityId(), new ActiveSalvation(start, start + duration, cue.randomSeed()));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != activeLevel) {
            ACTIVE.clear();
            activeLevel = level;
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double visualTime = level.getGameTime() + partialTick;
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Iterator<Map.Entry<Integer, ActiveSalvation>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ActiveSalvation> entry = iterator.next();
            ActiveSalvation active = entry.getValue();
            if (visualTime >= active.endsAt()) {
                iterator.remove();
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || entity.isRemoved()) continue;
            renderSalvation(event.getPoseStack(), buffers, camera, cameraPosition,
                    entity, active, visualTime, partialTick);
        }
    }

    private static void renderSalvation(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
            Camera camera, Vec3 cameraPosition, Entity entity, ActiveSalvation active,
            double visualTime, float partialTick) {
        float age = (float) (visualTime - active.startedAt());
        float total = Math.max(1.0F, active.endsAt() - active.startedAt());
        float progress = Mth.clamp(age / total, 0.0F, 1.0F);
        float descent = easeOutCubic(Mth.clamp(age / 24.0F, 0.0F, 1.0F));
        float fadeIn = Mth.clamp(age / 5.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((total - age) / 7.0F, 0.0F, 1.0F);
        float visibility = fadeIn * fadeOut;
        float prayerPulse = 0.5F + 0.5F * Mth.sin(age * 0.24F);

        double x = Mth.lerp(partialTick, entity.xo, entity.getX());
        double y = Mth.lerp(partialTick, entity.yo, entity.getY());
        double z = Mth.lerp(partialTick, entity.zo, entity.getZ());
        double headY = y + entity.getBbHeight();
        double bottomOffset = Mth.lerp(descent, 1.55D, 0.38D);
        float angelScale = (0.82F + prayerPulse * 0.018F) * (0.82F + fadeIn * 0.18F);
        float angelHeight = 2.0F * angelScale;
        double angelBaseY = headY + bottomOffset
                + (descent >= 1.0F ? Mth.sin(age * 0.14F) * 0.035F : 0.0F);
        double angelCenterY = angelBaseY + angelHeight * 0.5D;
        double relativeX = x - cameraPosition.x;
        double relativeY = y - cameraPosition.y;
        double relativeZ = z - cameraPosition.z;

        RenderType lightType = RenderType.entityTranslucentEmissive(WHITE_TEXTURE);
        VertexConsumer lightVertices = buffers.getBuffer(lightType);
        renderLightShafts(poseStack, lightVertices, camera, relativeX,
                headY - cameraPosition.y, relativeZ, angelCenterY - cameraPosition.y,
                age, visibility);
        renderMotes(poseStack, lightVertices, camera, relativeX, relativeY, relativeZ,
                entity.getBbHeight(), age, visibility, active.seed());
        buffers.endBatch(lightType);

        RenderType angelMaterialType = RenderType.entityTranslucentEmissive(ANGEL_MATERIAL);
        RenderType angelColorType = RenderType.entityTranslucentEmissive(WHITE_TEXTURE);
        VertexConsumer angelMaterial = buffers.getBuffer(angelMaterialType);
        VertexConsumer angelColors = buffers.getBuffer(angelColorType);
        int angelAlpha = Mth.clamp((int) ((132.0F + prayerPulse * 50.0F) * visibility), 0, 190);
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        renderAngelModel(poseStack, angelMaterial, angelColors, relativeX,
                angelBaseY - cameraPosition.y, relativeZ, angelScale, yaw, age, angelAlpha);
        buffers.endBatch(angelMaterialType);
        buffers.endBatch(angelColorType);

        RenderType ringType = RenderType.lightning();
        VertexConsumer ringVertices = buffers.getBuffer(ringType);
        renderGroundRings(poseStack, ringVertices, relativeX, relativeY + 0.035D,
                relativeZ, progress, age, visibility);
        buffers.endBatch(ringType);
    }

    private static void renderAngelModel(PoseStack poseStack, VertexConsumer material,
            VertexConsumer colors, double x, double y, double z, float scale,
            float yaw, float age, int alpha) {
        if (alpha <= 0) return;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.scale(-scale, -scale, scale);
        ANGEL_MODEL.setupAnimation(age);
        ANGEL_MODEL.render(poseStack, material, colors, alpha);
        poseStack.popPose();
    }

    private static void renderLightShafts(PoseStack poseStack, VertexConsumer vertices, Camera camera,
            double x, double headY, double z, double angelCenterY, float age, float visibility) {
        float span = (float) Math.max(0.4D, angelCenterY - headY + 0.35D);
        for (int index = 0; index < 5; index++) {
            float offset = (index - 2) * 0.18F;
            float pulse = 0.5F + 0.5F * Mth.sin(age * 0.18F + index * 1.37F);
            float width = 0.045F + (index % 2) * 0.035F;
            int alpha = Mth.clamp((int) ((18.0F + pulse * 35.0F) * visibility), 0, 65);
            renderBillboard(poseStack, vertices, camera, x + offset, headY + span * 0.5D,
                    z + index * 0.004D, width, span, 255, 231, 151, alpha);
        }
    }

    private static void renderMotes(PoseStack poseStack, VertexConsumer vertices, Camera camera,
            double x, double y, double z, float entityHeight, float age, float visibility, long seed) {
        for (int index = 0; index < 18; index++) {
            long mixed = seed + index * 0x9E3779B97F4A7C15L;
            double phase = ((mixed >>> 16) & 0xFFFFL) / 65535.0D * Mth.TWO_PI;
            double radius = 0.28D + ((mixed >>> 36) & 0xFFL) / 255.0D * 0.72D;
            double rise = Mth.frac(age * (0.014F + (index % 4) * 0.003F)
                    + (float) (((mixed >>> 8) & 0xFFL) / 255.0D));
            double angle = phase + age * (0.018D + (index % 3) * 0.004D);
            double moteX = x + Mth.cos((float) angle) * radius;
            double moteY = y + 0.12D + rise * (entityHeight + 2.3D);
            double moteZ = z + Mth.sin((float) angle) * radius;
            float twinkle = 0.5F + 0.5F * Mth.sin(age * 0.42F + index * 2.03F);
            float size = 0.025F + (index % 3) * 0.012F + twinkle * 0.012F;
            int alpha = Mth.clamp((int) ((75.0F + twinkle * 135.0F) * visibility), 0, 220);
            renderBillboard(poseStack, vertices, camera, moteX, moteY, moteZ,
                    size, size, 255, 224, 133, alpha);
        }
    }

    private static void renderGroundRings(PoseStack poseStack, VertexConsumer vertices,
            double x, double y, double z, float progress, float age, float visibility) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        float expansion = easeOutCubic(Mth.clamp(age / 29.0F, 0.0F, 1.0F));
        float outerRadius = 0.25F + expansion * 1.55F;
        int outerAlpha = Mth.clamp((int) ((1.0F - progress * 0.72F) * visibility * 170.0F), 0, 180);
        segmentedRing(poseStack, vertices, outerRadius, 0.10F, 28, 0.46F,
                255, 221, 126, outerAlpha, age * 0.006F);

        float prayerStrength = Mth.clamp((age - 17.0F) / 8.0F, 0.0F, 1.0F) * visibility;
        int innerAlpha = Mth.clamp((int) ((92.0F + Mth.sin(age * 0.22F) * 35.0F)
                * prayerStrength), 0, 150);
        segmentedRing(poseStack, vertices, 0.62F, 0.075F, 20, 0.55F,
                255, 239, 182, innerAlpha, -age * 0.004F);
        poseStack.popPose();
    }

    private static void segmentedRing(PoseStack poseStack, VertexConsumer vertices, float radius,
            float thickness, int segments, float fill, int red, int green, int blue,
            int alpha, float rotation) {
        if (alpha <= 0) return;
        float halfGap = (1.0F - fill) * Mth.TWO_PI / segments * 0.5F;
        for (int index = 0; index < segments; index++) {
            float center = rotation + Mth.TWO_PI * index / segments;
            float a0 = center - Mth.PI / segments + halfGap;
            float a1 = center + Mth.PI / segments - halfGap;
            float inner = radius - thickness * 0.5F;
            float outer = radius + thickness * 0.5F;
            ringVertex(poseStack, vertices, a0, inner, red, green, blue, alpha);
            ringVertex(poseStack, vertices, a0, outer, red, green, blue, alpha);
            ringVertex(poseStack, vertices, a1, outer, red, green, blue, alpha);
            ringVertex(poseStack, vertices, a1, inner, red, green, blue, alpha);
        }
    }

    private static void ringVertex(PoseStack poseStack, VertexConsumer vertices, float angle,
            float radius, int red, int green, int blue, int alpha) {
        vertices.addVertex(poseStack.last(), Mth.cos(angle) * radius, 0.0F, Mth.sin(angle) * radius)
                .setColor(red, green, blue, alpha);
    }

    private static void renderBillboard(PoseStack poseStack, VertexConsumer vertices, Camera camera,
            double x, double y, double z, float width, float height,
            int red, int green, int blue, int alpha) {
        if (alpha <= 0) return;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        Matrix4f pose = poseStack.last().pose();
        float halfWidth = width * 0.5F;
        float halfHeight = height * 0.5F;
        billboardVertex(vertices, pose, -halfWidth, -halfHeight, 0.0F, 1.0F, red, green, blue, alpha);
        billboardVertex(vertices, pose, halfWidth, -halfHeight, 1.0F, 1.0F, red, green, blue, alpha);
        billboardVertex(vertices, pose, halfWidth, halfHeight, 1.0F, 0.0F, red, green, blue, alpha);
        billboardVertex(vertices, pose, -halfWidth, halfHeight, 0.0F, 0.0F, red, green, blue, alpha);
        poseStack.popPose();
    }

    private static void billboardVertex(VertexConsumer vertices, Matrix4f pose,
            float x, float y, float u, float v, int red, int green, int blue, int alpha) {
        vertices.addVertex(pose, x, y, 0.0F)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private record ActiveSalvation(long startedAt, long endsAt, long seed) {
    }
}
