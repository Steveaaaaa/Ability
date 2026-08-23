package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import com.steveaaaaa.ability.network.ClientProgressCache;
import com.steveaaaaa.ability.network.ServerboundActivateAbilityPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class PrimerInputEvents {
    private static final ResourceLocation PRIMER = AbilityMod.id("primer");
    private static boolean previousUseDown;
    private static boolean charging;

    private PrimerInputEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!canCharge(minecraft)) {
            previousUseDown = false;
            charging = false;
            return;
        }

        boolean useDown = minecraft.options.keyUse.isDown();
        if (useDown && !previousUseDown) {
            charging = true;
            send(ActiveAbilityInput.CHARGE_START);
        } else if (!useDown && previousUseDown && charging) {
            charging = false;
            send(ActiveAbilityInput.CHARGE_RELEASE);
        }
        previousUseDown = useDown;
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.isUseItem() && canCharge(minecraft)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        previousUseDown = false;
        charging = false;
    }

    private static boolean canCharge(Minecraft minecraft) {
        return minecraft.player != null
                && minecraft.screen == null
                && minecraft.player.getOffhandItem().is(Items.FIRE_CHARGE)
                && ClientProgressCache.snapshot().purchasedAbilities().contains(PRIMER);
    }

    private static void send(ActiveAbilityInput input) {
        PacketDistributor.sendToServer(new ServerboundActivateAbilityPayload(PRIMER, input));
    }
}
