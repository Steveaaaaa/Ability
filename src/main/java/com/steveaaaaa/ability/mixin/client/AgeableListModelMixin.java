package com.steveaaaaa.ability.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.client.DodgeAnimationEvents;
import net.minecraft.client.model.AgeableListModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AgeableListModel.class)
public abstract class AgeableListModelMixin {
    @Inject(method = "renderToBuffer", at = @At("HEAD"), cancellable = true)
    private void ability$renderArticulatedDodgeModel(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color,
            CallbackInfo callback
    ) {
        if (DodgeAnimationEvents.renderArticulatedModel(
                (AgeableListModel<?>) (Object) this,
                poseStack,
                consumer,
                packedLight,
                packedOverlay,
                color
        )) {
            callback.cancel();
        }
    }
}
