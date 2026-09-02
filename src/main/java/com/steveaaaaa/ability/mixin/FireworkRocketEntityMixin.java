package com.steveaaaaa.ability.mixin;

import com.steveaaaaa.ability.ability.effect.DangerousChargeFirework;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin implements DangerousChargeFirework {
    @Unique
    private static final EntityDataAccessor<Boolean> ABILITY$DANGEROUS_CHARGE =
            SynchedEntityData.defineId(FireworkRocketEntity.class, EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void ability$defineDangerousChargeData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(ABILITY$DANGEROUS_CHARGE, false);
    }

    @Override
    public boolean ability$isDangerousCharge() {
        return ((FireworkRocketEntity) (Object) this).getEntityData().get(ABILITY$DANGEROUS_CHARGE);
    }

    @Override
    public void ability$setDangerousCharge(boolean dangerousCharge) {
        ((FireworkRocketEntity) (Object) this).getEntityData().set(ABILITY$DANGEROUS_CHARGE, dangerousCharge);
    }
}
