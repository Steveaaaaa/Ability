package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientTransmutationQueue;
import com.steveaaaaa.ability.network.ClientboundTransmutationPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ChorusTransmutationRenderer {
    private static final int DURATION_TICKS = 22;
    private static final double MAX_DISTANCE_SQR = 64.0D * 64.0D;
    private static final List<Transmutation> ACTIVE = new ArrayList<>();
    private static ClientLevel activeLevel;

    private ChorusTransmutationRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }
        if (activeLevel != level) {
            ACTIVE.clear();
            activeLevel = level;
        }
        ClientboundTransmutationPayload payload;
        while ((payload = ClientTransmutationQueue.poll()) != null) {
            Block input = BuiltInRegistries.BLOCK.getOptional(payload.inputBlock()).orElse(null);
            Block output = BuiltInRegistries.BLOCK.getOptional(payload.outputBlock()).orElse(null);
            if (input == null || output == null || minecraft.player == null
                    || minecraft.player.distanceToSqr(payload.position().getCenter()) > MAX_DISTANCE_SQR) {
                continue;
            }
            if (ACTIVE.size() >= 16) ACTIVE.remove(0);
            ACTIVE.add(new Transmutation(
                    level.getGameTime(),
                    payload.position(),
                    input.defaultBlockState(),
                    output.defaultBlockState(),
                    payload.advanced(),
                    payload.rank(),
                    payload.randomSeed()
            ));
        }
        long gameTime = level.getGameTime();
        ACTIVE.removeIf(active -> gameTime - active.startedAt() >= DURATION_TICKS);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ACTIVE.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != activeLevel) return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double visualTime = level.getGameTime() + partialTick;
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        Set<RenderType> usedRenderTypes = new HashSet<>();

        for (Transmutation transmutation : ACTIVE) {
            float progress = progress(visualTime, transmutation.startedAt());
            renderFragments(poseStack, buffers, dispatcher, usedRenderTypes, level,
                    cameraPosition, transmutation, progress);
        }
        usedRenderTypes.forEach(buffers::endBatch);

        VertexConsumer pixels = buffers.getBuffer(RenderType.lightning());
        for (Transmutation transmutation : ACTIVE) {
            float progress = progress(visualTime, transmutation.startedAt());
            renderPixelMagic(poseStack, pixels, cameraPosition, transmutation, progress);
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static void renderFragments(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
            BlockRenderDispatcher dispatcher, Set<RenderType> usedRenderTypes, ClientLevel level,
            Vec3 cameraPosition, Transmutation transmutation, float progress) {
        BlockState state = progress < 0.5F ? transmutation.inputState() : transmutation.outputState();
        if (state.getRenderShape() != RenderShape.MODEL) return;
        float burst = Mth.sin(progress * Mth.PI);
        float reform = Math.abs(progress - 0.5F) * 2.0F;
        float fragmentScale = 0.255F + reform * 0.035F;
        BlockPos pos = transmutation.position();

        for (int index = 0; index < 27; index++) {
            int gridX = index % 3 - 1;
            int gridY = index / 3 % 3 - 1;
            int gridZ = index / 9 - 1;
            if (gridX == 0 && gridY == 0 && gridZ == 0) continue;
            Vec3 base = new Vec3(gridX / 3.0D, gridY / 3.0D, gridZ / 3.0D);
            Vec3 radial = base.normalize();
            RandomSource random = RandomSource.create(transmutation.seed() ^ index * 0x9E3779B97F4A7C15L);
            double distance = burst * (0.24D + random.nextDouble() * 0.18D);
            double swirl = Mth.sin(progress * Mth.PI) * (random.nextDouble() - 0.5D) * 0.18D;
            Vec3 offset = base.add(radial.scale(distance)).add(-radial.z * swirl, 0.0D, radial.x * swirl);

            poseStack.pushPose();
            poseStack.translate(
                    pos.getX() + 0.5D + offset.x - cameraPosition.x,
                    pos.getY() + 0.5D + offset.y - cameraPosition.y,
                    pos.getZ() + 0.5D + offset.z - cameraPosition.z
            );
            poseStack.scale(fragmentScale, fragmentScale, fragmentScale);
            poseStack.translate(-0.5D, -0.5D, -0.5D);
            renderBlock(dispatcher, level, state, pos, poseStack, buffers, usedRenderTypes,
                    transmutation.seed() + index);
            poseStack.popPose();
        }
    }

    private static void renderBlock(BlockRenderDispatcher dispatcher, ClientLevel level, BlockState state,
            BlockPos pos, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
            Set<RenderType> usedRenderTypes, long seed) {
        BakedModel model = dispatcher.getBlockModel(state);
        ModelData modelData = model.getModelData(level, pos, state, ModelData.EMPTY);
        for (RenderType sourceType : model.getRenderTypes(state, RandomSource.create(seed), modelData)) {
            RenderType movingType = RenderTypeHelper.getMovingBlockRenderType(sourceType);
            dispatcher.getModelRenderer().tesselateBlock(
                    level, model, state, pos, poseStack, buffers.getBuffer(movingType), false,
                    RandomSource.create(seed), seed, OverlayTexture.NO_OVERLAY, modelData, sourceType
            );
            usedRenderTypes.add(movingType);
        }
    }

    private static void renderPixelMagic(PoseStack poseStack, VertexConsumer vertices, Vec3 cameraPosition,
            Transmutation transmutation, float progress) {
        BlockPos pos = transmutation.position();
        float fade = Mth.clamp(Math.min(progress / 0.12F, (1.0F - progress) / 0.18F), 0.0F, 1.0F);
        int alpha = Mth.clamp((int) (fade * 210.0F), 0, 220);
        double x = pos.getX() + 0.5D - cameraPosition.x;
        double y = pos.getY() + 0.5D - cameraPosition.y;
        double z = pos.getZ() + 0.5D - cameraPosition.z;
        poseStack.pushPose();
        poseStack.translate(x, y, z);

        float pulse = 0.515F + Mth.sin(progress * Mth.PI) * 0.08F;
        renderSegmentedFrame(poseStack, vertices, pulse, alpha, 202, 131, 255);
        renderCornerMotes(poseStack, vertices, transmutation, progress, alpha);
        poseStack.popPose();
    }

    private static void renderSegmentedFrame(PoseStack poseStack, VertexConsumer vertices,
            float half, int alpha, int red, int green, int blue) {
        float thickness = 0.018F;
        float segmentLength = half * 0.38F;
        float[] fixed = {-half, half};
        for (float y : fixed) {
            for (float z : fixed) {
                for (int segment = -1; segment <= 1; segment++) {
                    float center = segment * half * 0.62F;
                    solidBox(poseStack, vertices, center, y, z,
                            segmentLength, thickness, thickness, red, green, blue, alpha);
                }
            }
        }
        for (float x : fixed) {
            for (float z : fixed) {
                for (int segment = -1; segment <= 1; segment++) {
                    float center = segment * half * 0.62F;
                    solidBox(poseStack, vertices, x, center, z,
                            thickness, segmentLength, thickness, red, green, blue, alpha);
                }
            }
        }
        for (float x : fixed) {
            for (float y : fixed) {
                for (int segment = -1; segment <= 1; segment++) {
                    float center = segment * half * 0.62F;
                    solidBox(poseStack, vertices, x, y, center,
                            thickness, thickness, segmentLength, red, green, blue, alpha);
                }
            }
        }
    }

    private static void renderCornerMotes(PoseStack poseStack, VertexConsumer vertices,
            Transmutation transmutation, float progress, int alpha) {
        float gather = smooth(Mth.clamp((progress - 0.08F) / 0.68F, 0.0F, 1.0F));
        float distance = Mth.lerp(gather, 0.64F, 0.08F);
        int index = 0;
        for (int xSign : new int[]{-1, 1}) {
            for (int ySign : new int[]{-1, 1}) {
                for (int zSign : new int[]{-1, 1}) {
                    float wobble = Mth.sin(progress * 18.0F + index * 1.7F) * 0.025F;
                    int red = transmutation.advanced() ? (index % 2 == 0 ? 232 : 155) : 224;
                    int green = transmutation.advanced() ? (index % 2 == 0 ? 211 : 238) : 128;
                    int blue = transmutation.advanced() ? (index % 2 == 0 ? 85 : 124) : 255;
                    float size = 0.045F + (transmutation.rank() / 10.0F) * 0.012F;
                    solidBox(poseStack, vertices,
                            xSign * (distance + wobble),
                            ySign * (distance - wobble),
                            zSign * distance,
                            size, size, size, red, green, blue, alpha);
                    index++;
                }
            }
        }
    }

    private static void solidBox(PoseStack poseStack, VertexConsumer vertices,
            float centerX, float centerY, float centerZ,
            float sizeX, float sizeY, float sizeZ,
            int red, int green, int blue, int alpha) {
        float x0 = centerX - sizeX * 0.5F;
        float x1 = centerX + sizeX * 0.5F;
        float y0 = centerY - sizeY * 0.5F;
        float y1 = centerY + sizeY * 0.5F;
        float z0 = centerZ - sizeZ * 0.5F;
        float z1 = centerZ + sizeZ * 0.5F;
        quad(poseStack, vertices, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, red, green, blue, alpha);
        quad(poseStack, vertices, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, red, green, blue, alpha);
        quad(poseStack, vertices, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, red, green, blue, alpha);
        quad(poseStack, vertices, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, red, green, blue, alpha);
        quad(poseStack, vertices, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, red, green, blue, alpha);
        quad(poseStack, vertices, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, red, green, blue, alpha);
    }

    private static void quad(PoseStack poseStack, VertexConsumer vertices,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3,
            int red, int green, int blue, int alpha) {
        vertices.addVertex(poseStack.last(), x0, y0, z0).setColor(red, green, blue, alpha);
        vertices.addVertex(poseStack.last(), x1, y1, z1).setColor(red, green, blue, alpha);
        vertices.addVertex(poseStack.last(), x2, y2, z2).setColor(red, green, blue, alpha);
        vertices.addVertex(poseStack.last(), x3, y3, z3).setColor(red, green, blue, alpha);
    }

    private static float progress(double visualTime, long startedAt) {
        return Mth.clamp((float) ((visualTime - startedAt) / DURATION_TICKS), 0.0F, 1.0F);
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static void clear() {
        ACTIVE.clear();
        activeLevel = null;
        ClientTransmutationQueue.clear();
    }

    private record Transmutation(
            long startedAt,
            BlockPos position,
            BlockState inputState,
            BlockState outputState,
            boolean advanced,
            int rank,
            long seed
    ) {
    }
}
