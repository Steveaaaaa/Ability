package com.steveaaaaa.ability.ability.effect;

import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID)
public final class CombatStatusEventHandler {
    private CombatStatusEventHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && CombatStatusTracker.isStunned(attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && CombatStatusTracker.isStunned(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity().level() instanceof ServerLevel)
                || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (living.tickCount % 10 == 0 && CombatStatusTracker.hasGlowingMark(living.getUUID())) {
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, true));
        }
        CombatStatusTracker.maintainStun(living);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityTickAfter(EntityTickEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel
                && event.getEntity() instanceof LivingEntity living) {
            CombatStatusTracker.maintainStun(living);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        CombatStatusTracker.forgetTarget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            CombatStatusTracker.forgetTarget(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        CombatStatusTracker.forgetOwner(event.getEntity().getUUID());
        CombatStatusTracker.forgetTarget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof LivingEntity living) {
            CombatStatusTracker.syncStunTo(player, living);
            CounterSniperEffect.syncMarkToOwner(player, living);
        }
    }
}
