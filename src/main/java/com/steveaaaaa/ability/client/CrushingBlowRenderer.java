package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientboundCrushingBlowPayload;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class CrushingBlowRenderer {
    private static final ResourceLocation PRESSURE = AbilityMod.id("textures/particle/crushing_blow_pressure.png");
    private static final Set<UUID> COMPRESSED = ConcurrentHashMap.newKeySet();
    private static final double TAU = Math.PI * 2.0D;

    private CrushingBlowRenderer() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void compressOnRelease(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        CrushingBlowClientState.State state = CrushingBlowClientState.get(golem.getUUID());
        if (state == null || state.visualEvent() != ClientboundCrushingBlowPayload.VisualEvent.RELEASED) return;
        float age = animationAge(golem, state, event.getPartialTick());
        if (age >= 8.0F) return;
        float wave = Mth.sin(Mth.clamp(age / 8.0F, 0.0F, 1.0F) * Mth.PI);
        event.getPoseStack().pushPose();
        event.getPoseStack().scale(1.0F + wave * 0.045F, 1.0F - wave * 0.095F, 1.0F + wave * 0.045F);
        COMPRESSED.add(golem.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        CrushingBlowClientState.State state = CrushingBlowClientState.get(golem.getUUID());
        if (state != null) {
            float age = animationAge(golem, state, event.getPartialTick());
            if (state.visualEvent() == ClientboundCrushingBlowPayload.VisualEvent.RELEASED && age < 20.0F) {
                renderRelease(event, age);
            }
        }
        if (COMPRESSED.remove(golem.getUUID())) event.getPoseStack().popPose();
    }

    private static void renderRelease(RenderLivingEvent.Post<?, ?> event, float age) {
        float progress = Mth.clamp(age / 15.0F, 0.0F, 1.0F);
        float leadRadius = easeOut(progress) * 7.5F;
        int leadAlpha = (int) ((1.0F - progress) * 245.0F);
        renderGroundRing(event, leadRadius, 64, 0.38F, leadAlpha, 0.08F);
        renderGroundRing(event, Math.max(0.0F, leadRadius - 0.58F), 56, 0.31F,
                (int) (leadAlpha * 0.72F), 0.11F);

        float secondProgress = Mth.clamp((age - 2.0F) / 15.0F, 0.0F, 1.0F);
        if (age >= 2.0F) {
            float secondRadius = easeOut(secondProgress) * 7.5F;
            renderGroundRing(event, secondRadius, 52, 0.3F,
                    (int) ((1.0F - secondProgress) * 190.0F), 0.055F);
        }

        for (int i = 0; i < 36; i++) {
            double angle = i * TAU / 36.0D;
            float steppedRadius = Math.round(leadRadius * 4.0F) / 4.0F;
            renderBillboard(event, PRESSURE, (float) Math.cos(angle) * steppedRadius,
                    0.16F + (i % 3) * 0.055F, (float) Math.sin(angle) * steppedRadius,
                    0.25F + (i % 4 == 0 ? 0.09F : 0.0F), leadAlpha, LightTexture.FULL_BRIGHT);
        }
        if (age >= 6.0F) {
            float returnProgress = Mth.clamp((age - 6.0F) / 14.0F, 0.0F, 1.0F);
            float returnRadius = (1.0F - easeOut(returnProgress)) * 7.5F;
            for (int i = 0; i < 12; i++) {
                double angle = i * TAU / 12.0D + 0.22D;
                renderBillboard(event, PRESSURE, (float) Math.cos(angle) * returnRadius,
                        0.2F + returnProgress * 1.15F, (float) Math.sin(angle) * returnRadius,
                        0.16F, (int) ((1.0F - returnProgress) * 220.0F), LightTexture.FULL_BRIGHT);
            }
        }
    }

    private static void renderGroundRing(RenderLivingEvent.Post<?, ?> event, float radius,
            int segments, float size, int alpha, float y) {
        if (radius <= 0.05F || alpha <= 0) return;
        for (int i = 0; i < segments; i++) {
            double angle = i * TAU / segments;
            float steppedRadius = Math.round((radius + (i & 1) * 0.07F) * 4.0F) / 4.0F;
            renderGroundTile(event, (float) Math.cos(angle) * steppedRadius, y,
                    (float) Math.sin(angle) * steppedRadius, size, alpha, (float) -angle);
        }
    }

    private static void renderGroundTile(RenderLivingEvent.Post<?, ?> event,
            float x, float y, float z, float size, int alpha, float rotation) {
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotation(rotation));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.scale(size, size, size);
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(PRESSURE));
        vertex(pose, consumer, -0.5F, -0.5F, 0.0F, 1.0F, alpha, LightTexture.FULL_BRIGHT);
        vertex(pose, consumer, 0.5F, -0.5F, 1.0F, 1.0F, alpha, LightTexture.FULL_BRIGHT);
        vertex(pose, consumer, 0.5F, 0.5F, 1.0F, 0.0F, alpha, LightTexture.FULL_BRIGHT);
        vertex(pose, consumer, -0.5F, 0.5F, 0.0F, 0.0F, alpha, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void renderBillboard(RenderLivingEvent.Post<?, ?> event, ResourceLocation texture,
            float x, float y, float z, float size, int alpha, int packedLight) {
        if (alpha <= 0) return;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.scale(size, size, size);
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(texture));
        vertex(pose, consumer, -0.5F, -0.5F, 0.0F, 1.0F, alpha, packedLight);
        vertex(pose, consumer, 0.5F, -0.5F, 1.0F, 1.0F, alpha, packedLight);
        vertex(pose, consumer, 0.5F, 0.5F, 1.0F, 0.0F, alpha, packedLight);
        vertex(pose, consumer, -0.5F, 0.5F, 0.0F, 0.0F, alpha, packedLight);
        pose.popPose();
    }

    private static void vertex(PoseStack pose, VertexConsumer consumer, float x, float y,
            float u, float v, int alpha, int packedLight) {
        consumer.addVertex(pose.last(), x, y, 0.0F)
                .setColor(238, 242, 248, Mth.clamp(alpha, 0, 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose.last(), 0.0F, 0.0F, 1.0F);
    }

    private static float animationAge(IronGolem golem, CrushingBlowClientState.State state, float partialTick) {
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
        CrushingBlowClientState.State state = CrushingBlowClientState.get(golem.getUUID());
        if (state == null || !state.ownerId().equals(minecraft.player.getUUID())) return;
        Component text = Component.translatable("hud.ability.crushing_blow.status",
                state.charge(), state.chargeThreshold(), state.damagePercent());
        int y = event.getGuiGraphics().guiHeight() / 2 +
                (GolemReinforcementClientState.get(golem.getUUID()) == null ? 18 : 29);
        event.getGuiGraphics().drawCenteredString(minecraft.font, text,
                event.getGuiGraphics().guiWidth() / 2, y, 0xD8DDE5);
    }

    @EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class LayerRegistration {
        private LayerRegistration() {
        }

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            if (event.getRenderer(EntityType.IRON_GOLEM) instanceof IronGolemRenderer renderer) {
                renderer.addLayer(new CrushingBlowLayer(renderer));
            }
        }
    }
}
