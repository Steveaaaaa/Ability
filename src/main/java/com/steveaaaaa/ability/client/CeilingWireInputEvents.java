package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import com.steveaaaaa.ability.network.ClientProgressCache;
import com.steveaaaaa.ability.network.ServerboundActivateAbilityPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class CeilingWireInputEvents {
    private static final ResourceLocation ABILITY = AbilityMod.id("ceiling_wire");
    private static boolean previousJump;
    private static long lastJumpTick = Long.MIN_VALUE;
    private CeilingWireInputEvents() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null
                || !ClientProgressCache.snapshot().purchasedAbilities().contains(ABILITY)) {
            reset(); return;
        }
        boolean jump = minecraft.options.keyJump.isDown();
        if (jump && !previousJump) {
            long now = minecraft.player.tickCount;
            if (now - lastJumpTick <= 7) {
                PacketDistributor.sendToServer(new ServerboundActivateAbilityPayload(ABILITY, ActiveAbilityInput.SECONDARY));
                lastJumpTick = Long.MIN_VALUE;
            } else lastJumpTick = now;
        }
        previousJump = jump;
    }

    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    private static void reset() { previousJump = false; lastJumpTick = Long.MIN_VALUE; }
}
