package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AbilityKeyMappings {
    public static final KeyMapping DODGE = new KeyMapping(
            "key.fantasypower.dodge",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.fantasypower"
    );
    public static final KeyMapping CHARGED_LEAP = new KeyMapping(
            "key.fantasypower.charged_leap",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.fantasypower"
    );
    private AbilityKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(DODGE);
        event.register(CHARGED_LEAP);
    }
}
