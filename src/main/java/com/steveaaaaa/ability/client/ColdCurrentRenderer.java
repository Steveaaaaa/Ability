package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.SnowGolemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ColdCurrentRenderer {
    private static final ResourceLocation CRYSTAL = AbilityMod.id("textures/particle/cold_current_crystal.png");
    private static final ResourceLocation SNOWFLAKE = AbilityMod.id("textures/particle/cold_current_snowflake.png");
    private static final double TAU = Math.PI * 2.0D;

    private ColdCurrentRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof SnowGolem golem)) return;
        ColdCurrentClientState.State state = ColdCurrentClientState.get(golem.getUUID());
        if (state == null) return;
        float time = golem.tickCount + event.getPartialTick();
        int light = state.stage() >= 4 ? LightTexture.FULL_BRIGHT : event.getPackedLight();

        if (state.stage() >= 2) {
            float pulse = state.stage() >= 4 ? 0.92F + Mth.sin(time * 0.16F) * 0.08F : 1.0F;
            renderAttachedBillboard(event, golem, CRYSTAL, -0.55F,
                    1.22F + Mth.sin(time * 0.08F) * 0.035F, 0.0F,
                    0.42F * pulse, 220, light);
            renderAttachedBillboard(event, golem, CRYSTAL, 0.55F,
                    1.22F + Mth.sin(time * 0.08F + 2.1F) * 0.035F, 0.0F,
                    0.42F * pulse, 220, light);
        }
        if (state.stage() >= 3) {
            for (int i = 0; i < 8; i++) {
                double angle = time * 0.018D + i * TAU / 8.0D;
                renderBillboard(event, SNOWFLAKE, (float) (Math.cos(angle) * 0.72D),
                        0.08F + Mth.sin(time * 0.07F + i) * 0.025F,
                        (float) (Math.sin(angle) * 0.72D), 0.2F, 82, light);
            }
        }
        if (state.stage() >= 4) {
            renderBillboard(event, SNOWFLAKE, 0.0F, 1.95F + Mth.sin(time * 0.11F) * 0.04F,
                    0.0F, 0.27F, 205, LightTexture.FULL_BRIGHT);
        }
    }

    private static void renderAttachedBillboard(RenderLivingEvent.Post<?, ?> event, SnowGolem golem,
            ResourceLocation texture, float localX, float y, float localZ, float size, int alpha, int packedLight) {
        float yaw = -Mth.rotLerp(event.getPartialTick(), golem.yBodyRotO, golem.yBodyRot) * Mth.DEG_TO_RAD;
        float cos = Mth.cos(yaw);
        float sin = Mth.sin(yaw);
        renderBillboard(event, texture, localX * cos - localZ * sin, y, localX * sin + localZ * cos,
                size, alpha, packedLight);
    }

    private static void renderBillboard(RenderLivingEvent.Post<?, ?> event, ResourceLocation texture,
            float x, float y, float z, float size, int alpha, int packedLight) {
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
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose.last(), 0.0F, 0.0F, 1.0F);
    }

    @SubscribeEvent
    public static void renderStatus(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.level == null
                || !(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof SnowGolem golem)) return;
        ColdCurrentClientState.State state = ColdCurrentClientState.get(golem.getUUID());
        if (state == null || !state.ownerId().equals(minecraft.player.getUUID())) return;

        int ageSeconds = state.estimatedAge(minecraft.level.getGameTime()) / 20;
        int finalSeconds = state.finalThresholdTicks() / 20;
        Component text = Component.translatable("hud.ability.cold_current.status",
                ageSeconds, finalSeconds, state.stage());
        event.getGuiGraphics().drawCenteredString(minecraft.font, text,
                event.getGuiGraphics().guiWidth() / 2, event.getGuiGraphics().guiHeight() / 2 + 18, 0xA9EFFF);
    }

    @EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class LayerRegistration {
        private LayerRegistration() {
        }

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            if (event.getRenderer(EntityType.SNOW_GOLEM) instanceof SnowGolemRenderer renderer) {
                renderer.addLayer(new ColdCurrentLayer(renderer));
            }
        }
    }
}
