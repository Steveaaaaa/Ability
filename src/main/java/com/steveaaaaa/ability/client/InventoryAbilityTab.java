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
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class InventoryAbilityTab {
    private static final ResourceLocation WORLD_TRAVELER = AbilityMod.id("world_traveler");
    private static final ResourceLocation ABILITY_TAB_ICON =
            AbilityMod.id("textures/gui/ability_tab_icon.png");
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 116;
    private static final int SCREEN_MARGIN = 2;
    private static boolean travelerPanelVisible;
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
        int buttonX = survivalInventory
                ? inventoryScreen.getGuiLeft() + 77
                : inventoryScreen.getGuiLeft() + inventoryScreen.getXSize() + 2;

        DungeonTabButton tab = new DungeonTabButton(
                buttonX,
                inventoryScreen.getGuiTop() + 7,
                Component.translatable("gui.ability.tab"),
                () -> false,
                () -> Minecraft.getInstance().setScreen(new AbilityScreen(screen)),
                ABILITY_TAB_ICON,
                ItemStack.EMPTY
        );
        tab.setTooltip(Tooltip.create(Component.translatable("gui.ability.tab")));
        event.addListener(tab);
        travelerPanelVisible = false;
        if (survivalInventory && ClientProgressCache.snapshot().purchasedAbilities().contains(WORLD_TRAVELER)) {
            DungeonTabButton traveler = new DungeonTabButton(
                    buttonX,
                    inventoryScreen.getGuiTop() + 29,
                    Component.translatable("gui.ability.world_traveler.tab"),
                    () -> travelerPanelVisible,
                    () -> {
                        travelerPanelVisible = !travelerPanelVisible;
                        if (travelerPanelVisible) send(ServerboundWorldTravelerPayload.Action.REQUEST, -1);
                    },
                    null,
                    new ItemStack(Items.LODESTONE)
            );
            traveler.setTooltip(Tooltip.create(Component.translatable("gui.ability.world_traveler.tab")));
            event.addListener(traveler);
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
        String title = font.plainSubstrByWidth(
                Component.translatable("gui.ability.world_traveler.filters").getString(), PANEL_WIDTH - 14);
        graphics.drawString(font, title, x + 7, y + 6, 0xFFFFFF, false);
        ItemStack hovered = ItemStack.EMPTY;
        for (int slot = 0; slot < 36; slot++) {
            int sx = x + 7 + (slot % 9) * 18;
            int sy = y + 19 + (slot / 9) * 18;
            graphics.fill(sx, sy, sx + 18, sy + 18, 0xFF5A606A);
            graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF171A20);
            ItemStack filter = ClientWorldTravelerCache.filter(slot);
            if (!filter.isEmpty()) {
                graphics.renderItem(filter, sx + 1, sy + 1);
                if (inside(event.getMouseX(), event.getMouseY(), sx, sy, 18, 18)) hovered = filter;
            }
        }
        Component bound = ClientWorldTravelerCache.boundContainer()
                .<Component>map(pos -> Component.translatable("gui.ability.world_traveler.bound",
                        pos.dimension().location().toString(), pos.pos().toShortString()))
                .orElseGet(() -> Component.translatable("gui.ability.world_traveler.unbound"));
        graphics.drawString(Minecraft.getInstance().font, bound, x + 7, y + 94, 0xB8C1CC, false);
        graphics.fill(x + 136, y + 91, x + 170, y + 110, 0xFF475569);
        graphics.drawCenteredString(Minecraft.getInstance().font,
                Component.translatable("gui.ability.world_traveler.remote"), x + 153, y + 97, 0xFFFFFF);
        if (!hovered.isEmpty())
            graphics.renderTooltip(Minecraft.getInstance().font, hovered, event.getMouseX(), event.getMouseY());
        graphics.pose().popPose();
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!travelerPanelVisible || !(event.getScreen() instanceof InventoryScreen inventory)) return;
        int x = panelX(inventory);
        int y = panelY(inventory);
        if (inside(event.getMouseX(), event.getMouseY(), x + 136, y + 91, 34, 19)) {
            send(ServerboundWorldTravelerPayload.Action.OPEN_REMOTE, -1);
            event.setCanceled(true);
            return;
        }
        for (int slot = 0; slot < 36; slot++) {
            int sx = x + 7 + (slot % 9) * 18;
            int sy = y + 19 + (slot / 9) * 18;
            if (!inside(event.getMouseX(), event.getMouseY(), sx, sy, 18, 18)) continue;
            if (event.getButton() == 1) send(ServerboundWorldTravelerPayload.Action.CLEAR_FILTER, slot);
            else if (event.getButton() == 0 && !inventory.getMenu().getCarried().isEmpty())
                send(ServerboundWorldTravelerPayload.Action.SET_FILTER, slot);
            event.setCanceled(true);
            return;
        }
        if (inside(event.getMouseX(), event.getMouseY(), x, y, PANEL_WIDTH, PANEL_HEIGHT)) event.setCanceled(true);
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
        private final ItemStack itemIcon;

        private DungeonTabButton(
                int x,
                int y,
                Component message,
                BooleanSupplier selected,
                Runnable action,
                ResourceLocation texture,
                ItemStack itemIcon
        ) {
            super(x, y, WIDTH, HEIGHT, message);
            this.selected = selected;
            this.action = action;
            this.texture = texture;
            this.itemIcon = itemIcon;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean highlighted = selected.getAsBoolean() || isHovered();
            int border = highlighted ? 0xFFE3BC6B : 0xFF6A5030;
            int fill = selected.getAsBoolean() ? 0xFF493720 : highlighted ? 0xFF332B20 : 0xFF1E201F;
            graphics.fill(getX() + 2, getY(), getX() + getWidth() - 2, getY() + getHeight(), fill);
            graphics.fill(getX(), getY() + 2, getX() + getWidth(), getY() + getHeight() - 2, fill);
            graphics.fill(getX() + 2, getY(), getX() + getWidth() - 2, getY() + 1, border);
            graphics.fill(getX() + 2, getY() + getHeight() - 1,
                    getX() + getWidth() - 2, getY() + getHeight(), border);
            graphics.fill(getX(), getY() + 2, getX() + 1, getY() + getHeight() - 2, border);
            graphics.fill(getX() + getWidth() - 1, getY() + 2,
                    getX() + getWidth(), getY() + getHeight() - 2, border);
            if (texture != null) {
                graphics.blit(texture, getX() + 2, getY() + 2,
                        0.0F, 0.0F, 16, 16, 16, 16);
            } else if (!itemIcon.isEmpty()) {
                graphics.renderItem(itemIcon, getX() + 2, getY() + 2);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
