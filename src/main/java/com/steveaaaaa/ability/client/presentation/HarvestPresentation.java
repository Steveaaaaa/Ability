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
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class HarvestPresentation {
    private static final ResourceLocation SWEEP = AbilityMod.id("sweep");
    private static final ResourceLocation SLASH_TEXTURE = AbilityMod.id("textures/particle/harvest_slash.png");
    private static final RenderType SLASH_TYPE = RenderType.entityTranslucent(SLASH_TEXTURE);
    private static final int DURATION_TICKS = 12;
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
        if (SLASHES.size() >= 32) SLASHES.removeFirst();
        SLASHES.add(new Slash(cue.position(), forward, level.getGameTime(), intensity));
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
        VertexConsumer vertices = buffers.getBuffer(SLASH_TYPE);
        Iterator<Slash> iterator = SLASHES.iterator();
        while (iterator.hasNext()) {
            Slash slash = iterator.next();
            float age = (float) (visualTime - slash.startedAt());
            if (age >= DURATION_TICKS) {
                iterator.remove();
                continue;
            }
            renderSlash(level, poseStack, vertices, cameraPosition, slash, age);
        }
        buffers.endBatch(SLASH_TYPE);
    }

    private static void renderSlash(ClientLevel level, PoseStack poseStack, VertexConsumer vertices,
            Vec3 cameraPosition, Slash slash, float age) {
        float progress = Mth.clamp(age / DURATION_TICKS, 0.0F, 1.0F);
        float grow = 1.0F - (1.0F - Math.min(1.0F, progress / 0.24F))
                * (1.0F - Math.min(1.0F, progress / 0.24F));
        float alphaEnvelope = Math.min(1.0F, progress / 0.10F)
                * Mth.clamp((1.0F - progress) / 0.62F, 0.0F, 1.0F);
        int alpha = Mth.clamp((int) (alphaEnvelope * (190.0F + slash.intensity() * 65.0F)), 0, 255);
        float size = (1.05F + slash.intensity() * 0.72F) * (0.74F + grow * 0.26F);

        Vec3 forward = slash.forward();
        Vec3 baseRight = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        Vec3 baseUp = new Vec3(0.0D, 1.0D, 0.0D);
        float angle = (float) Math.toRadians(-22.0F + progress * 38.0F);
        Vec3 right = baseRight.scale(Mth.cos(angle)).add(baseUp.scale(Mth.sin(angle)));
        Vec3 up = baseUp.scale(Mth.cos(angle)).subtract(baseRight.scale(Mth.sin(angle)));
        Vec3 center = slash.position().subtract(cameraPosition);
        Vec3 halfRight = right.scale(size * 0.5F);
        Vec3 halfUp = up.scale(size * 0.5F);
        int packedLight = LevelRenderer.getLightColor(level, BlockPos.containing(slash.position()));

        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        vertex(poseStack, vertices, halfRight.reverse().subtract(halfUp), 0.0F, 1.0F, alpha, packedLight, forward);
        vertex(poseStack, vertices, halfRight.subtract(halfUp), 1.0F, 1.0F, alpha, packedLight, forward);
        vertex(poseStack, vertices, halfRight.add(halfUp), 1.0F, 0.0F, alpha, packedLight, forward);
        vertex(poseStack, vertices, halfRight.reverse().add(halfUp), 0.0F, 0.0F, alpha, packedLight, forward);
        poseStack.popPose();
    }

    private static void vertex(PoseStack poseStack, VertexConsumer vertices, Vec3 position,
            float u, float v, int alpha, int packedLight, Vec3 normal) {
        vertices.addVertex(poseStack.last(), (float) position.x, (float) position.y, (float) position.z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(poseStack.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void clear(ClientLevel level) {
        SLASHES.clear();
        activeLevel = level;
    }

    private record Slash(Vec3 position, Vec3 forward, long startedAt, float intensity) {
    }
}
