package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class InventoryAbilityTab {
    private InventoryAbilityTab() {
    }

    @SubscribeEvent
    public static void onInventoryInitialized(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) {
            return;
        }
        AbstractContainerScreen<?> inventoryScreen = (AbstractContainerScreen<?>) screen;

        Button tab = Button.builder(
                        Component.translatable("gui.ability.tab.short"),
                        button -> Minecraft.getInstance().setScreen(new AbilityScreen(screen))
                )
                .bounds(
                        inventoryScreen.getGuiLeft() + inventoryScreen.getXSize() + 2,
                        inventoryScreen.getGuiTop() + 8,
                        24,
                        20
                )
                .tooltip(Tooltip.create(Component.translatable("gui.ability.tab")))
                .build();
        event.addListener(tab);
    }
}
