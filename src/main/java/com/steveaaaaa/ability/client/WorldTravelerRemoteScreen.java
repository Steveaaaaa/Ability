package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.menu.ModMenus;
import com.steveaaaaa.ability.menu.WorldTravelerRemoteMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class WorldTravelerRemoteScreen extends AbstractContainerScreen<WorldTravelerRemoteMenu> {
    public WorldTravelerRemoteScreen(WorldTravelerRemoteMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 114 + menu.rows() * 18;
        inventoryLabelY = imageHeight - 94;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF20242B);
        graphics.fill(leftPos + 4, topPos + 14, leftPos + imageWidth - 4,
                topPos + 22 + menu.rows() * 18, 0xFF111318);
        for (int slot = 0; slot < menu.remoteSlots(); slot++) {
            int x = leftPos + 7 + (slot % 9) * 18;
            int y = topPos + 17 + (slot / 9) * 18;
            graphics.fill(x, y, x + 18, y + 18, 0xFF5A606A);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF171A20);
        }
    }

    @EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
            event.register(ModMenus.WORLD_TRAVELER_REMOTE.get(), WorldTravelerRemoteScreen::new);
        }
    }
}
