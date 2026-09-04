package com.steveaaaaa.ability.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steveaaaaa.ability.client.DodgeAnimationEvents;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void ability$applyDodgePose(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo callback
    ) {
        if (entity instanceof AbstractClientPlayer player) {
            float partialTick = Math.clamp(ageInTicks - entity.tickCount, 0.0F, 1.0F);
            DodgeAnimationEvents.posePlayer(player, (PlayerModel<?>) (Object) this, partialTick);
        }
    }

    @Inject(method = "translateToHand", at = @At("HEAD"), cancellable = true)
    private void ability$translateDodgeHand(
            HumanoidArm arm,
            PoseStack poseStack,
            CallbackInfo callback
    ) {
        if (DodgeAnimationEvents.translateArticulatedHand(arm, poseStack)) {
            callback.cancel();
        }
    }
}
