package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientTransmutationQueue;
import com.steveaaaaa.ability.network.ClientboundTransmutationPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

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
            hideConvertedBlock(level, payload.position());
        }
        long gameTime = level.getGameTime();
        ACTIVE.removeIf(active -> {
            if (gameTime - active.startedAt() >= DURATION_TICKS) {
                revealConvertedBlock(level, active);
                return true;
            }
            hideConvertedBlock(level, active.position());
            return false;
        });
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
        RenderType fragmentType = RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS);
        VertexConsumer fragmentVertices = buffers.getBuffer(fragmentType);

        for (Transmutation transmutation : ACTIVE) {
            float progress = progress(visualTime, transmutation.startedAt());
            renderFragments(poseStack, fragmentVertices, minecraft, level,
                    cameraPosition, transmutation, progress);
        }
        buffers.endBatch(fragmentType);

        VertexConsumer pixels = buffers.getBuffer(RenderType.lightning());
        for (Transmutation transmutation : ACTIVE) {
            float progress = progress(visualTime, transmutation.startedAt());
            renderPixelMagic(poseStack, pixels, cameraPosition, transmutation, progress);
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static void renderFragments(PoseStack poseStack, VertexConsumer vertices,
            Minecraft minecraft, ClientLevel level,
            Vec3 cameraPosition, Transmutation transmutation, float progress) {
        BlockState state = progress < 0.5F ? transmutation.inputState() : transmutation.outputState();
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        TextureAtlasSprite sprite = model.getParticleIcon();
        float burst = Mth.sin(progress * Mth.PI);
        float reform = Math.abs(progress - 0.5F) * 2.0F;
        float fragmentScale = 0.282F + reform * 0.018F;
        BlockPos pos = transmutation.position();
        int packedLight = LevelRenderer.getLightColor(level, pos);

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
            renderFragment(poseStack, vertices, sprite, gridX, gridY, gridZ, packedLight);
            poseStack.popPose();
        }
    }

    private static void renderFragment(PoseStack poseStack, VertexConsumer vertices, TextureAtlasSprite sprite,
            int gridX, int gridY, int gridZ, int packedLight) {
        fragmentFace(poseStack, vertices, sprite, Direction.NORTH, gridX, gridY, gridZ,
                gridZ == -1, packedLight);
        fragmentFace(poseStack, vertices, sprite, Direction.SOUTH, gridX, gridY, gridZ,
                gridZ == 1, packedLight);
        fragmentFace(poseStack, vertices, sprite, Direction.WEST, gridX, gridY, gridZ,
                gridX == -1, packedLight);
        fragmentFace(poseStack, vertices, sprite, Direction.EAST, gridX, gridY, gridZ,
                gridX == 1, packedLight);
        fragmentFace(poseStack, vertices, sprite, Direction.UP, gridX, gridY, gridZ,
                gridY == 1, packedLight);
        fragmentFace(poseStack, vertices, sprite, Direction.DOWN, gridX, gridY, gridZ,
                gridY == -1, packedLight);
    }

    private static void fragmentFace(PoseStack poseStack, VertexConsumer vertices, TextureAtlasSprite sprite,
            Direction face, int gridX, int gridY, int gridZ, boolean exterior, int packedLight) {
        float u0;
        float u1;
        float v0;
        float v1;
        if (exterior) {
            int uCell;
            int vCell;
            if (face.getAxis() == Direction.Axis.Z) {
                uCell = face == Direction.NORTH ? 1 - gridX : gridX + 1;
                vCell = 1 - gridY;
            } else if (face.getAxis() == Direction.Axis.X) {
                uCell = face == Direction.EAST ? 1 - gridZ : gridZ + 1;
                vCell = 1 - gridY;
            } else {
                uCell = gridX + 1;
                vCell = face == Direction.UP ? gridZ + 1 : 1 - gridZ;
            }
            u0 = uCell / 3.0F;
            u1 = (uCell + 1) / 3.0F;
            v0 = vCell / 3.0F;
            v1 = (vCell + 1) / 3.0F;
        } else {
            u0 = 0.43F;
            u1 = 0.57F;
            v0 = 0.43F;
            v1 = 0.57F;
        }
        int red = exterior ? 255 : 112;
        int green = exterior ? 255 : 52;
        int blue = exterior ? 255 : 145;
        texturedFace(poseStack, vertices, sprite, face, u0, u1, v0, v1,
                red, green, blue, packedLight);
    }

    private static void texturedFace(PoseStack poseStack, VertexConsumer vertices, TextureAtlasSprite sprite,
            Direction face, float u0, float u1, float v0, float v1,
            int red, int green, int blue, int packedLight) {
        float min = -0.5F;
        float max = 0.5F;
        float atlasU0 = Mth.lerp(u0, sprite.getU0(), sprite.getU1());
        float atlasU1 = Mth.lerp(u1, sprite.getU0(), sprite.getU1());
        float atlasV0 = Mth.lerp(v0, sprite.getV0(), sprite.getV1());
        float atlasV1 = Mth.lerp(v1, sprite.getV0(), sprite.getV1());
        switch (face) {
            case NORTH -> texturedQuad(poseStack, vertices,
                    min, min, min, max, min, min, max, max, min, min, max, min,
                    atlasU0, atlasU1, atlasV0, atlasV1, red, green, blue, packedLight, 0, 0, -1);
            case SOUTH -> texturedQuad(poseStack, vertices,
                    max, min, max, min, min, max, min, max, max, max, max, max,
                    atlasU0, atlasU1, atlasV0, atlasV1, red, green, blue, packedLight, 0, 0, 1);
            case WEST -> texturedQuad(poseStack, vertices,
                    min, min, max, min, min, min, min, max, min, min, max, max,
                    atlasU0, atlasU1, atlasV0, atlasV1, red, green, blue, packedLight, -1, 0, 0);
            case EAST -> texturedQuad(poseStack, vertices,
                    max, min, min, max, min, max, max, max, max, max, max, min,
                    atlasU0, atlasU1, atlasV0, atlasV1, red, green, blue, packedLight, 1, 0, 0);
            case UP -> texturedQuad(poseStack, vertices,
                    min, max, min, max, max, min, max, max, max, min, max, max,
                    atlasU0, atlasU1, atlasV0, atlasV1, red, green, blue, packedLight, 0, 1, 0);
            case DOWN -> texturedQuad(poseStack, vertices,
                    min, min, max, max, min, max, max, min, min, min, min, min,
                    atlasU0, atlasU1, atlasV0, atlasV1, red, green, blue, packedLight, 0, -1, 0);
        }
    }

    private static void texturedQuad(PoseStack poseStack, VertexConsumer vertices,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3,
            float u0, float u1, float v0, float v1, int red, int green, int blue,
            int packedLight, float normalX, float normalY, float normalZ) {
        texturedVertex(poseStack, vertices, x0, y0, z0, u0, v1, red, green, blue,
                packedLight, normalX, normalY, normalZ);
        texturedVertex(poseStack, vertices, x1, y1, z1, u1, v1, red, green, blue,
                packedLight, normalX, normalY, normalZ);
        texturedVertex(poseStack, vertices, x2, y2, z2, u1, v0, red, green, blue,
                packedLight, normalX, normalY, normalZ);
        texturedVertex(poseStack, vertices, x3, y3, z3, u0, v0, red, green, blue,
                packedLight, normalX, normalY, normalZ);
    }

    private static void texturedVertex(PoseStack poseStack, VertexConsumer vertices,
            float x, float y, float z, float u, float v, int red, int green, int blue,
            int packedLight, float normalX, float normalY, float normalZ) {
        vertices.addVertex(poseStack.last(), x, y, z)
                .setColor(red, green, blue, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(poseStack.last(), normalX, normalY, normalZ);
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

    private static void hideConvertedBlock(ClientLevel level, BlockPos position) {
        if (!level.getBlockState(position).isAir()) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 19, 512);
        }
    }

    private static void revealConvertedBlock(ClientLevel level, Transmutation transmutation) {
        if (level.getBlockState(transmutation.position()).isAir()) {
            level.setBlock(transmutation.position(), transmutation.outputState(), 19, 512);
        }
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
