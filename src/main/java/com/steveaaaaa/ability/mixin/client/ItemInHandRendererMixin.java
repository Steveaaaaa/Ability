package com.steveaaaaa.ability.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steveaaaaa.ability.client.presentation.EnchantedEdgeWeaponRenderer;
import com.steveaaaaa.ability.client.presentation.BlastExcavationTntRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), remap = false)
    private void ability$beginHeldItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext,
            boolean leftHand, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo callback) {
        EnchantedEdgeWeaponRenderer.beginHeldItem(entity, stack, displayContext);
        BlastExcavationTntRenderer.beginHeldItem(entity, stack, displayContext);
    }

    @Inject(method = "renderItem", at = @At("RETURN"), remap = false)
    private void ability$endHeldItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext,
            boolean leftHand, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo callback) {
        EnchantedEdgeWeaponRenderer.endHeldItem();
        BlastExcavationTntRenderer.endHeldItem();
    }
}
