package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientboundCrushingBlowPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

public final class CrushingBlowLayer extends RenderLayer<IronGolem, IronGolemModel<IronGolem>> {
    public static final ResourceLocation TEXTURE =
            AbilityMod.id("textures/entity/iron_golem/crushing_blow.png");
    private static final ResourceLocation[] CHARGE_TEXTURES = {
            AbilityMod.id("textures/entity/iron_golem/crushing_blow_charge_1.png"),
            AbilityMod.id("textures/entity/iron_golem/crushing_blow_charge_2.png"),
            AbilityMod.id("textures/entity/iron_golem/crushing_blow_charge_3.png"),
            AbilityMod.id("textures/entity/iron_golem/crushing_blow_charge_4.png"),
            AbilityMod.id("textures/entity/iron_golem/crushing_blow_charge_5.png"),
            AbilityMod.id("textures/entity/iron_golem/crushing_blow_charge_6.png")
    };

    public CrushingBlowLayer(RenderLayerParent<IronGolem, IronGolemModel<IronGolem>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, IronGolem golem,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        CrushingBlowClientState.State state = CrushingBlowClientState.get(golem.getUUID());
        if (state == null) return;
        if (GolemReinforcementClientState.get(golem.getUUID()) == null
                && Minecraft.getInstance().getResourceManager().getResource(TEXTURE).isPresent()) {
            renderColoredCutoutModel(getParentModel(), TEXTURE, poseStack, buffer, packedLight, golem, -1);
        }

        float animationAge = Math.max(0.0F,
                golem.level().getGameTime() + partialTick - state.animationTick());
        boolean releaseFlash = state.visualEvent() == ClientboundCrushingBlowPayload.VisualEvent.RELEASED
                && animationAge < 8.0F;
        int litLayers = releaseFlash ? CHARGE_TEXTURES.length
                : (int) Math.ceil(state.charge() * CHARGE_TEXTURES.length
                        / (double) Math.max(1, state.chargeThreshold()));
        for (int i = 0; i < Math.min(litLayers, CHARGE_TEXTURES.length); i++) {
            ResourceLocation texture = CHARGE_TEXTURES[i];
            if (Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty()) continue;
            boolean newestPulse = state.visualEvent() == ClientboundCrushingBlowPayload.VisualEvent.CHARGED
                    && animationAge < 7.0F && i == litLayers - 1;
            renderColoredCutoutModel(getParentModel(), texture, poseStack, buffer,
                    releaseFlash || newestPulse ? LightTexture.FULL_BRIGHT : packedLight, golem, -1);
        }
    }
}
