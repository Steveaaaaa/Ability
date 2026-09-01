package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientWorldTravelerVisualQueue;
import com.steveaaaaa.ability.network.ClientboundWorldTravelerVisualPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class WorldTravelerVisualRenderer {
    private static final List<Visual> ACTIVE = new ArrayList<>();
    private static ClientLevel activeLevel;
    private static long remoteOpenStartedAt = Long.MIN_VALUE;

    private WorldTravelerVisualRenderer() {
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
        ClientboundWorldTravelerVisualPayload payload;
        while ((payload = ClientWorldTravelerVisualQueue.poll()) != null) {
            if (ACTIVE.size() >= 24) ACTIVE.remove(0);
            int duration = switch (payload.action()) {
                case BIND -> 24;
                case ROUTE -> 18;
                case REMOTE_OPEN -> 14;
            };
            ACTIVE.add(new Visual(payload, level.getGameTime(), duration));
            if (payload.action() == ClientboundWorldTravelerVisualPayload.Action.REMOTE_OPEN) {
                remoteOpenStartedAt = level.getGameTime();
            }
        }
        long gameTime = level.getGameTime();
        ACTIVE.removeIf(visual -> gameTime - visual.startedAt() >= visual.durationTicks());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
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

        for (Visual visual : ACTIVE) {
            if (visual.payload().action() == ClientboundWorldTravelerVisualPayload.Action.ROUTE) {
                renderRoutedItem(minecraft, level, poseStack, buffers, camera,
                        cameraPosition, visual, visual.progress(visualTime), partialTick);
            }
        }

        VertexConsumer pixels = buffers.getBuffer(RenderType.lightning());
        for (Visual visual : ACTIVE) {
            float progress = visual.progress(visualTime);
            switch (visual.payload().action()) {
                case BIND -> renderBinding(level, poseStack, pixels, cameraPosition, visual, progress, partialTick);
                case ROUTE -> renderRoute(level, poseStack, pixels, camera, cameraPosition, visual, progress, partialTick);
                case REMOTE_OPEN -> renderRemotePortal(level, poseStack, pixels, camera,
                        cameraPosition, visual, progress, partialTick);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static void renderRoutedItem(Minecraft minecraft, ClientLevel level, PoseStack poseStack,
            MultiBufferSource.BufferSource buffers, Camera camera, Vec3 cameraPosition,
            Visual visual, float progress, float partialTick) {
        Entity player = level.getEntity(visual.payload().playerId());
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getOptional(visual.payload().itemId()).orElse(null);
        if (player == null || item == null || progress >= 0.72F) return;
        Vec3 center = interpolatedCenter(player, partialTick);
        Vec3 right = new Vec3(Mth.cos(player.getYRot() * Mth.DEG_TO_RAD), 0.0D,
                Mth.sin(player.getYRot() * Mth.DEG_TO_RAD));
        Vec3 portal = center.add(right.scale(0.72D)).add(0.0D, 0.22D, 0.0D);
        float travel = smooth(Mth.clamp(progress / 0.68F, 0.0F, 1.0F));
        Vec3 position = center.add(0.0D, 0.12D, 0.0D).lerp(portal, travel);
        float scale = 0.48F * (1.0F - travel * 0.82F);

        poseStack.pushPose();
        poseStack.translate(position.x - cameraPosition.x, position.y - cameraPosition.y,
                position.z - cameraPosition.z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(scale, scale, scale);
        minecraft.getItemRenderer().renderStatic(
                new ItemStack(item), ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                poseStack, buffers, level, (int) visual.payload().randomSeed()
        );
        poseStack.popPose();
    }

    private static void renderBinding(ClientLevel level, PoseStack poseStack, VertexConsumer vertices,
            Vec3 cameraPosition, Visual visual, float progress, float partialTick) {
        ClientboundWorldTravelerVisualPayload payload = visual.payload();
        if (!sameDimension(level, payload)) return;
        Vec3 container = payload.destination().getCenter();
        int alpha = fadeAlpha(progress, 225);
        poseStack.pushPose();
        poseStack.translate(container.x - cameraPosition.x, container.y - cameraPosition.y,
                container.z - cameraPosition.z);
        renderSegmentedFrame(poseStack, vertices, 0.54F + Mth.sin(progress * Mth.PI) * 0.08F,
                218, 174, 73, alpha);
        renderCompassMark(poseStack, vertices, alpha);
        poseStack.popPose();

        Entity player = level.getEntity(payload.playerId());
        if (player != null && progress < 0.72F) {
            Vec3 from = interpolatedCenter(player, partialTick).add(0.0D, 0.1D, 0.0D);
            Vec3 to = container;
            renderPixelLine(poseStack, vertices, cameraPosition, from, to,
                    1.0F - progress / 0.72F, 226, 190, 104, alpha);
        }
    }

    private static void renderRoute(ClientLevel level, PoseStack poseStack, VertexConsumer vertices,
            Camera camera, Vec3 cameraPosition, Visual visual, float progress, float partialTick) {
        Entity player = level.getEntity(visual.payload().playerId());
        if (player == null) return;
        Vec3 center = interpolatedCenter(player, partialTick);
        Vec3 right = new Vec3(Mth.cos(player.getYRot() * Mth.DEG_TO_RAD), 0.0D,
                Mth.sin(player.getYRot() * Mth.DEG_TO_RAD));
        Vec3 portal = center.add(right.scale(0.72D)).add(0.0D, 0.22D, 0.0D);
        int alpha = fadeAlpha(progress, 220);
        renderBillboardPortal(poseStack, vertices, camera, cameraPosition, portal,
                progress, visual.payload().crossDimension(), alpha);
        renderCompressedPixels(poseStack, vertices, cameraPosition, center, portal,
                progress, visual.payload().crossDimension(), alpha, visual.payload().randomSeed());

        if (sameDimension(level, visual.payload())
                && center.distanceToSqr(visual.payload().destination().getCenter()) <= 48.0D * 48.0D) {
            Vec3 destination = visual.payload().destination().getCenter();
            poseStack.pushPose();
            poseStack.translate(destination.x - cameraPosition.x,
                    destination.y - cameraPosition.y, destination.z - cameraPosition.z);
            float pulse = Mth.sin(progress * Mth.PI);
            renderSegmentedFrame(poseStack, vertices, 0.51F + pulse * 0.10F,
                    225, 184, 82, (int) (alpha * pulse));
            poseStack.popPose();
        }
    }

    private static void renderRemotePortal(ClientLevel level, PoseStack poseStack, VertexConsumer vertices,
            Camera camera, Vec3 cameraPosition, Visual visual, float progress, float partialTick) {
        Entity player = level.getEntity(visual.payload().playerId());
        if (player == null) return;
        Vec3 center = interpolatedCenter(player, partialTick).add(0.0D, 0.35D, 0.0D);
        renderBillboardPortal(poseStack, vertices, camera, cameraPosition, center,
                progress, visual.payload().crossDimension(), fadeAlpha(progress, 190));
    }

    private static void renderBillboardPortal(PoseStack poseStack, VertexConsumer vertices, Camera camera,
            Vec3 cameraPosition, Vec3 position, float progress, boolean crossDimension, int alpha) {
        poseStack.pushPose();
        poseStack.translate(position.x - cameraPosition.x, position.y - cameraPosition.y,
                position.z - cameraPosition.z);
        poseStack.mulPose(camera.rotation());
        float scale = 0.24F + smooth(progress) * 0.22F;
        int red = crossDimension ? 183 : 231;
        int green = crossDimension ? 126 : 190;
        int blue = crossDimension ? 255 : 92;
        for (int side = -1; side <= 1; side += 2) {
            solidBox(poseStack, vertices, side * scale, 0.0F, 0.0F,
                    0.045F, scale * 1.75F, 0.026F, red, green, blue, alpha);
            solidBox(poseStack, vertices, 0.0F, side * scale, 0.0F,
                    scale * 1.75F, 0.045F, 0.026F, red, green, blue, alpha);
        }
        poseStack.popPose();
    }

    private static void renderCompressedPixels(PoseStack poseStack, VertexConsumer vertices,
            Vec3 cameraPosition, Vec3 from, Vec3 portal, float progress, boolean crossDimension,
            int alpha, long seed) {
        for (int index = 0; index < 8; index++) {
            float offset = ((seed >>> (index * 7)) & 0x7FL) / 127.0F;
            float travel = smooth(Mth.clamp(progress * 1.35F - offset * 0.25F, 0.0F, 1.0F));
            Vec3 start = from.add(
                    ((index & 1) - 0.5D) * 0.36D,
                    ((index >> 1 & 1) - 0.5D) * 0.5D,
                    ((index >> 2 & 1) - 0.5D) * 0.36D
            );
            Vec3 position = start.lerp(portal, travel).subtract(cameraPosition);
            int red = crossDimension ? 189 : 239;
            int green = crossDimension ? 137 : 204;
            int blue = crossDimension ? 255 : 112;
            poseStack.pushPose();
            poseStack.translate(position.x, position.y, position.z);
            float size = 0.045F * (1.0F - travel * 0.65F);
            solidBox(poseStack, vertices, 0, 0, 0, size, size, size, red, green, blue, alpha);
            poseStack.popPose();
        }
    }

    private static void renderPixelLine(PoseStack poseStack, VertexConsumer vertices, Vec3 cameraPosition,
            Vec3 from, Vec3 to, float visible, int red, int green, int blue, int alpha) {
        int count = 13;
        for (int index = 0; index < count; index++) {
            float fraction = (index + 0.5F) / count;
            if (fraction > visible) continue;
            Vec3 position = from.lerp(to, fraction).subtract(cameraPosition);
            poseStack.pushPose();
            poseStack.translate(position.x, position.y, position.z);
            solidBox(poseStack, vertices, 0, 0, 0,
                    0.035F, 0.035F, 0.035F, red, green, blue, alpha);
            poseStack.popPose();
        }
    }

    private static void renderCompassMark(PoseStack poseStack, VertexConsumer vertices, int alpha) {
        float y = 0.575F;
        solidBox(poseStack, vertices, 0, y, 0, 0.08F, 0.022F, 0.42F, 247, 213, 128, alpha);
        solidBox(poseStack, vertices, 0, y, 0, 0.42F, 0.022F, 0.08F, 247, 213, 128, alpha);
        solidBox(poseStack, vertices, 0, y + 0.012F, -0.25F,
                0.12F, 0.025F, 0.12F, 255, 235, 166, alpha);
    }

    private static void renderSegmentedFrame(PoseStack poseStack, VertexConsumer vertices,
            float half, int red, int green, int blue, int alpha) {
        float thickness = 0.018F;
        float length = half * 0.38F;
        float[] fixed = {-half, half};
        for (float y : fixed) for (float z : fixed) for (int segment = -1; segment <= 1; segment++)
            solidBox(poseStack, vertices, segment * half * 0.62F, y, z,
                    length, thickness, thickness, red, green, blue, alpha);
        for (float x : fixed) for (float z : fixed) for (int segment = -1; segment <= 1; segment++)
            solidBox(poseStack, vertices, x, segment * half * 0.62F, z,
                    thickness, length, thickness, red, green, blue, alpha);
        for (float x : fixed) for (float y : fixed) for (int segment = -1; segment <= 1; segment++)
            solidBox(poseStack, vertices, x, y, segment * half * 0.62F,
                    thickness, thickness, length, red, green, blue, alpha);
    }

    private static void solidBox(PoseStack poseStack, VertexConsumer vertices,
            float centerX, float centerY, float centerZ, float sizeX, float sizeY, float sizeZ,
            int red, int green, int blue, int alpha) {
        float x0 = centerX - sizeX * 0.5F, x1 = centerX + sizeX * 0.5F;
        float y0 = centerY - sizeY * 0.5F, y1 = centerY + sizeY * 0.5F;
        float z0 = centerZ - sizeZ * 0.5F, z1 = centerZ + sizeZ * 0.5F;
        quad(poseStack, vertices, x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0, red,green,blue,alpha);
        quad(poseStack, vertices, x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1, red,green,blue,alpha);
        quad(poseStack, vertices, x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, red,green,blue,alpha);
        quad(poseStack, vertices, x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, red,green,blue,alpha);
        quad(poseStack, vertices, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, red,green,blue,alpha);
        quad(poseStack, vertices, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, red,green,blue,alpha);
    }

    private static void quad(PoseStack poseStack, VertexConsumer vertices,
            float x0,float y0,float z0, float x1,float y1,float z1,
            float x2,float y2,float z2, float x3,float y3,float z3,
            int red,int green,int blue,int alpha) {
        vertices.addVertex(poseStack.last(), x0,y0,z0).setColor(red,green,blue,alpha);
        vertices.addVertex(poseStack.last(), x1,y1,z1).setColor(red,green,blue,alpha);
        vertices.addVertex(poseStack.last(), x2,y2,z2).setColor(red,green,blue,alpha);
        vertices.addVertex(poseStack.last(), x3,y3,z3).setColor(red,green,blue,alpha);
    }

    @SubscribeEvent
    public static void renderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || remoteOpenStartedAt == Long.MIN_VALUE) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float age = minecraft.level.getGameTime() + partialTick - remoteOpenStartedAt;
        if (age < 0.0F || age >= 14.0F) return;
        float progress = smooth(Mth.clamp(age / 10.0F, 0.0F, 1.0F));
        int alpha = Mth.clamp((int) ((1.0F - age / 14.0F) * 205.0F), 0, 205);
        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int halfW = (int) Mth.lerp(progress, 18.0F, width * 0.46F);
        int halfH = (int) Mth.lerp(progress, 12.0F, height * 0.44F);
        int cx = width / 2;
        int cy = height / 2;
        int color = alpha << 24 | 0xD8A94F;
        int purple = alpha * 2 / 3 << 24 | 0xA47BE8;
        pixelBorder(graphics, cx - halfW, cy - halfH, cx + halfW, cy + halfH, color, 3);
        pixelBorder(graphics, cx - halfW + 5, cy - halfH + 5,
                cx + halfW - 5, cy + halfH - 5, purple, 2);
    }

    private static void pixelBorder(GuiGraphics graphics, int left, int top, int right, int bottom,
            int color, int thickness) {
        int segment = Math.max(6, (right - left) / 7);
        for (int index = 0; index < 4; index++) {
            int x = left + index * (right - left - segment) / 3;
            graphics.fill(x, top, x + segment, top + thickness, color);
            graphics.fill(x, bottom - thickness, x + segment, bottom, color);
        }
        segment = Math.max(6, (bottom - top) / 7);
        for (int index = 0; index < 4; index++) {
            int y = top + index * (bottom - top - segment) / 3;
            graphics.fill(left, y, left + thickness, y + segment, color);
            graphics.fill(right - thickness, y, right, y + segment, color);
        }
    }

    private static Vec3 interpolatedCenter(Entity entity, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() * 0.55D,
                Mth.lerp(partialTick, entity.zo, entity.getZ())
        );
    }

    private static boolean sameDimension(ClientLevel level, ClientboundWorldTravelerVisualPayload payload) {
        return level.dimension().location().equals(payload.destinationDimension());
    }

    private static int fadeAlpha(float progress, int maximum) {
        float fade = Mth.clamp(Math.min(progress / 0.12F, (1.0F - progress) / 0.22F), 0.0F, 1.0F);
        return Mth.clamp((int) (fade * maximum), 0, maximum);
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static void clear() {
        ACTIVE.clear();
        activeLevel = null;
        remoteOpenStartedAt = Long.MIN_VALUE;
        ClientWorldTravelerVisualQueue.clear();
    }

    private record Visual(ClientboundWorldTravelerVisualPayload payload, long startedAt, int durationTicks) {
        float progress(double visualTime) {
            return Mth.clamp((float) ((visualTime - startedAt) / durationTicks), 0.0F, 1.0F);
        }
    }
}
