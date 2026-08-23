package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.effect.AttributeModifierEffect;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID)
public final class PlayerProgressSyncEvents {
    private PlayerProgressSyncEvents() {
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player -> {
            AttributeModifierEffect.reconcile(player);
            PlayerProgressSynchronizer.send(player);
        });
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttributeModifierEffect.reconcile(player);
            PlayerProgressSynchronizer.send(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttributeModifierEffect.reconcile(player);
            PlayerProgressSynchronizer.send(player);
        }
    }
}
