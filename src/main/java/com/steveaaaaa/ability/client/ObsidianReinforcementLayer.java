package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

public final class ObsidianReinforcementLayer extends RenderLayer<IronGolem, IronGolemModel<IronGolem>> {
    public static final ResourceLocation TEXTURE =
            AbilityMod.id("textures/entity/iron_golem/obsidian_reinforcement.png");

    public ObsidianReinforcementLayer(RenderLayerParent<IronGolem, IronGolemModel<IronGolem>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, IronGolem golem,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        if (GolemReinforcementClientState.get(golem.getUUID()) == null
                || Minecraft.getInstance().getResourceManager().getResource(TEXTURE).isEmpty()) {
            return;
        }
        renderColoredCutoutModel(getParentModel(), TEXTURE, poseStack, buffer, packedLight, golem, -1);
    }
}
