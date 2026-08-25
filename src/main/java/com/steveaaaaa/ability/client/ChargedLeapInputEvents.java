package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ChargedLeapInputEvents {
    private static final ResourceLocation CHARGED_LEAP = AbilityMod.id("charged_leap");
    static final int MAXIMUM_CHARGE_TICKS = 20;
    private static boolean physicalJumpDown;
    private static boolean charging;
    private static int chargeTicks;
    private static boolean leapPrimed;
    private static boolean leapWasAirborne;

    private ChargedLeapInputEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.screen != null
                || !ClientProgressCache.snapshot().purchasedAbilities().contains(CHARGED_LEAP)) {
            reset(true);
            return;
        }
        if (charging && physicalJumpDown) {
            minecraft.options.keyJump.setDown(false);
            chargeTicks = Math.min(chargeTicks + 1, MAXIMUM_CHARGE_TICKS);
        }
        if (leapPrimed) {
            if (!minecraft.player.onGround()) {
                leapWasAirborne = true;
            } else if (leapWasAirborne) {
                leapPrimed = false;
                leapWasAirborne = false;
            }
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.keyShift.matches(event.getKey(), event.getScanCode())
                && event.getAction() == InputConstants.RELEASE
                && charging) {
            cancelCharge(minecraft);
            return;
        }
        if (!minecraft.options.keyJump.matches(event.getKey(), event.getScanCode())) {
            return;
        }
        if (event.getAction() == InputConstants.PRESS) {
            physicalJumpDown = true;
            if (!canUse(minecraft)) {
                return;
            }
            if (minecraft.options.keyShift.isDown()
                    && minecraft.player.onGround()
                    && canChargeFromCurrentState(minecraft)) {
                minecraft.options.keyJump.setDown(false);
                charging = true;
                chargeTicks = 0;
                send(ActiveAbilityInput.CHARGE_START);
            } else if (leapPrimed && !minecraft.player.onGround()) {
                minecraft.options.keyJump.setDown(false);
                leapPrimed = false;
                send(ActiveAbilityInput.SECONDARY);
            }
        } else if (event.getAction() == InputConstants.REPEAT) {
            if (charging) {
                minecraft.options.keyJump.setDown(false);
            }
        } else if (event.getAction() == InputConstants.RELEASE) {
            physicalJumpDown = false;
            if (!charging) {
                return;
            }
            charging = false;
            chargeTicks = 0;
            leapPrimed = true;
            leapWasAirborne = false;
            send(ActiveAbilityInput.CHARGE_RELEASE);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset(false);
    }

    static boolean isCharging() {
        return charging;
    }

    static float chargeProgress(float partialTick) {
        if (!charging) {
            return 0.0F;
        }
        return Math.clamp((chargeTicks + Math.clamp(partialTick, 0.0F, 1.0F)) / MAXIMUM_CHARGE_TICKS, 0.0F, 1.0F);
    }

    private static boolean canUse(Minecraft minecraft) {
        return minecraft.player != null
                && minecraft.screen == null
                && ClientProgressCache.snapshot().purchasedAbilities().contains(CHARGED_LEAP);
    }

    private static boolean canChargeFromCurrentState(Minecraft minecraft) {
        return minecraft.player != null
                && !minecraft.player.isPassenger()
                && !minecraft.player.isInWater()
                && !minecraft.player.isInLava()
                && !minecraft.player.onClimbable()
                && !minecraft.player.getAbilities().flying;
    }

    private static void reset(boolean notifyServer) {
        if (notifyServer && charging) {
            send(ActiveAbilityInput.CHARGE_CANCEL);
        }
        physicalJumpDown = false;
        charging = false;
        chargeTicks = 0;
        leapPrimed = false;
        leapWasAirborne = false;
    }

    private static void cancelCharge(Minecraft minecraft) {
        charging = false;
        chargeTicks = 0;
        send(ActiveAbilityInput.CHARGE_CANCEL);
        minecraft.options.keyJump.setDown(physicalJumpDown);
    }

    private static void send(ActiveAbilityInput input) {
        PacketDistributor.sendToServer(new ServerboundActivateAbilityPayload(CHARGED_LEAP, input));
    }
}
