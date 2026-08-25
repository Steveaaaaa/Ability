package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class WeakPointMarkRenderer {
    private static final double TAU = Math.PI * 2.0D;
    private static final Map<MarkKey, MarkVisual> MARKS = new HashMap<>();
    private static final List<HitFlash> HIT_FLASHES = new ArrayList<>();
    private static final List<TriggerBurst> TRIGGER_BURSTS = new ArrayList<>();
    private static ClientLevel activeLevel;

    private WeakPointMarkRenderer() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(AbilityMod.id("weak_point"))) {
            return;
        }
        if (activeLevel != level) {
            clear(level);
        }
        MarkKey key = new MarkKey(cue.sourceEntityId(), cue.targetEntityId(), cue.instanceId());
        if (cue.cueId().equals(AbilityMod.id("marks"))) {
            if (cue.action() == AbilityCue.Action.STOP) {
                MARKS.remove(key);
            } else if (cue.action() == AbilityCue.Action.START) {
                long duration = cue.durationTicks() == AbilityCue.USE_DEFINITION_DURATION
                        ? AbilityCue.MAX_DURATION_TICKS
                        : cue.durationTicks();
                MARKS.put(key, new MarkVisual(
                        cue,
                        level.getGameTime() + Math.max(1L, duration)
                ));
            }
            return;
        }
        if (cue.action() != AbilityCue.Action.PULSE) {
            return;
        }
        if (cue.cueId().equals(AbilityMod.id("mark_hit"))) {
            HIT_FLASHES.add(new HitFlash(
                    cue.targetEntityId(),
                    localOffset(level, cue),
                    cue.position(),
                    cue.rank() / 255.0F,
                    level.getGameTime()
            ));
        } else if (cue.cueId().equals(AbilityMod.id("trigger"))) {
            MARKS.remove(key);
            TRIGGER_BURSTS.add(new TriggerBurst(
                    cue.targetEntityId(),
                    localOffset(level, cue),
                    cue.position(),
                    cue.randomSeed(),
                    level.getGameTime()
            ));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || activeLevel != level) {
            clear(level);
            return;
        }
        long gameTime = level.getGameTime();
        MARKS.entrySet().removeIf(entry -> gameTime >= entry.getValue().expiresAt()
                || missing(level, entry.getValue().cue().targetEntityId()));
        HIT_FLASHES.removeIf(flash -> gameTime - flash.startedAt() >= 8L);
        TRIGGER_BURSTS.removeIf(burst -> gameTime - burst.startedAt() >= 12L);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || MARKS.isEmpty() && HIT_FLASHES.isEmpty() && TRIGGER_BURSTS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != activeLevel) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double visualTime = level.getGameTime() + partialTick;
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());

        for (MarkVisual mark : MARKS.values()) {
            renderMarks(level, mark, visualTime, partialTick, camera, cameraPosition, poseStack, vertices);
        }
        for (HitFlash flash : HIT_FLASHES) {
            renderHitFlash(level, flash, visualTime, partialTick, camera, cameraPosition, poseStack, vertices);
        }
        for (TriggerBurst burst : TRIGGER_BURSTS) {
            renderTrigger(level, burst, visualTime, partialTick, camera, cameraPosition, poseStack, vertices);
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    private static void renderMarks(
            ClientLevel level,
            MarkVisual visual,
            double visualTime,
            float partialTick,
            Camera camera,
            Vec3 cameraPosition,
            PoseStack poseStack,
            VertexConsumer vertices
    ) {
        Entity target = level.getEntity(visual.cue().targetEntityId());
        if (target == null) {
            return;
        }
        float progress = Math.clamp(visual.cue().rank() / 255.0F, 0.0F, 1.0F);
        int count = Mth.clamp((int) Math.floor(progress * 5.0F + 0.01F), 1, 5);
        float warmth = smoothStep(Math.clamp((progress - 0.35F) / 0.65F, 0.0F, 1.0F));
        int red = Mth.lerpInt(warmth, 112, 255);
        int green = Mth.lerpInt(warmth, 16, 108);
        int blue = Mth.lerpInt(warmth, 24, 20);
        double radius = (0.35D + target.getBbWidth() * 0.52D) * (1.0D - progress * 0.27D);
        double speed = 0.25D + progress * 0.55D;
        double phase = seedPhase(visual.cue().randomSeed()) + visualTime * speed * TAU / 20.0D;
        double x = Mth.lerp(partialTick, target.xo, target.getX());
        double y = Mth.lerp(partialTick, target.yo, target.getY()) + target.getBbHeight() * 0.62D;
        double z = Mth.lerp(partialTick, target.zo, target.getZ());
        for (int index = 0; index < count; index++) {
            double angle = phase + index * TAU / count;
            Vec3 position = new Vec3(
                    x + Math.cos(angle) * radius,
                    y + Math.sin(angle * 1.7D) * 0.11D,
                    z + Math.sin(angle) * radius
            );
            renderPixelMark(
                    poseStack,
                    vertices,
                    camera,
                    position.subtract(cameraPosition),
                    (float) angle + 0.78F,
                    0.075F + progress * 0.025F,
                    0.22F + progress * 0.06F,
                    red,
                    green,
                    blue,
                    205
            );
        }
    }

    private static void renderHitFlash(
            ClientLevel level,
            HitFlash flash,
            double visualTime,
            float partialTick,
            Camera camera,
            Vec3 cameraPosition,
            PoseStack poseStack,
            VertexConsumer vertices
    ) {
        double age = visualTime - flash.startedAt();
        float progress = (float) Math.clamp(age / 8.0D, 0.0D, 1.0D);
        Vec3 position = anchoredPosition(level, flash.targetEntityId(), flash.localOffset(), flash.fallback(), partialTick);
        int alpha = Math.round(235.0F * (1.0F - progress));
        int green = Mth.lerpInt(flash.markProgress(), 18, 102);
        float length = 0.27F + progress * 0.18F;
        renderPixelMark(poseStack, vertices, camera, position.subtract(cameraPosition), 0.72F,
                0.055F, length, 230, green, 25, alpha);
        renderPixelMark(poseStack, vertices, camera, position.subtract(cameraPosition), -0.72F,
                0.035F, length * 0.72F, 255, 78, 30, alpha);
    }

    private static void renderTrigger(
            ClientLevel level,
            TriggerBurst burst,
            double visualTime,
            float partialTick,
            Camera camera,
            Vec3 cameraPosition,
            PoseStack poseStack,
            VertexConsumer vertices
    ) {
        double age = visualTime - burst.startedAt();
        float progress = (float) Math.clamp(age / 12.0D, 0.0D, 1.0D);
        Vec3 impact = anchoredPosition(level, burst.targetEntityId(), burst.localOffset(), burst.fallback(), partialTick);
        if (progress < 0.58F) {
            float collapse = progress / 0.58F;
            double radius = (1.0D - smoothStep(collapse)) * 0.72D;
            for (int index = 0; index < 5; index++) {
                double angle = seedPhase(burst.seed()) + index * TAU / 5.0D + age * 0.16D;
                Vec3 shard = impact.add(
                        Math.cos(angle) * radius,
                        Math.sin(angle * 1.4D) * radius * 0.38D,
                        Math.sin(angle) * radius
                );
                renderPixelMark(poseStack, vertices, camera, shard.subtract(cameraPosition), (float) angle,
                        0.07F, 0.27F, 255, 82, 24, 230);
            }
            return;
        }
        float burstProgress = (progress - 0.58F) / 0.42F;
        int alpha = Math.round(255.0F * (1.0F - burstProgress));
        float length = 0.28F + burstProgress * 0.65F;
        renderPixelMark(poseStack, vertices, camera, impact.subtract(cameraPosition), 0.78F,
                0.07F, length, 255, 45, 30, alpha);
        renderPixelMark(poseStack, vertices, camera, impact.subtract(cameraPosition), -0.78F,
                0.07F, length, 255, 122, 32, alpha);
        renderPixelMark(poseStack, vertices, camera, impact.subtract(cameraPosition), 0.0F,
                0.10F, 0.18F + burstProgress * 0.18F, 255, 245, 220, alpha);
    }

    private static void renderPixelMark(
            PoseStack poseStack,
            VertexConsumer vertices,
            Camera camera,
            Vec3 relativePosition,
            float rotation,
            float halfWidth,
            float halfHeight,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        if (alpha <= 0) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(relativePosition.x, relativePosition.y, relativePosition.z);
        poseStack.mulPose(camera.rotation());
        float rotationStep = (float) (Math.PI / 4.0D);
        poseStack.mulPose(Axis.ZP.rotation(Math.round(rotation / rotationStep) * rotationStep));
        Matrix4f pose = poseStack.last().pose();
        float pixelSize = Math.max(0.018F, Math.min(halfWidth * 0.9F, halfHeight / 5.0F));
        int steps = Mth.clamp(Math.round(halfHeight * 2.0F / pixelSize), 5, 31);
        if ((steps & 1) == 0) {
            steps++;
        }
        float cellSize = pixelSize * 0.82F;
        float halfCell = cellSize * 0.5F;
        int center = steps / 2;
        for (int row = 0; row < steps; row++) {
            int pattern = Math.floorMod(row, 8);
            int xStep = pattern <= 1 ? -1 : pattern <= 4 ? 0 : pattern <= 5 ? 1 : 0;
            float x = xStep * pixelSize * 0.62F;
            float y = (row - center) * pixelSize;
            float shade = pattern == 0 || pattern == 5 ? 0.72F : pattern == 2 || pattern == 7 ? 0.88F : 1.0F;
            int pixelRed = Math.round(red * shade);
            int pixelGreen = Math.round(green * shade);
            int pixelBlue = Math.round(blue * shade);
            vertices.addVertex(pose, x - halfCell, y + halfCell, 0.0F)
                    .setColor(pixelRed, pixelGreen, pixelBlue, alpha);
            vertices.addVertex(pose, x + halfCell, y + halfCell, 0.0F)
                    .setColor(pixelRed, pixelGreen, pixelBlue, alpha);
            vertices.addVertex(pose, x + halfCell, y - halfCell, 0.0F)
                    .setColor(pixelRed, pixelGreen, pixelBlue, alpha);
            vertices.addVertex(pose, x - halfCell, y - halfCell, 0.0F)
                    .setColor(pixelRed, pixelGreen, pixelBlue, alpha);
        }
        poseStack.popPose();
    }

    private static Vec3 localOffset(ClientLevel level, AbilityCue cue) {
        Entity target = level.getEntity(cue.targetEntityId());
        return target == null ? Vec3.ZERO : cue.position().subtract(target.position());
    }

    private static Vec3 anchoredPosition(
            ClientLevel level,
            int targetEntityId,
            Vec3 localOffset,
            Vec3 fallback,
            float partialTick
    ) {
        Entity target = level.getEntity(targetEntityId);
        if (target == null) {
            return fallback;
        }
        return new Vec3(
                Mth.lerp(partialTick, target.xo, target.getX()),
                Mth.lerp(partialTick, target.yo, target.getY()),
                Mth.lerp(partialTick, target.zo, target.getZ())
        ).add(localOffset);
    }

    private static boolean missing(ClientLevel level, int entityId) {
        Entity entity = level.getEntity(entityId);
        return entity == null || entity.isRemoved();
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static double seedPhase(long seed) {
        return Math.floorMod(seed, 65_536L) / 65_536.0D * TAU;
    }

    private static void clear(ClientLevel level) {
        MARKS.clear();
        HIT_FLASHES.clear();
        TRIGGER_BURSTS.clear();
        activeLevel = level;
    }

    private record MarkKey(int sourceEntityId, int targetEntityId, long instanceId) {
    }

    private record MarkVisual(AbilityCue cue, long expiresAt) {
    }

    private record HitFlash(
            int targetEntityId,
            Vec3 localOffset,
            Vec3 fallback,
            float markProgress,
            long startedAt
    ) {
    }

    private record TriggerBurst(
            int targetEntityId,
            Vec3 localOffset,
            Vec3 fallback,
            long seed,
            long startedAt
    ) {
    }
}
