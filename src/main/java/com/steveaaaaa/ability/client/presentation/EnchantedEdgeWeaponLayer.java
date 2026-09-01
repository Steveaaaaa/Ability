package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.effect.EnchantedEdgeEffect;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class EnchantedEdgeWeaponLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private EnchantedEdgeWeaponLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch) {
        if (!EnchantedEdgeWeaponRenderer.isActive(player)) return;
        renderHand(player, player.getMainHandItem(), player.getMainArm(), poseStack, buffer, partialTick);
        renderHand(player, player.getOffhandItem(), player.getMainArm().getOpposite(), poseStack, buffer, partialTick);
    }

    private void renderHand(AbstractClientPlayer player, ItemStack stack, HumanoidArm arm,
            PoseStack poseStack, MultiBufferSource buffer, float partialTick) {
        if (!EnchantedEdgeEffect.isWeapon(stack)) return;
        boolean leftHand = arm == HumanoidArm.LEFT;
        poseStack.pushPose();
        getParentModel().translateToHand(arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate((leftHand ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
        EnchantedEdgeWeaponRenderer.applyItemModelTransform(poseStack, player, stack,
                leftHand ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                leftHand);
        EnchantedEdgeWeaponRenderer.renderOrbit(poseStack, buffer, partialTick, 0.9F);
        poseStack.popPose();
    }

    @EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                var renderer = event.getSkin(skin);
                if (renderer instanceof PlayerRenderer playerRenderer) {
                    playerRenderer.addLayer(new EnchantedEdgeWeaponLayer(playerRenderer));
                }
            }
        }
    }
}
