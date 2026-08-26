package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientboundGolemReinforcementPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ObsidianReinforcementRenderer {
    public static final ResourceLocation SHIELD_TEXTURE =
            AbilityMod.id("textures/entity/iron_golem/obsidian_shield.png");
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/obsidian.png");
    private static final int SEGMENTS = 12;
    private static final int RINGS = 6;

    private ObsidianReinforcementRenderer() {
    }

    @SubscribeEvent
    public static void renderShield(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        GolemReinforcementClientState.State state = GolemReinforcementClientState.get(golem.getUUID());
        if (state == null) return;

        float time = golem.tickCount + event.getPartialTick();
        float animationAge = animationAge(golem, state, event.getPartialTick());
        boolean recentBlock = state.visualEvent() == ClientboundGolemReinforcementPayload.VisualEvent.SHIELD_BLOCKED
                && animationAge < 10.0F;
        if (state.shields() <= 0 && !recentBlock) return;

        if (state.shields() > 0) renderShell(event, state, time, animationAge);
        if (recentBlock) renderImpactWave(event, state, animationAge);
    }

    private static void renderShell(RenderLivingEvent.Post<?, ?> event,
            GolemReinforcementClientState.State state, float time, float animationAge) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation texture = minecraft.getResourceManager().getResource(SHIELD_TEXTURE).isPresent()
                ? SHIELD_TEXTURE : FALLBACK_TEXTURE;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0D, 1.38D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees(time * 0.32F));

        float scale = 1.0F + Mth.sin(time * 0.09F) * 0.012F;
        float animationAlpha = 1.0F;
        if (state.visualEvent() == ClientboundGolemReinforcementPayload.VisualEvent.ACTIVATED
                && animationAge < 14.0F) {
            float progress = Mth.clamp(animationAge / 14.0F, 0.0F, 1.0F);
            scale *= 0.68F + easeOut(progress) * 0.32F;
            animationAlpha = progress;
        } else if (state.visualEvent() == ClientboundGolemReinforcementPayload.VisualEvent.SHIELD_GAINED
                && animationAge < 12.0F) {
            float progress = Mth.clamp(animationAge / 12.0F, 0.0F, 1.0F);
            scale *= 1.0F + Mth.sin(progress * Mth.PI) * 0.075F;
        }
        pose.scale(scale, scale, scale);

        float strength = state.shields() / (float) Math.max(1, state.maxShields());
        int alpha = (int) ((30.0F + strength * 30.0F) * animationAlpha);
        VertexConsumer faces = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(texture));
        renderFacetedSurface(pose, faces, event.getPackedLight(), 1.03F, 1.56F, 0.84F,
                118, 82, 142, alpha, time * 0.0025F);

        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        for (int layer = 0; layer < state.shields(); layer++) {
            float offset = layer * 0.025F;
            int lineAlpha = (int) ((105.0F - layer * 18.0F) * animationAlpha);
            renderFacetLines(pose, lines, 1.035F + offset, 1.565F + offset, 0.845F + offset,
                    126, 88, 158, lineAlpha);
        }
        pose.popPose();
    }

    private static void renderFacetedSurface(PoseStack pose, VertexConsumer consumer, int packedLight,
            float radiusX, float radiusY, float radiusZ, int red, int green, int blue, int alpha, float uvOffset) {
        for (int ring = 0; ring < RINGS; ring++) {
            float lat0 = -Mth.HALF_PI + Mth.PI * ring / RINGS;
            float lat1 = -Mth.HALF_PI + Mth.PI * (ring + 1) / RINGS;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                float lon0 = Mth.TWO_PI * segment / SEGMENTS;
                float lon1 = Mth.TWO_PI * (segment + 1) / SEGMENTS;
                vertex(pose, consumer, lat0, lon0, radiusX, radiusY, radiusZ, red, green, blue, alpha,
                        segment / (float) SEGMENTS + uvOffset, ring / (float) RINGS, packedLight);
                vertex(pose, consumer, lat0, lon1, radiusX, radiusY, radiusZ, red, green, blue, alpha,
                        (segment + 1) / (float) SEGMENTS + uvOffset, ring / (float) RINGS, packedLight);
                vertex(pose, consumer, lat1, lon1, radiusX, radiusY, radiusZ, red, green, blue, alpha,
                        (segment + 1) / (float) SEGMENTS + uvOffset, (ring + 1) / (float) RINGS, packedLight);
                vertex(pose, consumer, lat1, lon0, radiusX, radiusY, radiusZ, red, green, blue, alpha,
                        segment / (float) SEGMENTS + uvOffset, (ring + 1) / (float) RINGS, packedLight);
            }
        }
    }

    private static void vertex(PoseStack pose, VertexConsumer consumer, float latitude, float longitude,
            float radiusX, float radiusY, float radiusZ, int red, int green, int blue, int alpha,
            float u, float v, int packedLight) {
        float cosLat = Mth.cos(latitude);
        float nx = cosLat * Mth.cos(longitude);
        float ny = Mth.sin(latitude);
        float nz = cosLat * Mth.sin(longitude);
        consumer.addVertex(pose.last(), nx * radiusX, ny * radiusY, nz * radiusZ)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose.last(), nx, ny, nz);
    }

    private static void renderFacetLines(PoseStack pose, VertexConsumer consumer,
            float radiusX, float radiusY, float radiusZ, int red, int green, int blue, int alpha) {
        for (int segment = 0; segment < SEGMENTS; segment++) {
            float longitude = Mth.TWO_PI * segment / SEGMENTS;
            for (int ring = 0; ring < RINGS; ring++) {
                lineVertex(pose, consumer, -Mth.HALF_PI + Mth.PI * ring / RINGS, longitude,
                        radiusX, radiusY, radiusZ, red, green, blue, alpha);
                lineVertex(pose, consumer, -Mth.HALF_PI + Mth.PI * (ring + 1) / RINGS, longitude,
                        radiusX, radiusY, radiusZ, red, green, blue, alpha);
            }
        }
        for (int ring = 1; ring < RINGS; ring++) {
            float latitude = -Mth.HALF_PI + Mth.PI * ring / RINGS;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                lineVertex(pose, consumer, latitude, Mth.TWO_PI * segment / SEGMENTS,
                        radiusX, radiusY, radiusZ, red, green, blue, alpha);
                lineVertex(pose, consumer, latitude, Mth.TWO_PI * (segment + 1) / SEGMENTS,
                        radiusX, radiusY, radiusZ, red, green, blue, alpha);
            }
        }
    }

    private static void lineVertex(PoseStack pose, VertexConsumer consumer, float latitude, float longitude,
            float radiusX, float radiusY, float radiusZ, int red, int green, int blue, int alpha) {
        float cosLat = Mth.cos(latitude);
        float nx = cosLat * Mth.cos(longitude);
        float ny = Mth.sin(latitude);
        float nz = cosLat * Mth.sin(longitude);
        consumer.addVertex(pose.last(), nx * radiusX, ny * radiusY, nz * radiusZ)
                .setColor(red, green, blue, alpha)
                .setNormal(pose.last(), nx, ny, nz);
    }

    private static void renderImpactWave(RenderLivingEvent.Post<?, ?> event,
            GolemReinforcementClientState.State state, float animationAge) {
        float progress = Mth.clamp(animationAge / 10.0F, 0.0F, 1.0F);
        float dx = state.impactX();
        float dz = state.impactZ();
        float horizontal = Mth.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.001F) {
            dx = 0.0F;
            dz = 1.0F;
            horizontal = 1.0F;
        }
        dx /= horizontal;
        dz /= horizontal;
        float centerX = dx * 1.06F;
        float centerZ = dz * 0.87F;
        float centerY = 1.38F + Mth.clamp(state.impactY(), -0.7F, 0.7F) * 0.9F;
        float tangentX = -dz;
        float tangentZ = dx;
        int alpha = (int) ((1.0F - progress) * 230.0F);
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        for (int ring = 0; ring < 3; ring++) {
            float size = 0.16F + progress * 0.62F + ring * 0.07F;
            diamond(event.getPoseStack(), lines, centerX, centerY, centerZ, tangentX, tangentZ, size,
                    206, 157, 231, Math.max(0, alpha - ring * 35));
        }
    }

    private static void diamond(PoseStack pose, VertexConsumer consumer, float cx, float cy, float cz,
            float tx, float tz, float size, int red, int green, int blue, int alpha) {
        float[][] points = {
                {cx, cy + size, cz},
                {cx + tx * size, cy, cz + tz * size},
                {cx, cy - size, cz},
                {cx - tx * size, cy, cz - tz * size}
        };
        for (int i = 0; i < points.length; i++) {
            float[] first = points[i];
            float[] second = points[(i + 1) % points.length];
            consumer.addVertex(pose.last(), first[0], first[1], first[2]).setColor(red, green, blue, alpha)
                    .setNormal(pose.last(), 0.0F, 1.0F, 0.0F);
            consumer.addVertex(pose.last(), second[0], second[1], second[2]).setColor(red, green, blue, alpha)
                    .setNormal(pose.last(), 0.0F, 1.0F, 0.0F);
        }
    }

    private static float animationAge(IronGolem golem, GolemReinforcementClientState.State state, float partialTick) {
        return Math.max(0.0F, golem.level().getGameTime() + partialTick - state.animationTick());
    }

    private static float easeOut(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    @SubscribeEvent
    public static void renderStatus(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null
                || !(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof IronGolem golem)) return;
        GolemReinforcementClientState.State state = GolemReinforcementClientState.get(golem.getUUID());
        if (state == null || !state.ownerId().equals(minecraft.player.getUUID())) return;

        Component text = Component.translatable("hud.ability.obsidian_reinforcement.status",
                state.charge(), state.chargeThreshold(), state.shields(), state.maxShields());
        int y = event.getGuiGraphics().guiHeight() / 2 + 18;
        event.getGuiGraphics().drawCenteredString(minecraft.font, text,
                event.getGuiGraphics().guiWidth() / 2, y, 0xE8B94F);
    }

    @EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class LayerRegistration {
        private LayerRegistration() {
        }

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            if (event.getRenderer(EntityType.IRON_GOLEM) instanceof IronGolemRenderer renderer) {
                renderer.addLayer(new ObsidianReinforcementLayer(renderer));
            }
        }
    }
}
