package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class PigArmorLayer extends RenderLayer<Pig, PigModel<Pig>> {
    private static final ResourceLocation TEXTURE =
            AbilityMod.id("textures/entity/pig/iron_cavalry_armor.png");
    private static final ModelLayerLocation MODEL_LAYER =
            new ModelLayerLocation(AbilityMod.id("pig_armor"), "main");
    private final PigModel<Pig> armorModel;

    public PigArmorLayer(RenderLayerParent<Pig, PigModel<Pig>> parent,
            net.minecraft.client.model.geom.EntityModelSet modelSet) {
        super(parent);
        armorModel = new PigModel<>(modelSet.bakeLayer(MODEL_LAYER));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, Pig pig,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        if (!PigArmorPresentation.isActive(pig) || pig.isInvisible()) {
            return;
        }
        getParentModel().copyPropertiesTo(armorModel);
        armorModel.prepareMobModel(pig, limbSwing, limbSwingAmount, partialTick);
        armorModel.setupAnim(pig, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        armorModel.renderToBuffer(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY, -1);
    }

    @EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void registerLayerDefinition(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(MODEL_LAYER,
                    () -> PigModel.createBodyLayer(new CubeDeformation(0.22F)));
        }

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            if (event.getRenderer(EntityType.PIG) instanceof PigRenderer renderer) {
                renderer.addLayer(new PigArmorLayer(renderer, event.getEntityModels()));
            }
        }
    }
}
