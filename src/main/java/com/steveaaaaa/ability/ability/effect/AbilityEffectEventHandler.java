package com.steveaaaaa.ability.ability.effect;

import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID)
public final class AbilityEffectEventHandler {
    private AbilityEffectEventHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.getBreaker() instanceof ServerPlayer player) {
            BlockDropEffectTypeRegistry.process(event, player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageModifierEffect.process(event);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            AttributeModifierEffect.reconcile(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttributeModifierEffect.forget(player);
        }
    }
}
