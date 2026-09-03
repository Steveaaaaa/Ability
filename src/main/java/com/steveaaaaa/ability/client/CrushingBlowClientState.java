package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientCrushingBlowQueue;
import com.steveaaaaa.ability.network.ClientboundCrushingBlowPayload;
import com.steveaaaaa.ability.client.presentation.CrushingBlowGroundRenderer;
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
public final class CrushingBlowClientState {
    private static final Map<UUID, State> STATES = new HashMap<>();

    private CrushingBlowClientState() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        ClientboundCrushingBlowPayload payload;
        while ((payload = ClientCrushingBlowQueue.poll()) != null) {
            State previous = STATES.get(payload.golemId());
            boolean preserve = payload.visualEvent() == ClientboundCrushingBlowPayload.VisualEvent.SYNC
                    && previous != null;
            long animationTick = preserve ? previous.animationTick() : minecraft.level.getGameTime();
            if (!preserve && payload.visualEvent() == ClientboundCrushingBlowPayload.VisualEvent.WINDUP) {
                animationTick -= Math.max(0, payload.releaseTicks() - 1);
            } else if (!preserve && payload.visualEvent() == ClientboundCrushingBlowPayload.VisualEvent.RELEASED) {
                animationTick -= Math.max(0, payload.releaseTicks() - payload.impactTick());
                CrushingBlowGroundRenderer.accept(minecraft.level,
                        payload.impactX(), payload.impactY(), payload.impactZ());
            }
            STATES.put(payload.golemId(), new State(
                    payload.ownerId(), payload.charge(), payload.chargeThreshold(), payload.damagePercent(),
                    payload.releaseTicks(), payload.impactTick(),
                    preserve ? previous.visualEvent() : payload.visualEvent(),
                    animationTick,
                    preserve ? previous.impactX() : payload.impactX(),
                    preserve ? previous.impactY() : payload.impactY(),
                    preserve ? previous.impactZ() : payload.impactZ()
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
        ClientCrushingBlowQueue.clear();
    }

    public record State(
            UUID ownerId,
            int charge,
            int chargeThreshold,
            int damagePercent,
            int releaseTicks,
            int impactTick,
            ClientboundCrushingBlowPayload.VisualEvent visualEvent,
            long animationTick,
            float impactX,
            float impactY,
            float impactZ
    ) {
    }
}
