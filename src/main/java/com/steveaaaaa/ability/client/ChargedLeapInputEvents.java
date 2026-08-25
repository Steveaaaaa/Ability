package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import com.steveaaaaa.ability.config.AbilityClientConfig;
import com.steveaaaaa.ability.config.AbilityClientConfig.ChargedLeapControlMode;
import com.steveaaaaa.ability.network.ClientProgressCache;
import com.steveaaaaa.ability.network.ServerboundActivateAbilityPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ChargedLeapInputEvents {
    private static final ResourceLocation CHARGED_LEAP = AbilityMod.id("charged_leap");
    static final int MAXIMUM_CHARGE_TICKS = 20;
    private static final int JUMP_KEY_TAP_TICKS = 4;
    private static boolean previousActivationDown;
    private static boolean previousJumpDown;
    private static boolean charging;
    private static int chargeTicks;
    private static boolean leapPrimed;
    private static boolean leapWasAirborne;
    private static boolean injectedVanillaJump;
    private static ChargedLeapControlMode chargingMode;

    private ChargedLeapInputEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        ChargedLeapControlMode mode = AbilityClientConfig.chargedLeapControlMode();
        if (!canUse(minecraft) || mode == ChargedLeapControlMode.JUMP_KEY
                && minecraft.player.getAbilities().instabuild) {
            if (minecraft.player != null
                    && (mode == ChargedLeapControlMode.JUMP_KEY
                    || chargingMode == ChargedLeapControlMode.JUMP_KEY)) {
                minecraft.options.keyJump.setDown(isPhysicallyDown(minecraft, minecraft.options.keyJump));
            }
            reset(true);
            return;
        }

        boolean jumpDown = isPhysicallyDown(minecraft, minecraft.options.keyJump);
        if (injectedVanillaJump) {
            minecraft.options.keyJump.setDown(jumpDown);
            injectedVanillaJump = false;
        }
        boolean activationDown = mode == ChargedLeapControlMode.JUMP_KEY
                ? jumpDown
                : isPhysicallyDown(minecraft, AbilityKeyMappings.CHARGED_LEAP);
        boolean activationPressed = activationDown && !previousActivationDown;
        boolean jumpPressed = jumpDown && !previousJumpDown;

        if (charging && chargingMode != mode) {
            cancelCharge();
        }
        if (charging) {
            controlledKey(minecraft, chargingMode).setDown(false);
            if (!activationDown) {
                releaseCharge(minecraft, chargingMode);
            } else {
                chargeTicks = Math.min(chargeTicks + 1, MAXIMUM_CHARGE_TICKS);
            }
        } else if (activationPressed && minecraft.player.onGround() && canChargeFromCurrentState(minecraft)) {
            controlledKey(minecraft, mode).setDown(false);
            charging = true;
            chargingMode = mode;
            chargeTicks = 0;
            send(mode == ChargedLeapControlMode.JUMP_KEY
                    ? ActiveAbilityInput.SPACE_CHARGE_START
                    : ActiveAbilityInput.CHARGE_START);
        } else if (jumpPressed && leapPrimed && !minecraft.player.onGround()) {
            minecraft.options.keyJump.setDown(false);
            leapPrimed = false;
            send(ActiveAbilityInput.SECONDARY);
        }

        previousActivationDown = activationDown;
        previousJumpDown = jumpDown;
        updateLeapState(minecraft);
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
        return Math.clamp(
                (chargeTicks + Math.clamp(partialTick, 0.0F, 1.0F)) / MAXIMUM_CHARGE_TICKS,
                0.0F,
                1.0F
        );
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

    private static void releaseCharge(Minecraft minecraft, ChargedLeapControlMode mode) {
        boolean shortJumpTap = mode == ChargedLeapControlMode.JUMP_KEY && chargeTicks < JUMP_KEY_TAP_TICKS;
        charging = false;
        chargingMode = null;
        if (shortJumpTap) {
            chargeTicks = 0;
            send(ActiveAbilityInput.CHARGE_CANCEL);
            minecraft.options.keyJump.setDown(true);
            injectedVanillaJump = true;
            return;
        }
        chargeTicks = 0;
        leapPrimed = true;
        leapWasAirborne = false;
        send(ActiveAbilityInput.CHARGE_RELEASE);
    }

    private static void updateLeapState(Minecraft minecraft) {
        if (!leapPrimed) {
            return;
        }
        if (!minecraft.player.onGround()) {
            leapWasAirborne = true;
        } else if (leapWasAirborne) {
            leapPrimed = false;
            leapWasAirborne = false;
        }
    }

    private static void reset(boolean notifyServer) {
        if (notifyServer && charging) {
            send(ActiveAbilityInput.CHARGE_CANCEL);
        }
        previousActivationDown = false;
        previousJumpDown = false;
        charging = false;
        chargeTicks = 0;
        leapPrimed = false;
        leapWasAirborne = false;
        injectedVanillaJump = false;
        chargingMode = null;
    }

    private static void cancelCharge() {
        charging = false;
        chargeTicks = 0;
        chargingMode = null;
        send(ActiveAbilityInput.CHARGE_CANCEL);
    }

    private static KeyMapping controlledKey(Minecraft minecraft, ChargedLeapControlMode mode) {
        return mode == ChargedLeapControlMode.JUMP_KEY
                ? minecraft.options.keyJump
                : AbilityKeyMappings.CHARGED_LEAP;
    }

    private static boolean isPhysicallyDown(Minecraft minecraft, KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        long window = minecraft.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, key.getValue());
        }
        return mapping.isDown();
    }

    private static void send(ActiveAbilityInput input) {
        PacketDistributor.sendToServer(new ServerboundActivateAbilityPayload(CHARGED_LEAP, input));
    }
}
