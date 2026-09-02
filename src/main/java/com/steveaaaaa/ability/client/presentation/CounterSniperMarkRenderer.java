package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class CounterSniperMarkRenderer {
    private static final net.minecraft.resources.ResourceLocation ABILITY = AbilityMod.id("counter_sniper");
    private static final net.minecraft.resources.ResourceLocation TARGET_MARK = AbilityMod.id("target_mark");
    private static final RenderType SEE_THROUGH_MARKER = RenderType.create(
            "ability_counter_sniper_marker",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false)
    );
    private static final Map<Integer, Mark> MARKS = new HashMap<>();
    private static ClientLevel activeLevel;

    private CounterSniperMarkRenderer() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(ABILITY) || !cue.cueId().equals(TARGET_MARK)) {
            return;
        }
        if (activeLevel != level) {
            clear(level);
        }
        if (cue.action() == AbilityCue.Action.STOP) {
            MARKS.remove(cue.targetEntityId());
        } else if (cue.action() == AbilityCue.Action.START) {
            MARKS.put(cue.targetEntityId(), new Mark(level.getGameTime(), cue.randomSeed()));
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || MARKS.isEmpty()) {
            return;
        }
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
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(SEE_THROUGH_MARKER);
        MARKS.entrySet().removeIf(entry -> {
            Entity target = level.getEntity(entry.getKey());
            if (target == null || target.isRemoved()) {
                return true;
            }
            renderMark(event.getPoseStack(), vertices, camera, cameraPosition, target,
                    entry.getValue(), visualTime, partialTick);
            return false;
        });
        buffers.endBatch(SEE_THROUGH_MARKER);
    }

    private static void renderMark(PoseStack poseStack, VertexConsumer vertices, Camera camera,
            Vec3 cameraPosition, Entity target, Mark mark, double visualTime, float partialTick) {
        double x = Mth.lerp(partialTick, target.xo, target.getX());
        double y = Mth.lerp(partialTick, target.yo, target.getY()) + target.getBbHeight() * 0.62D;
        double z = Mth.lerp(partialTick, target.zo, target.getZ());
        double distance = cameraPosition.distanceTo(new Vec3(x, y, z));
        float size = Mth.clamp(0.42F + (float) distance * 0.006F, 0.42F, 0.9F);
        float appear = Mth.clamp((float) (visualTime - mark.startedAt()) / 5.0F, 0.0F, 1.0F);
        float pulse = 0.94F + Mth.sin((float) (visualTime * 0.18D + (mark.seed() & 31L))) * 0.06F;

        poseStack.pushPose();
        poseStack.translate(x - cameraPosition.x, y - cameraPosition.y, z - cameraPosition.z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(size * appear * pulse, size * appear * pulse, size * appear * pulse);
        Matrix4f pose = poseStack.last().pose();

        int darkAlpha = 168;
        int goldAlpha = 238;
        corner(vertices, pose, -0.50F, 0.50F, 1.0F, 0.0F, 0.0F, -1.0F, darkAlpha, goldAlpha);
        corner(vertices, pose, 0.50F, 0.50F, -1.0F, 0.0F, 0.0F, -1.0F, darkAlpha, goldAlpha);
        corner(vertices, pose, -0.50F, -0.50F, 1.0F, 0.0F, 0.0F, 1.0F, darkAlpha, goldAlpha);
        corner(vertices, pose, 0.50F, -0.50F, -1.0F, 0.0F, 0.0F, 1.0F, darkAlpha, goldAlpha);
        rect(vertices, pose, -0.055F, -0.055F, 0.055F, 0.055F, 255, 205, 91, 220);
        rect(vertices, pose, -0.018F, -0.018F, 0.018F, 0.018F, 255, 246, 199, 255);
        rect(vertices, pose, -0.035F, 0.60F, 0.035F, 0.73F, 174, 43, 35, 210);
        poseStack.popPose();
    }

    private static void corner(VertexConsumer vertices, Matrix4f pose, float x, float y,
            float horizontalDirection, float unusedHorizontal, float unusedVertical, float verticalDirection,
            int darkAlpha, int goldAlpha) {
        float thickness = 0.075F;
        float length = 0.28F;
        rect(vertices, pose,
                Math.min(x, x + horizontalDirection * length), y - thickness * 0.5F,
                Math.max(x, x + horizontalDirection * length), y + thickness * 0.5F,
                77, 22, 18, darkAlpha);
        rect(vertices, pose,
                x - thickness * 0.5F, Math.min(y, y + verticalDirection * length),
                x + thickness * 0.5F, Math.max(y, y + verticalDirection * length),
                77, 22, 18, darkAlpha);
        float inset = 0.018F;
        float brightThickness = 0.035F;
        rect(vertices, pose,
                Math.min(x + horizontalDirection * inset, x + horizontalDirection * (length - inset)),
                y - brightThickness * 0.5F,
                Math.max(x + horizontalDirection * inset, x + horizontalDirection * (length - inset)),
                y + brightThickness * 0.5F,
                230, 157, 50, goldAlpha);
        rect(vertices, pose,
                x - brightThickness * 0.5F,
                Math.min(y + verticalDirection * inset, y + verticalDirection * (length - inset)),
                x + brightThickness * 0.5F,
                Math.max(y + verticalDirection * inset, y + verticalDirection * (length - inset)),
                230, 157, 50, goldAlpha);
    }

    private static void rect(VertexConsumer vertices, Matrix4f pose,
            float x0, float y0, float x1, float y1, int red, int green, int blue, int alpha) {
        vertices.addVertex(pose, x0, y0, 0.0F).setColor(red, green, blue, alpha);
        vertices.addVertex(pose, x1, y0, 0.0F).setColor(red, green, blue, alpha);
        vertices.addVertex(pose, x1, y1, 0.0F).setColor(red, green, blue, alpha);
        vertices.addVertex(pose, x0, y1, 0.0F).setColor(red, green, blue, alpha);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear(null);
    }

    private static void clear(ClientLevel level) {
        MARKS.clear();
        activeLevel = level;
    }

    private record Mark(long startedAt, long seed) {
    }
}
