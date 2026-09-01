package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steveaaaaa.ability.AbilityMod;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class CrushingBlowGroundRenderer {
    private static final double RADIUS = 7.5D;
    private static final int WAVE_TICKS = 20;
    private static final int DURATION_TICKS = 36;
    private static final int MAX_HEIGHT_DIFFERENCE = 1;
    private static final int MAX_SURFACE_BLOCKS = 196;
    private static final double MAX_RENDER_DISTANCE_SQR = 56.0D * 56.0D;
    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static ClientLevel activeLevel;

    private CrushingBlowGroundRenderer() {
    }

    public static void accept(ClientLevel level, double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 origin = new Vec3(x, y, z);
        if (minecraft.player == null || minecraft.player.position().distanceToSqr(origin) > MAX_RENDER_DISTANCE_SQR) return;
        if (activeLevel != level) {
            IMPACTS.clear();
            activeLevel = level;
        }
        BlockPos reference = findReferenceSurface(level, BlockPos.containing(origin));
        if (reference == null) return;
        List<SurfaceBlock> blocks = collectSurfaceBlocks(level, reference);
        if (!blocks.isEmpty()) IMPACTS.add(new Impact(level.getGameTime(), reference, blocks));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != activeLevel) {
            IMPACTS.clear();
            activeLevel = level;
            return;
        }
        long gameTime = level.getGameTime();
        IMPACTS.removeIf(impact -> gameTime - impact.startedAt() >= DURATION_TICKS);
        for (Impact impact : IMPACTS) {
            int age = (int) (gameTime - impact.startedAt());
            if (age >= 0 && age < WAVE_TICKS) emitWave(level, impact, age);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || IMPACTS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != activeLevel) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        Set<RenderType> usedRenderTypes = new HashSet<>();
        for (Impact impact : IMPACTS) {
            double age = level.getGameTime() - impact.startedAt() + partialTick;
            for (SurfaceBlock surface : impact.blocks()) {
                float strength = deformationStrength(age, surface.delayTicks());
                if (strength <= 0.001F || level.getBlockState(surface.position()) != surface.state()) continue;
                BlockPos pos = surface.position();
                float normalized = (float) Mth.clamp(surface.distance() / RADIUS, 0.0D, 1.0D);
                float tilt = (float) Math.toRadians(5.0F + (1.0F - normalized) * 14.0F) * strength;
                float tangentX = (float) -surface.radialZ();
                float tangentZ = (float) surface.radialX();
                double bowlHeight = (0.025D + normalized * 0.1D) * strength;
                double inward = 0.075D * strength;

                poseStack.pushPose();
                poseStack.translate(pos.getX() - cameraPosition.x + surface.radialX() * -inward,
                        pos.getY() - cameraPosition.y + bowlHeight,
                        pos.getZ() - cameraPosition.z + surface.radialZ() * -inward);
                poseStack.translate(0.5D, 0.08D, 0.5D);
                if (surface.distance() > 0.2D) {
                    poseStack.mulPose(new Quaternionf().rotationAxis(tilt, tangentX, 0.0F, tangentZ));
                }
                poseStack.scale(0.9F, 0.96F, 0.9F);
                poseStack.translate(-0.5D, -0.08D, -0.5D);
                renderWorldBlock(dispatcher, level, surface.state(), pos, poseStack, buffers, usedRenderTypes);
                poseStack.popPose();
            }
        }
        usedRenderTypes.forEach(buffers::endBatch);
    }

    private static void renderWorldBlock(BlockRenderDispatcher dispatcher, ClientLevel level, BlockState state,
            BlockPos pos, PoseStack poseStack, MultiBufferSource.BufferSource buffers, Set<RenderType> usedRenderTypes) {
        BakedModel model = dispatcher.getBlockModel(state);
        ModelData modelData = model.getModelData(level, pos, state, ModelData.EMPTY);
        long seed = state.getSeed(pos);
        for (RenderType source : model.getRenderTypes(state, RandomSource.create(seed), modelData)) {
            RenderType moving = RenderTypeHelper.getMovingBlockRenderType(source);
            dispatcher.getModelRenderer().tesselateBlock(level, model, state, pos, poseStack,
                    buffers.getBuffer(moving), false, RandomSource.create(seed), seed,
                    OverlayTexture.NO_OVERLAY, modelData, source);
            usedRenderTypes.add(moving);
        }
    }

    private static BlockPos findReferenceSurface(ClientLevel level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = center.getY() + 1; y >= center.getY() - 12; y--) {
            cursor.set(center.getX(), y, center.getZ());
            if (isExposedSurface(level, cursor)) return cursor.immutable();
        }
        return null;
    }

    private static List<SurfaceBlock> collectSurfaceBlocks(ClientLevel level, BlockPos reference) {
        ArrayList<SurfaceBlock> result = new ArrayList<>();
        int radius = Mth.ceil(RADIUS);
        for (int xOffset = -radius; xOffset <= radius; xOffset++) {
            for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                double distance = Math.sqrt(xOffset * xOffset + zOffset * zOffset);
                if (distance > RADIUS || result.size() >= MAX_SURFACE_BLOCKS) continue;
                BlockPos surface = findNearbySurface(level, reference.getX() + xOffset,
                        reference.getZ() + zOffset, reference.getY());
                if (surface == null) continue;
                double radialX = distance < 0.01D ? 0.0D : xOffset / distance;
                double radialZ = distance < 0.01D ? 0.0D : zOffset / distance;
                result.add(new SurfaceBlock(surface, level.getBlockState(surface), distance,
                        distance / RADIUS * WAVE_TICKS, radialX, radialZ));
            }
        }
        return List.copyOf(result);
    }

    private static BlockPos findNearbySurface(ClientLevel level, int x, int z, int referenceY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = referenceY + MAX_HEIGHT_DIFFERENCE; y >= referenceY - MAX_HEIGHT_DIFFERENCE; y--) {
            cursor.set(x, y, z);
            if (level.hasChunkAt(cursor) && isExposedSurface(level, cursor)) return cursor.immutable();
        }
        return null;
    }

    private static boolean isExposedSurface(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getRenderShape() != RenderShape.MODEL || state.getCollisionShape(level, pos).isEmpty()
                || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.is(BlockTags.LEAVES)) return false;
        BlockPos above = pos.above();
        return level.getBlockState(above).getCollisionShape(level, above).isEmpty();
    }

    private static float deformationStrength(double age, double delayTicks) {
        double localAge = age - delayTicks;
        if (localAge <= 0.0D || age >= DURATION_TICKS) return 0.0F;
        float appear = smoothStep((float) Mth.clamp(localAge / 3.0D, 0.0D, 1.0D));
        float recover = age <= 28.0D ? 1.0F
                : 1.0F - smoothStep((float) Mth.clamp((age - 28.0D) / 8.0D, 0.0D, 1.0D));
        return appear * recover;
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static void emitWave(ClientLevel level, Impact impact, int age) {
        double radius = RADIUS * (age + 1.0D) / WAVE_TICKS;
        int count = 28;
        double y = impact.reference().getY() + 1.08D;
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0D * index / count + age * 0.045D;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double x = impact.reference().getX() + 0.5D + cos * radius;
            double z = impact.reference().getZ() + 0.5D + sin * radius;
            level.addParticle(ParticleTypes.CLOUD, x, y, z, cos * 0.09D, 0.012D, sin * 0.09D);
            if ((index + age) % 4 == 0) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,
                                level.getBlockState(BlockPos.containing(x, y - 0.7D, z))),
                        x, y - 0.58D, z, cos * 0.035D, 0.045D, sin * 0.035D);
            }
        }
    }

    private record SurfaceBlock(BlockPos position, BlockState state, double distance,
            double delayTicks, double radialX, double radialZ) {
    }

    private record Impact(long startedAt, BlockPos reference, List<SurfaceBlock> blocks) {
    }
}
