package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientProgressCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ClientProgressEvents {
    private ClientProgressEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientProgressCache.clear();
    }
}
