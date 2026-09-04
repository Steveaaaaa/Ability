package com.steveaaaaa.ability.mixin;

import com.steveaaaaa.ability.ability.effect.DodgeEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerDimensionsMixin {
    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void ability$useRollDimensions(
            Pose pose,
            CallbackInfoReturnable<EntityDimensions> callback
    ) {
        if ((Object) this instanceof ServerPlayer player && DodgeEffect.isRolling(player)) {
            callback.setReturnValue(DodgeEffect.ROLL_DIMENSIONS);
        }
    }
}
