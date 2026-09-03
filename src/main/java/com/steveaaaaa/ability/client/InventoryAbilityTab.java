package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientProgressCache;
import com.steveaaaaa.ability.network.ClientWorldTravelerCache;
import com.steveaaaaa.ability.network.ServerboundWorldTravelerPayload;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class InventoryAbilityTab {
    private static final ResourceLocation WORLD_TRAVELER = AbilityMod.id("world_traveler");
    private static final ResourceLocation ABILITY_TAB_ICON =
            AbilityMod.id("textures/gui/ability_tab_icon.png");
    private static final ResourceLocation WORLD_TRAVELER_TAB_ICON =
            AbilityMod.id("textures/gui/world_traveler_tab_icon.png");
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 136;
    private static final int FILTER_GRID_Y = 29;
    private static final int REMOTE_BUTTON_X = 136;
    private static final int REMOTE_BUTTON_Y = 107;
    private static final int REMOTE_BUTTON_WIDTH = 34;
    private static final int REMOTE_BUTTON_HEIGHT = 20;
    private static final int ICON_RENDER_SIZE = 16;
    private static final int SCREEN_MARGIN = 2;
    private static boolean travelerPanelVisible;
    private static boolean suppressTravelerMouseRelease;
    private static DungeonTabButton abilityTabButton;
    private static DungeonTabButton travelerTabButton;
    private InventoryAbilityTab() {
    }

    @SubscribeEvent
    public static void onInventoryInitialized(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) {
            return;
        }
        AbstractContainerScreen<?> inventoryScreen = (AbstractContainerScreen<?>) screen;
        boolean survivalInventory = screen instanceof InventoryScreen;
        int abilityButtonX = survivalInventory
                ? inventoryScreen.getGuiLeft() + 126
                : inventoryScreen.getGuiLeft() + inventoryScreen.getXSize() + 2;
        int buttonY = survivalInventory
                ? inventoryScreen.getGuiTop() + 61
                : inventoryScreen.getGuiTop() + 7;

        abilityTabButton = new DungeonTabButton(
                abilityButtonX,
                buttonY,
                Component.translatable("gui.fantasypower.tab"),
                () -> false,
                () -> Minecraft.getInstance().setScreen(new AbilityScreen(screen)),
                ABILITY_TAB_ICON
        );
        abilityTabButton.setTooltip(Tooltip.create(Component.translatable("gui.fantasypower.tab")));
        event.addListener(abilityTabButton);
        travelerPanelVisible = false;
        suppressTravelerMouseRelease = false;
        travelerTabButton = null;
        if (survivalInventory && ClientProgressCache.snapshot().purchasedAbilities().contains(WORLD_TRAVELER)) {
            travelerTabButton = new DungeonTabButton(
                    inventoryScreen.getGuiLeft() + 148,
                    inventoryScreen.getGuiTop() + 61,
                    Component.translatable("gui.fantasypower.world_traveler.tab"),
                    () -> travelerPanelVisible,
                    () -> {
                        travelerPanelVisible = !travelerPanelVisible;
                        if (travelerPanelVisible) send(ServerboundWorldTravelerPayload.Action.REQUEST, -1);
                    },
                    WORLD_TRAVELER_TAB_ICON
            );
            travelerTabButton.setTooltip(Tooltip.create(
                    Component.translatable("gui.fantasypower.world_traveler.tab")));
            event.addListener(travelerTabButton);
        }
    }

    @SubscribeEvent
    public static void onRenderBefore(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory)) return;
        if (abilityTabButton != null) {
            abilityTabButton.setPosition(inventory.getGuiLeft() + 126, inventory.getGuiTop() + 61);
        }
        if (travelerTabButton != null) {
            travelerTabButton.setPosition(inventory.getGuiLeft() + 148, inventory.getGuiTop() + 61);
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!travelerPanelVisible || !(event.getScreen() instanceof InventoryScreen inventory)) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int x = panelX(inventory);
        int y = panelY(inventory);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF20242B);
        graphics.fill(x, y, x + PANEL_WIDTH, y + 1, 0xFFE3BC6B);
        graphics.fill(x, y + PANEL_HEIGHT - 1, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF6A5030);
        graphics.fill(x, y, x + 1, y + PANEL_HEIGHT, 0xFFE3BC6B);
        graphics.fill(x + PANEL_WIDTH - 1, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF6A5030);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("gui.fantasypower.world_traveler.filters"),
                x + 7, y + 6, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.fantasypower.world_traveler.filters.hint"),
                x + 7, y + 17, 0xFF9EA8B6, false);
        ItemStack hovered = ItemStack.EMPTY;
        for (int slot = 0; slot < 36; slot++) {
            int sx = x + 7 + (slot % 9) * 18;
            int sy = y + FILTER_GRID_Y + (slot / 9) * 18;
            graphics.fill(sx, sy, sx + 18, sy + 18, 0xFF5A606A);
            graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF171A20);
            ItemStack filter = ClientWorldTravelerCache.filter(slot);
            if (!filter.isEmpty()) {
                graphics.renderItem(filter, sx + 1, sy + 1);
                if (inside(event.getMouseX(), event.getMouseY(), sx, sy, 18, 18)) hovered = filter;
            }
        }
        ClientWorldTravelerCache.boundContainer().ifPresentOrElse(pos -> {
            String dimension = font.plainSubstrByWidth(pos.dimension().location().toString(), 124);
            String coordinates = font.plainSubstrByWidth(pos.pos().toShortString(), 124);
            graphics.drawString(font, dimension, x + 7, y + 106, 0xB8C1CC, false);
            graphics.drawString(font, coordinates, x + 7, y + 117, 0xB8C1CC, false);
        }, () -> graphics.drawString(font,
                Component.translatable("gui.fantasypower.world_traveler.unbound"),
                x + 7, y + 111, 0xB8C1CC, false));
        graphics.fill(x + REMOTE_BUTTON_X, y + REMOTE_BUTTON_Y,
                x + REMOTE_BUTTON_X + REMOTE_BUTTON_WIDTH,
                y + REMOTE_BUTTON_Y + REMOTE_BUTTON_HEIGHT, 0xFF475569);
        graphics.drawCenteredString(Minecraft.getInstance().font,
                Component.translatable("gui.fantasypower.world_traveler.remote"),
                x + REMOTE_BUTTON_X + REMOTE_BUTTON_WIDTH / 2, y + REMOTE_BUTTON_Y + 6, 0xFFFFFF);
        if (!hovered.isEmpty())
            graphics.renderTooltip(Minecraft.getInstance().font, hovered, event.getMouseX(), event.getMouseY());
        graphics.pose().popPose();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        suppressTravelerMouseRelease = false;
        if (!travelerPanelVisible || !(event.getScreen() instanceof InventoryScreen inventory)) return;
        int x = panelX(inventory);
        int y = panelY(inventory);
        if (!inside(event.getMouseX(), event.getMouseY(), x, y, PANEL_WIDTH, PANEL_HEIGHT)) return;
        suppressTravelerMouseRelease = true;
        if (inside(event.getMouseX(), event.getMouseY(),
                x + REMOTE_BUTTON_X, y + REMOTE_BUTTON_Y,
                REMOTE_BUTTON_WIDTH, REMOTE_BUTTON_HEIGHT)) {
            send(ServerboundWorldTravelerPayload.Action.OPEN_REMOTE, -1);
            event.setCanceled(true);
            return;
        }
        for (int slot = 0; slot < 36; slot++) {
            int sx = x + 7 + (slot % 9) * 18;
            int sy = y + FILTER_GRID_Y + (slot / 9) * 18;
            if (!inside(event.getMouseX(), event.getMouseY(), sx, sy, 18, 18)) continue;
            if (event.getButton() == 1) send(ServerboundWorldTravelerPayload.Action.CLEAR_FILTER, slot);
            else if (event.getButton() == 0 && !inventory.getMenu().getCarried().isEmpty())
                send(ServerboundWorldTravelerPayload.Action.SET_FILTER, slot);
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!suppressTravelerMouseRelease) return;
        suppressTravelerMouseRelease = false;
        event.setCanceled(true);
    }

    private static int panelX(InventoryScreen screen) {
        int left = screen.getGuiLeft() - PANEL_WIDTH - 4;
        if (left >= SCREEN_MARGIN) return left;
        int right = screen.getGuiLeft() + screen.getXSize() + 30;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        if (right + PANEL_WIDTH <= screenWidth - SCREEN_MARGIN) return right;
        return Math.max(SCREEN_MARGIN, screenWidth - PANEL_WIDTH - SCREEN_MARGIN);
    }

    private static int panelY(InventoryScreen screen) {
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return Math.max(SCREEN_MARGIN,
                Math.min(screen.getGuiTop(), screenHeight - PANEL_HEIGHT - SCREEN_MARGIN));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void send(ServerboundWorldTravelerPayload.Action action, int slot) {
        PacketDistributor.sendToServer(new ServerboundWorldTravelerPayload(action, slot));
    }

    private static final class DungeonTabButton extends AbstractButton {
        private static final int WIDTH = 20;
        private static final int HEIGHT = 20;
        private final BooleanSupplier selected;
        private final Runnable action;
        private final ResourceLocation texture;

        private DungeonTabButton(
                int x,
                int y,
                Component message,
                BooleanSupplier selected,
                Runnable action,
                ResourceLocation texture
        ) {
            super(x, y, WIDTH, HEIGHT, message);
            this.selected = selected;
            this.action = action;
            this.texture = texture;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            float lift = (selected.getAsBoolean() || isHovered()) ? 1.0F : 0.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + WIDTH / 2.0F,
                    getY() + HEIGHT / 2.0F - lift, 0.0F);
            float scale = ICON_RENDER_SIZE / (float) WIDTH;
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.blit(texture, -WIDTH / 2, -HEIGHT / 2,
                    0.0F, 0.0F, WIDTH, HEIGHT, WIDTH, HEIGHT);
            graphics.pose().popPose();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
