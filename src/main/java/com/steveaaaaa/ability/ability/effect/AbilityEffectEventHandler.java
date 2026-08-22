package com.steveaaaaa.ability.ability.effect;

import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

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
}
