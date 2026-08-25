package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ChargedLeapImpactRenderer {
    private static final double IMPACT_RADIUS = 5.0D;
    private static final int DURATION_TICKS = 14;
    private static final int WAVE_TICKS = 7;
    private static final int MAX_HEIGHT_DIFFERENCE = 1;
    private static final int MAX_SURFACE_BLOCKS = 96;
    private static final double MAX_RENDER_DISTANCE_SQR = 48.0D * 48.0D;
    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static ClientLevel activeLevel;

    private ChargedLeapImpactRenderer() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(AbilityMod.id("charged_leap"))
                || !cue.cueId().equals(AbilityMod.id("impact"))
                || cue.action() != AbilityCue.Action.PULSE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.player.position().distanceToSqr(cue.position()) > MAX_RENDER_DISTANCE_SQR) {
            return;
        }
        if (activeLevel != level) {
            IMPACTS.clear();
            activeLevel = level;
        }
        BlockPos center = BlockPos.containing(cue.position());
        BlockPos reference = findReferenceSurface(level, center);
        if (reference == null) {
            return;
        }
        List<SurfaceBlock> blocks = collectSurfaceBlocks(level, reference);
        if (!blocks.isEmpty()) {
            IMPACTS.add(new Impact(level.getGameTime(), reference, blocks));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || activeLevel != level) {
            IMPACTS.clear();
            activeLevel = level;
            return;
        }
        long gameTime = level.getGameTime();
        IMPACTS.removeIf(impact -> gameTime - impact.startedAt() >= DURATION_TICKS);
        for (Impact impact : IMPACTS) {
            int age = (int) (gameTime - impact.startedAt());
            if (age >= 0 && age < WAVE_TICKS) {
                emitWave(level, impact, age);
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || IMPACTS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != activeLevel) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean rendered = false;
        for (Impact impact : IMPACTS) {
            double age = level.getGameTime() - impact.startedAt() + partialTick;
            for (SurfaceBlock surface : impact.blocks()) {
                double lift = liftHeight(age, surface.delayTicks());
                if (lift <= 0.001D) {
                    continue;
                }
                BlockPos pos = surface.position();
                poseStack.pushPose();
                poseStack.translate(
                        pos.getX() - cameraPosition.x,
                        pos.getY() - cameraPosition.y + lift,
                        pos.getZ() - cameraPosition.z
                );
                minecraft.getBlockRenderer().renderSingleBlock(
                        surface.state(),
                        poseStack,
                        buffers,
                        LevelRenderer.getLightColor(level, pos),
                        OverlayTexture.NO_OVERLAY
                );
                poseStack.popPose();
                rendered = true;
            }
        }
        if (rendered) {
            buffers.endBatch();
        }
    }

    private static BlockPos findReferenceSurface(ClientLevel level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = center.getY() + 1; y >= center.getY() - 12; y--) {
            cursor.set(center.getX(), y, center.getZ());
            if (isExposedSurface(level, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    private static List<SurfaceBlock> collectSurfaceBlocks(ClientLevel level, BlockPos reference) {
        ArrayList<SurfaceBlock> result = new ArrayList<>();
        int radius = Mth.ceil(IMPACT_RADIUS);
        for (int xOffset = -radius; xOffset <= radius; xOffset++) {
            for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                double distance = Math.sqrt(xOffset * xOffset + zOffset * zOffset);
                if (distance > IMPACT_RADIUS || result.size() >= MAX_SURFACE_BLOCKS) {
                    continue;
                }
                BlockPos surface = findNearbySurface(
                        level,
                        reference.getX() + xOffset,
                        reference.getZ() + zOffset,
                        reference.getY()
                );
                if (surface == null) {
                    continue;
                }
                result.add(new SurfaceBlock(
                        surface,
                        level.getBlockState(surface),
                        distance / IMPACT_RADIUS * 3.0D
                ));
            }
        }
        return List.copyOf(result);
    }

    private static BlockPos findNearbySurface(ClientLevel level, int x, int z, int referenceY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = referenceY + MAX_HEIGHT_DIFFERENCE; y >= referenceY - MAX_HEIGHT_DIFFERENCE; y--) {
            cursor.set(x, y, z);
            if (level.hasChunkAt(cursor) && isExposedSurface(level, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    private static boolean isExposedSurface(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getRenderShape() != RenderShape.MODEL
                || state.getCollisionShape(level, pos).isEmpty()
                || !state.getFluidState().isEmpty()
                || state.hasBlockEntity()
                || state.is(BlockTags.LEAVES)) {
            return false;
        }
        BlockPos above = pos.above();
        return level.getBlockState(above).getCollisionShape(level, above).isEmpty();
    }

    private static double liftHeight(double age, double delayTicks) {
        double progress = (age - delayTicks) / (DURATION_TICKS - 3.0D);
        if (progress <= 0.0D || progress >= 1.0D) {
            return 0.0D;
        }
        return Math.sin(progress * Math.PI) * 0.38D;
    }

    private static void emitWave(ClientLevel level, Impact impact, int age) {
        double radius = IMPACT_RADIUS * (age + 1.0D) / WAVE_TICKS;
        int count = 18;
        double y = impact.reference().getY() + 1.12D;
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0D * index / count + age * 0.11D;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            level.addParticle(
                    ParticleTypes.CLOUD,
                    impact.reference().getX() + 0.5D + cos * radius,
                    y,
                    impact.reference().getZ() + 0.5D + sin * radius,
                    cos * 0.075D,
                    0.015D,
                    sin * 0.075D
            );
        }
    }

    private record SurfaceBlock(BlockPos position, BlockState state, double delayTicks) {
    }

    private record Impact(long startedAt, BlockPos reference, List<SurfaceBlock> blocks) {
    }
}
