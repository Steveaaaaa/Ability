package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.network.ClientGolemReinforcementQueue;
import com.steveaaaaa.ability.network.ClientboundGolemReinforcementPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import com.steveaaaaa.ability.AbilityMod;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class GolemReinforcementClientState {
    private static final Map<UUID, State> STATES = new HashMap<>();

    private GolemReinforcementClientState() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        ClientboundGolemReinforcementPayload payload;
        while ((payload = ClientGolemReinforcementQueue.poll()) != null) {
            long tick = minecraft.level.getGameTime();
            State previous = STATES.get(payload.golemId());
            long animationTick = payload.visualEvent() == ClientboundGolemReinforcementPayload.VisualEvent.SYNC
                    && previous != null ? previous.animationTick : tick;
            ClientboundGolemReinforcementPayload.VisualEvent visualEvent =
                    payload.visualEvent() == ClientboundGolemReinforcementPayload.VisualEvent.SYNC && previous != null
                            ? previous.visualEvent : payload.visualEvent();
            boolean preserveAnimation = payload.visualEvent() == ClientboundGolemReinforcementPayload.VisualEvent.SYNC
                    && previous != null;
            STATES.put(payload.golemId(), new State(
                    payload.ownerId(), payload.charge(), payload.chargeThreshold(),
                    payload.shields(), payload.maxShields(), visualEvent, animationTick,
                    preserveAnimation ? previous.impactX : payload.impactX(),
                    preserveAnimation ? previous.impactY : payload.impactY(),
                    preserveAnimation ? previous.impactZ : payload.impactZ()
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
        ClientGolemReinforcementQueue.clear();
    }

    public record State(
            UUID ownerId,
            int charge,
            int chargeThreshold,
            int shields,
            int maxShields,
            ClientboundGolemReinforcementPayload.VisualEvent visualEvent,
            long animationTick,
            float impactX,
            float impactY,
            float impactZ
    ) {
    }
}
