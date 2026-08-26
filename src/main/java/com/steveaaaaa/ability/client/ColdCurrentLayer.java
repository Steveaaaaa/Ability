package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.client.model.SnowGolemModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.SnowGolem;

public final class ColdCurrentLayer extends RenderLayer<SnowGolem, SnowGolemModel<SnowGolem>> {
    public static final ResourceLocation TEXTURE =
            AbilityMod.id("textures/entity/snow_golem/cold_current.png");

    public ColdCurrentLayer(RenderLayerParent<SnowGolem, SnowGolemModel<SnowGolem>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, SnowGolem golem,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        if (ColdCurrentClientState.get(golem.getUUID()) != null) {
            renderColoredCutoutModel(getParentModel(), TEXTURE, poseStack, buffer, packedLight, golem, -1);
        }
    }
}
