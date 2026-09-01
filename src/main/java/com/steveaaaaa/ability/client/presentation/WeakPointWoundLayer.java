package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class WeakPointWoundLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private WeakPointWoundLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T target,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        WeakPointMarkRenderer.renderWounds(getParentModel(), poseStack, buffer, packedLight, target, partialTick);
    }

    @EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (EntityType<?> type : event.getEntityTypes()) {
                attach(event.getRenderer(type));
            }
            for (var skin : event.getSkins()) {
                attach(event.getSkin(skin));
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void attach(EntityRenderer<?> renderer) {
            if (renderer instanceof LivingEntityRenderer livingRenderer) {
                livingRenderer.addLayer(new WeakPointWoundLayer(livingRenderer));
            }
        }
    }
}
