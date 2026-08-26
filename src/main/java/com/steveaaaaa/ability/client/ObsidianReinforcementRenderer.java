package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ObsidianReinforcementRenderer {
    private static final ItemStack OBSIDIAN = new ItemStack(Items.OBSIDIAN);

    private ObsidianReinforcementRenderer() {
    }

    @SubscribeEvent
    public static void renderFragments(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        GolemReinforcementClientState.State state = GolemReinforcementClientState.get(golem.getUUID());
        if (state == null || state.shields() <= 0) return;

        float time = golem.tickCount + event.getPartialTick();
        for (int i = 0; i < state.shields(); i++) {
            float angle = time * 1.8F + i * (360.0F / Math.max(1, state.shields()));
            double radians = angle * Math.PI / 180.0D;
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(Math.cos(radians) * 0.82D, 1.15D + Math.sin(time * 0.08F + i) * 0.08D,
                    Math.sin(radians) * 0.82D);
            pose.mulPose(Axis.YP.rotationDegrees(angle + 45.0F));
            pose.mulPose(Axis.XP.rotationDegrees(28.0F));
            pose.scale(0.22F, 0.22F, 0.22F);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    OBSIDIAN, ItemDisplayContext.FIXED, event.getPackedLight(), OverlayTexture.NO_OVERLAY,
                    pose, event.getMultiBufferSource(), golem.level(), golem.getId() + i
            );
            pose.popPose();
        }
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
