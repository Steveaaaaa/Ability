package com.steveaaaaa.ability.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steveaaaaa.ability.client.DodgeAnimationEvents;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void ability$applyDodgeRotation(
            LivingEntity entity,
            PoseStack poseStack,
            float ageInTicks,
            float bodyYaw,
            float partialTick,
            float scale,
            CallbackInfo callback
    ) {
        if (entity instanceof AbstractClientPlayer player) {
            DodgeAnimationEvents.transformPlayer(player, poseStack, partialTick);
        }
    }
}
