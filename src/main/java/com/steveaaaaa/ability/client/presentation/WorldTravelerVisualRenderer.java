package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientWorldTravelerVisualQueue;
import com.steveaaaaa.ability.network.ClientboundWorldTravelerVisualPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
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
    private static final ResourceLocation PORTAL_FRAME_TEXTURE =
            AbilityMod.id("textures/effect/world_traveler_portal_frame.png");
    private static final ResourceLocation PORTAL_SURFACE_TEXTURE =
            AbilityMod.id("block/world_traveler_portal");
    private static final float PORTAL_SCALE = 0.75F;
    private static final int PORTAL_IDLE_TICKS = 20 * 30;
    private static final List<Visual> ACTIVE = new ArrayList<>();
    private static final Map<Integer, PortalState> PORTALS = new HashMap<>();
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
            if (payload.action() == ClientboundWorldTravelerVisualPayload.Action.ROUTE) {
                PortalState current = PORTALS.get(payload.playerId());
                float open = current == null ? 0.0F : current.openAmount();
                PORTALS.put(payload.playerId(), new PortalState(
                        level.getGameTime(), open, open, payload.randomSeed()
                ));
            }
            if (payload.action() == ClientboundWorldTravelerVisualPayload.Action.REMOTE_OPEN) {
                remoteOpenStartedAt = level.getGameTime();
            }
        }
        long gameTime = level.getGameTime();
        ACTIVE.removeIf(visual -> gameTime - visual.startedAt() >= visual.durationTicks());
        PORTALS.entrySet().removeIf(entry -> {
            PortalState portal = entry.getValue();
            float next = gameTime - portal.lastRouteAt() <= PORTAL_IDLE_TICKS
                    ? Math.min(1.0F, portal.openAmount() + 1.0F / 14.0F)
                    : Math.max(0.0F, portal.openAmount() - 1.0F / 18.0F);
            entry.setValue(new PortalState(portal.lastRouteAt(), portal.openAmount(), next, portal.seed()));
            return next <= 0.0F && gameTime - portal.lastRouteAt() > PORTAL_IDLE_TICKS;
        });
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || (ACTIVE.isEmpty() && PORTALS.isEmpty())) return;
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

        RenderType surfaceType = RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS);
        VertexConsumer surfaceVertices = buffers.getBuffer(surfaceType);
        TextureAtlasSprite surfaceSprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(PORTAL_SURFACE_TEXTURE);
        for (Map.Entry<Integer, PortalState> entry : PORTALS.entrySet()) {
            Entity player = level.getEntity(entry.getKey());
            if (player == null || player.isRemoved()) continue;
            float open = Mth.lerp(partialTick, entry.getValue().previousOpenAmount(),
                    entry.getValue().openAmount());
            Vec3 portal = portalPosition(player, partialTick);
            renderPortalSurface(poseStack, surfaceVertices, surfaceSprite, camera,
                    cameraPosition, portal, open);
        }
        buffers.endBatch(surfaceType);

        RenderType frameType = RenderType.entityTranslucentEmissive(PORTAL_FRAME_TEXTURE);
        VertexConsumer frameTexture = buffers.getBuffer(frameType);
        for (Map.Entry<Integer, PortalState> entry : PORTALS.entrySet()) {
            Entity player = level.getEntity(entry.getKey());
            if (player == null || player.isRemoved()) continue;
            float open = Mth.lerp(partialTick, entry.getValue().previousOpenAmount(),
                    entry.getValue().openAmount());
            renderPortalFrame(poseStack, frameTexture, camera, cameraPosition,
                    portalPosition(player, partialTick), open);
        }
        buffers.endBatch(frameType);

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
        renderCompressedPixels(poseStack, vertices, cameraPosition, center, portal,
                progress, false, alpha, visual.payload().randomSeed());

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
        // Remote opening is represented by the folding GUI frame only. Keeping world portals tied
        // exclusively to successful item routes guarantees one small persistent portal per player.
    }

    private static void renderPortalSurface(PoseStack poseStack, VertexConsumer vertices,
            TextureAtlasSprite sprite, Camera camera, Vec3 cameraPosition,
            Vec3 position, float openAmount) {
        if (openAmount <= 0.001F) return;
        poseStack.pushPose();
        poseStack.translate(position.x - cameraPosition.x, position.y - cameraPosition.y,
                position.z - cameraPosition.z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(PORTAL_SCALE, PORTAL_SCALE, PORTAL_SCALE);
        float eased = smooth(openAmount);
        float halfSize = 0.012F + eased * 0.275F;
        int alpha = Mth.clamp((int) Mth.lerp(eased, 75.0F, 190.0F), 0, 190);
        portalQuad(poseStack, vertices, -halfSize, -halfSize, halfSize, halfSize,
                -0.002F, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), alpha);
        portalQuad(poseStack, vertices, halfSize, -halfSize, -halfSize, halfSize,
                -0.002F, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), alpha);
        poseStack.popPose();
    }

    private static void renderPortalFrame(PoseStack poseStack, VertexConsumer vertices, Camera camera,
            Vec3 cameraPosition, Vec3 position, float openAmount) {
        if (openAmount <= 0.001F) return;
        poseStack.pushPose();
        poseStack.translate(position.x - cameraPosition.x, position.y - cameraPosition.y,
                position.z - cameraPosition.z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(PORTAL_SCALE, PORTAL_SCALE, PORTAL_SCALE);
        float eased = smooth(openAmount);
        float halfWidth = 0.018F + eased * 0.315F;
        float halfHeight = halfWidth;
        int alpha = Mth.clamp((int) Mth.lerp(eased, 105.0F, 220.0F), 0, 220);
        portalQuad(poseStack, vertices, -halfWidth, -halfHeight, halfWidth, halfHeight,
                0.0F, 0.0F, 0.0F, 1.0F, 1.0F, alpha);
        portalQuad(poseStack, vertices, halfWidth, -halfHeight, -halfWidth, halfHeight,
                0.0F, 0.0F, 0.0F, 1.0F, 1.0F, alpha);
        poseStack.popPose();
    }

    private static void portalQuad(PoseStack poseStack, VertexConsumer vertices,
            float left, float bottom, float right, float top, float depth,
            float u0, float v0, float u1, float v1, int alpha) {
        portalVertex(poseStack, vertices, left, bottom, depth, u0, v1, alpha);
        portalVertex(poseStack, vertices, right, bottom, depth, u1, v1, alpha);
        portalVertex(poseStack, vertices, right, top, depth, u1, v0, alpha);
        portalVertex(poseStack, vertices, left, top, depth, u0, v0, alpha);
    }

    private static void portalVertex(PoseStack poseStack, VertexConsumer vertices,
            float x, float y, float z, float u, float v, int alpha) {
        vertices.addVertex(poseStack.last(), x, y, z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
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
            int red = 247;
            int green = 214;
            int blue = 139;
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

    private static Vec3 portalPosition(Entity player, float partialTick) {
        Vec3 center = interpolatedCenter(player, partialTick);
        Vec3 right = new Vec3(Mth.cos(player.getYRot() * Mth.DEG_TO_RAD), 0.0D,
                Mth.sin(player.getYRot() * Mth.DEG_TO_RAD));
        return center.add(right.scale(0.72D)).add(0.0D, 0.22D, 0.0D);
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
        PORTALS.clear();
        activeLevel = null;
        remoteOpenStartedAt = Long.MIN_VALUE;
        ClientWorldTravelerVisualQueue.clear();
    }

    private record Visual(ClientboundWorldTravelerVisualPayload payload, long startedAt, int durationTicks) {
        float progress(double visualTime) {
            return Mth.clamp((float) ((visualTime - startedAt) / durationTicks), 0.0F, 1.0F);
        }
    }

    private record PortalState(long lastRouteAt, float previousOpenAmount, float openAmount, long seed) {
    }
}
