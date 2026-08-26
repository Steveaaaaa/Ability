package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientColdCurrentQueue;
import com.steveaaaaa.ability.network.ClientboundColdCurrentPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ColdCurrentClientState {
    private static final Map<UUID, State> STATES = new HashMap<>();

    private ColdCurrentClientState() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        ClientboundColdCurrentPayload payload;
        while ((payload = ClientColdCurrentQueue.poll()) != null) {
            STATES.put(payload.golemId(), new State(
                    payload.ownerId(), payload.ageTicks(), payload.finalThresholdTicks(), payload.stage(),
                    minecraft.level.getGameTime()
            ));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static State get(UUID golemId) {
        return STATES.get(golemId);
    }

    private static void clear() {
        STATES.clear();
        ClientColdCurrentQueue.clear();
    }

    public record State(UUID ownerId, int ageAtSync, int finalThresholdTicks, int stage, long syncGameTime) {
        public int estimatedAge(long gameTime) {
            return Math.min(finalThresholdTicks, ageAtSync + (int) Math.max(0L, gameTime - syncGameTime));
        }
    }
}
