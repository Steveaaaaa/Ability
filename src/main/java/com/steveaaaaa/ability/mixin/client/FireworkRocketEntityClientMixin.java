package com.steveaaaaa.ability.mixin.client;

import com.steveaaaaa.ability.ability.effect.DangerousChargeFirework;
import com.steveaaaaa.ability.client.presentation.DangerousChargeExplosionPresentation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityClientMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void ability$replaceDangerousChargeExplosion(byte eventId, CallbackInfo ci) {
        FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;
        if (eventId != 17
                || !(rocket instanceof DangerousChargeFirework dangerousCharge)
                || !dangerousCharge.ability$isDangerousCharge()
                || !(rocket.level() instanceof ClientLevel level)) {
            return;
        }
        DangerousChargeExplosionPresentation.spawn(level, rocket.position(), rocket.getUUID().getLeastSignificantBits());
        ci.cancel();
    }
}
