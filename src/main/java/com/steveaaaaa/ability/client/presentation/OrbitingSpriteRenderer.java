package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
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
import org.joml.Matrix4f;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class OrbitingSpriteRenderer {
    private static final double TAU = Math.PI * 2.0D;

    private OrbitingSpriteRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double visualTime = level.getGameTime() + partialTick;
        Camera camera = event.getCamera();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Set<RenderType> usedRenderTypes = new HashSet<>();

        for (ClientAbilityPresentationManager.ActivePresentation active
                : ClientAbilityPresentationManager.activePresentations()) {
            for (AbilityPresentationDefinition.OrbitingSprite definition : active.orbitingSprites()) {
                int entityId = definition.anchor() == AbilityPresentationDefinition.Anchor.SOURCE
                        ? active.cue().sourceEntityId()
                        : active.cue().targetEntityId();
                Entity entity = level.getEntity(entityId);
                if (entity == null || entity.isRemoved()) {
                    continue;
                }
                renderOrbit(
                        poseStack, buffers, usedRenderTypes, level, camera, entity,
                        definition, active, visualTime, partialTick
                );
            }
        }
        usedRenderTypes.forEach(buffers::endBatch);
    }

    private static void renderOrbit(
            PoseStack poseStack,
            MultiBufferSource.BufferSource buffers,
            Set<RenderType> usedRenderTypes,
            ClientLevel level,
            Camera camera,
            Entity entity,
            AbilityPresentationDefinition.OrbitingSprite definition,
            ClientAbilityPresentationManager.ActivePresentation active,
            double visualTime,
            float partialTick
    ) {
        float scale = fadeScale(visualTime, active.startedAt(), active.expiresAt(), definition.fadeTicks());
        if (scale <= 0.0F) {
            return;
        }

        int count = spriteCount(entity.getBbWidth(), definition);
        double radius = orbitRadius(entity.getBbWidth(), definition);
        double entityX = Mth.lerp(partialTick, entity.xo, entity.getX());
        double entityY = Mth.lerp(partialTick, entity.yo, entity.getY());
        double entityZ = Mth.lerp(partialTick, entity.zo, entity.getZ());
        double phaseOffset = Math.floorMod(active.cue().randomSeed(), 65_536L) / 65_536.0D * TAU;
        double phase = phaseOffset + visualTime * definition.rotationsPerSecond() * TAU / 20.0D;
        Vec3 cameraPosition = camera.getPosition();
        RenderType renderType = RenderType.entityTranslucent(definition.texture());
        VertexConsumer vertices = buffers.getBuffer(renderType);
        usedRenderTypes.add(renderType);

        for (int index = 0; index < count; index++) {
            double angle = phase + (double) index * TAU / count;
            double bob = Math.sin(
                    visualTime * definition.bobCyclesPerSecond() * TAU / 20.0D + (double) index * TAU / count
            ) * definition.bobAmplitude();
            double x = entityX + Math.cos(angle) * radius;
            double y = entityY + entity.getBbHeight() + definition.heightOffset() + bob;
            double z = entityZ + Math.sin(angle) * radius;
            int light = definition.fullBright()
                    ? LightTexture.FULL_BRIGHT
                    : LevelRenderer.getLightColor(level, entity.blockPosition());
            renderBillboard(
                    poseStack,
                    vertices,
                    camera,
                    x - cameraPosition.x,
                    y - cameraPosition.y,
                    z - cameraPosition.z,
                    definition.size() * scale,
                    Math.round(255.0F * scale),
                    light
            );
        }
    }

    private static void renderBillboard(
            PoseStack poseStack,
            VertexConsumer vertices,
            Camera camera,
            double x,
            double y,
            double z,
            float size,
            int alpha,
            int packedLight
    ) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(size, size, size);
        Matrix4f pose = poseStack.last().pose();
        vertex(vertices, pose, -0.5F, -0.5F, 0.0F, 1.0F, alpha, packedLight);
        vertex(vertices, pose, 0.5F, -0.5F, 1.0F, 1.0F, alpha, packedLight);
        vertex(vertices, pose, 0.5F, 0.5F, 1.0F, 0.0F, alpha, packedLight);
        vertex(vertices, pose, -0.5F, 0.5F, 0.0F, 0.0F, alpha, packedLight);
        poseStack.popPose();
    }

    private static void vertex(
            VertexConsumer vertices,
            Matrix4f pose,
            float x,
            float y,
            float u,
            float v,
            int alpha,
            int packedLight
    ) {
        vertices.addVertex(pose, x, y, 0.0F)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    static float fadeScale(double visualTime, long startedAt, long expiresAt, int fadeTicks) {
        if (fadeTicks <= 0) {
            return visualTime < expiresAt ? 1.0F : 0.0F;
        }
        double duration = Math.max(0.0D, expiresAt - startedAt);
        double effectiveFadeTicks = Math.min(fadeTicks, Math.max(0.5D, duration / 3.0D));
        double fadeIn = Math.clamp((visualTime - startedAt) / effectiveFadeTicks, 0.0D, 1.0D);
        double fadeOut = Math.clamp((expiresAt - visualTime) / effectiveFadeTicks, 0.0D, 1.0D);
        return (float) Math.min(fadeIn, fadeOut);
    }

    static int spriteCount(float entityWidth, AbilityPresentationDefinition.OrbitingSprite definition) {
        return Mth.clamp(
                Math.round(definition.countBias() + entityWidth * definition.countWidthMultiplier()),
                definition.minimumCount(),
                definition.maximumCount()
        );
    }

    static double orbitRadius(float entityWidth, AbilityPresentationDefinition.OrbitingSprite definition) {
        return Mth.clamp(
                definition.radiusBase() + entityWidth * definition.radiusWidthMultiplier(),
                definition.minimumRadius(),
                definition.maximumRadius()
        );
    }
}
