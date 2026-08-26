package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
    private static final ResourceLocation ANVIL = AbilityMod.id("textures/particle/crushing_blow_anvil.png");
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
            renderChestDisplay(event, golem, state, age);
            if (state.visualEvent() == ClientboundCrushingBlowPayload.VisualEvent.CHARGED && age < 9.0F) {
                renderContractingPressure(event, golem, age);
            }
            if (state.visualEvent() == ClientboundCrushingBlowPayload.VisualEvent.RELEASED && age < 20.0F) {
                renderRelease(event, age);
            }
        }
        if (COMPRESSED.remove(golem.getUUID())) event.getPoseStack().popPose();
    }

    private static void renderChestDisplay(RenderLivingEvent.Post<?, ?> event, IronGolem golem,
            CrushingBlowClientState.State state, float age) {
        float time = golem.tickCount + event.getPartialTick();
        float progress = state.charge() / (float) Math.max(1, state.chargeThreshold());
        float pulse = 0.95F + Mth.sin(time * (0.09F + progress * 0.08F)) * (0.025F + progress * 0.045F);
        int alpha = 110 + (int) (progress * 125.0F);
        float size = 0.42F * pulse;
        if (state.visualEvent() == ClientboundCrushingBlowPayload.VisualEvent.RELEASED && age < 8.0F) {
            float flash = 1.0F - age / 8.0F;
            size += flash * 0.28F;
            alpha = 255;
        }
        float yaw = Mth.rotLerp(event.getPartialTick(), golem.yBodyRotO, golem.yBodyRot) * Mth.DEG_TO_RAD;
        float frontX = -Mth.sin(yaw) * 0.58F;
        float frontZ = Mth.cos(yaw) * 0.58F;
        renderBillboard(event, ANVIL, frontX, 1.52F, frontZ, size, alpha, LightTexture.FULL_BRIGHT);

        int threshold = Math.max(1, state.chargeThreshold());
        float spacing = Math.min(0.15F, 0.76F / threshold);
        float start = -(threshold - 1) * spacing * 0.5F;
        for (int i = 0; i < threshold; i++) {
            boolean filled = i < state.charge();
            renderBillboard(event, PRESSURE, frontX + start + i * spacing, 1.19F, frontZ,
                    filled ? 0.105F : 0.075F, filled ? 235 : 58,
                    filled ? LightTexture.FULL_BRIGHT : event.getPackedLight());
        }
    }

    private static void renderContractingPressure(RenderLivingEvent.Post<?, ?> event, IronGolem golem, float age) {
        float progress = easeOut(Mth.clamp(age / 9.0F, 0.0F, 1.0F));
        float radius = Mth.lerp(progress, 1.65F, 0.25F);
        float time = golem.tickCount + event.getPartialTick();
        for (int i = 0; i < 6; i++) {
            double angle = i * TAU / 6.0D + time * 0.015D;
            renderBillboard(event, PRESSURE, (float) Math.cos(angle) * radius,
                    1.42F + (float) Math.sin(angle * 2.0D) * 0.22F,
                    (float) Math.sin(angle) * radius, 0.18F, (int) ((1.0F - progress) * 210.0F),
                    LightTexture.FULL_BRIGHT);
        }
    }

    private static void renderRelease(RenderLivingEvent.Post<?, ?> event, float age) {
        float progress = Mth.clamp(age / 16.0F, 0.0F, 1.0F);
        float radius = easeOut(progress) * 7.5F;
        int alpha = (int) ((1.0F - progress) * 205.0F);
        for (int i = 0; i < 40; i++) {
            double angle = i * TAU / 40.0D;
            float steppedRadius = Math.round((radius + (i % 3) * 0.08F) * 4.0F) / 4.0F;
            renderBillboard(event, PRESSURE, (float) Math.cos(angle) * steppedRadius,
                    0.12F + (i & 1) * 0.05F, (float) Math.sin(angle) * steppedRadius,
                    0.2F + (i % 4 == 0 ? 0.08F : 0.0F), alpha, event.getPackedLight());
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
