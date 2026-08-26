package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ObsidianReinforcementRenderer {
    public static final ResourceLocation SHIELD_TEXTURE =
            AbilityMod.id("textures/entity/iron_golem/obsidian_shield.png");
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/particle/bubble.png");
    private static final int SEGMENTS = 24;
    private static final int RINGS = 12;
    private static final float BASE_RADIUS = 1.68F;
    private static final float CENTER_Y = 1.38F;

    private ObsidianReinforcementRenderer() {
    }

    @SubscribeEvent
    public static void renderShieldBack(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        GolemReinforcementClientState.State state = GolemReinforcementClientState.get(golem.getUUID());
        if (state == null || state.shields() <= 0) return;
        renderSphereHalf(event, golem, state, false);
    }

    @SubscribeEvent
    public static void renderShieldFront(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        GolemReinforcementClientState.State state = GolemReinforcementClientState.get(golem.getUUID());
        if (state == null) return;
        if (state.shields() > 0) renderSphereHalf(event, golem, state, true);

        float animationAge = animationAge(golem, state, event.getPartialTick());
        if (state.visualEvent() == ClientboundGolemReinforcementPayload.VisualEvent.SHIELD_BLOCKED
                && animationAge < 10.0F) {
            renderImpactBubble(event, state, animationAge);
        }
    }

    private static void renderSphereHalf(RenderLivingEvent<?, ?> event, IronGolem golem,
            GolemReinforcementClientState.State state, boolean front) {
        float time = golem.tickCount + event.getPartialTick();
        float animationAge = animationAge(golem, state, event.getPartialTick());
        Appearance appearance = appearance(state, time, animationAge);
        Vector3f look = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
        Vector3f left = Minecraft.getInstance().gameRenderer.getMainCamera().getLeftVector();
        Vector3f up = Minecraft.getInstance().gameRenderer.getMainCamera().getUpVector();

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0D, CENTER_Y, 0.0D);
        pose.scale(appearance.radius(), appearance.radius(), appearance.radius());
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(texture()));
        for (int ring = 0; ring < RINGS; ring++) {
            float lat0 = -Mth.HALF_PI + Mth.PI * ring / RINGS;
            float lat1 = -Mth.HALF_PI + Mth.PI * (ring + 1) / RINGS;
            float centerLat = (lat0 + lat1) * 0.5F;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                float lon0 = Mth.TWO_PI * segment / SEGMENTS;
                float lon1 = Mth.TWO_PI * (segment + 1) / SEGMENTS;
                float centerLon = (lon0 + lon1) * 0.5F;
                float centerCos = Mth.cos(centerLat);
                float facing = centerCos * Mth.cos(centerLon) * look.x
                        + Mth.sin(centerLat) * look.y
                        + centerCos * Mth.sin(centerLon) * look.z;
                if (front != (facing < 0.0F)) continue;

                sphereVertex(pose, consumer, lat0, lon0, left, up, appearance, event.getPackedLight());
                sphereVertex(pose, consumer, lat1, lon0, left, up, appearance, event.getPackedLight());
                sphereVertex(pose, consumer, lat1, lon1, left, up, appearance, event.getPackedLight());
                sphereVertex(pose, consumer, lat0, lon1, left, up, appearance, event.getPackedLight());
            }
        }
        pose.popPose();
    }

    private static void sphereVertex(PoseStack pose, VertexConsumer consumer, float latitude, float longitude,
            Vector3f left, Vector3f up, Appearance appearance, int packedLight) {
        float cosLat = Mth.cos(latitude);
        float nx = cosLat * Mth.cos(longitude);
        float ny = Mth.sin(latitude);
        float nz = cosLat * Mth.sin(longitude);
        float u = 0.5F + (nx * left.x + ny * left.y + nz * left.z) * 0.5F;
        float v = 0.5F - (nx * up.x + ny * up.y + nz * up.z) * 0.5F;
        consumer.addVertex(pose.last(), nx, ny, nz)
                .setColor(176, 153, 199, appearance.alpha())
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose.last(), nx, ny, nz);
    }

    private static Appearance appearance(GolemReinforcementClientState.State state, float time, float animationAge) {
        float progressScale = 1.0F;
        float animationAlpha = 1.0F;
        if (state.visualEvent() == ClientboundGolemReinforcementPayload.VisualEvent.ACTIVATED
                && animationAge < 14.0F) {
            float progress = Mth.clamp(animationAge / 14.0F, 0.0F, 1.0F);
            progressScale = 0.68F + easeOut(progress) * 0.32F;
            animationAlpha = progress;
        } else if (state.visualEvent() == ClientboundGolemReinforcementPayload.VisualEvent.SHIELD_GAINED
                && animationAge < 12.0F) {
            float progress = Mth.clamp(animationAge / 12.0F, 0.0F, 1.0F);
            progressScale = 1.0F + Mth.sin(progress * Mth.PI) * 0.09F;
        }
        float breathing = 1.0F + Mth.sin(time * 0.085F) * 0.018F;
        float layerStrength = state.shields() / (float) Math.max(1, state.maxShields());
        float shimmer = 0.88F + Mth.sin(time * 0.12F) * 0.12F;
        int alpha = Mth.clamp((int) ((150.0F + layerStrength * 65.0F) * shimmer * animationAlpha), 0, 235);
        return new Appearance(BASE_RADIUS * breathing * progressScale, alpha);
    }

    private static void renderImpactBubble(RenderLivingEvent.Post<?, ?> event,
            GolemReinforcementClientState.State state, float animationAge) {
        float progress = Mth.clamp(animationAge / 10.0F, 0.0F, 1.0F);
        Vec3 normal = new Vec3(state.impactX(), state.impactY(), state.impactZ());
        if (normal.lengthSqr() < 1.0E-6D) normal = new Vec3(0.0D, 0.0D, 1.0D);
        normal = normal.normalize();
        Vec3 referenceUp = Math.abs(normal.y) > 0.92D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 tangent = referenceUp.cross(normal).normalize();
        Vec3 bitangent = normal.cross(tangent).normalize();
        Vec3 center = new Vec3(normal.x * BASE_RADIUS, CENTER_Y + normal.y * BASE_RADIUS,
                normal.z * BASE_RADIUS);
        float size = 0.22F + easeOut(progress) * 0.62F;
        int alpha = (int) ((1.0F - progress) * 245.0F);

        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(texture()));
        impactVertex(event, consumer, center.add(tangent.scale(-size)).add(bitangent.scale(-size)),
                0.0F, 1.0F, normal, alpha);
        impactVertex(event, consumer, center.add(tangent.scale(size)).add(bitangent.scale(-size)),
                1.0F, 1.0F, normal, alpha);
        impactVertex(event, consumer, center.add(tangent.scale(size)).add(bitangent.scale(size)),
                1.0F, 0.0F, normal, alpha);
        impactVertex(event, consumer, center.add(tangent.scale(-size)).add(bitangent.scale(size)),
                0.0F, 0.0F, normal, alpha);
    }

    private static void impactVertex(RenderLivingEvent.Post<?, ?> event, VertexConsumer consumer, Vec3 point,
            float u, float v, Vec3 normal, int alpha) {
        consumer.addVertex(event.getPoseStack().last(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(226, 210, 239, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(event.getPackedLight())
                .setNormal(event.getPoseStack().last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static ResourceLocation texture() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getResourceManager().getResource(SHIELD_TEXTURE).isPresent()
                ? SHIELD_TEXTURE : FALLBACK_TEXTURE;
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

    private record Appearance(float radius, int alpha) {
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
