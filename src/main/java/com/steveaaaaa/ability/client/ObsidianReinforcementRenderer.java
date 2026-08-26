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
            ResourceLocation.withDefaultNamespace("textures/particle/bubble.png");

    private ObsidianReinforcementRenderer() {
    }

    @SubscribeEvent
    public static void renderShield(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        GolemReinforcementClientState.State state = GolemReinforcementClientState.get(golem.getUUID());
        if (state == null) return;

        float animationAge = animationAge(golem, state, event.getPartialTick());
        boolean recentBlock = state.visualEvent() == ClientboundGolemReinforcementPayload.VisualEvent.SHIELD_BLOCKED
                && animationAge < 10.0F;
        if (state.shields() > 0) renderBubbleShell(event, state, golem.tickCount + event.getPartialTick(), animationAge);
        if (recentBlock) renderImpactBubble(event, state, animationAge);
    }

    private static void renderBubbleShell(RenderLivingEvent.Post<?, ?> event,
            GolemReinforcementClientState.State state, float time, float animationAge) {
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
        int alpha = (int) ((150.0F + layerStrength * 65.0F) * shimmer * animationAlpha);
        renderBubble(event, 0.0F, 1.38F, 0.0F, 1.68F * breathing * progressScale,
                176, 153, 199, Mth.clamp(alpha, 0, 235));
    }

    private static void renderImpactBubble(RenderLivingEvent.Post<?, ?> event,
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
        float x = dx * 0.92F;
        float y = 1.38F + Mth.clamp(state.impactY(), -0.7F, 0.7F) * 0.9F;
        float z = dz * 0.72F;
        float size = 0.22F + easeOut(progress) * 0.62F;
        int alpha = (int) ((1.0F - progress) * 245.0F);
        renderBubble(event, x, y, z, size, 226, 210, 239, alpha);
    }

    private static void renderBubble(RenderLivingEvent.Post<?, ?> event, float x, float y, float z,
            float radius, int red, int green, int blue, int alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation texture = minecraft.getResourceManager().getResource(SHIELD_TEXTURE).isPresent()
                ? SHIELD_TEXTURE : FALLBACK_TEXTURE;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(radius, radius, radius);
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(texture));
        quadVertex(pose, consumer, -1.0F, -1.0F, 0.0F, 1.0F, red, green, blue, alpha, event.getPackedLight());
        quadVertex(pose, consumer, 1.0F, -1.0F, 1.0F, 1.0F, red, green, blue, alpha, event.getPackedLight());
        quadVertex(pose, consumer, 1.0F, 1.0F, 1.0F, 0.0F, red, green, blue, alpha, event.getPackedLight());
        quadVertex(pose, consumer, -1.0F, 1.0F, 0.0F, 0.0F, red, green, blue, alpha, event.getPackedLight());
        pose.popPose();
    }

    private static void quadVertex(PoseStack pose, VertexConsumer consumer, float x, float y, float u, float v,
            int red, int green, int blue, int alpha, int packedLight) {
        consumer.addVertex(pose.last(), x, y, 0.0F)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose.last(), 0.0F, 0.0F, 1.0F);
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
