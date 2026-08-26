package com.steveaaaaa.ability.client;

import com.mojang.math.Axis;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.data.model.SkillDefinition;
import com.steveaaaaa.ability.network.ClientProgressCache;
import com.steveaaaaa.ability.network.ClientboundPurchaseResultPayload;
import com.steveaaaaa.ability.network.PlayerProgressSnapshot;
import com.steveaaaaa.ability.network.ServerboundPurchaseAbilityPayload;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AbilityScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 680;
    private static final int MAX_PANEL_HEIGHT = 360;
    private static final int HEADER_HEIGHT = 42;
    private static final int FOOTER_HEIGHT = 24;
    private static final int PANE_GAP = 7;
    private static final int SKILL_BUTTON_HEIGHT = 30;
    private static final int ABILITY_TILE_SIZE = 58;
    private static final int TILE_GAP = 8;
    private static final int DIAMOND_ICON_SIZE = 32;

    private static final ResourceLocation DUNGEON_ICON_FRAME = ResourceLocation.fromNamespaceAndPath(
            "ability",
            "textures/gui/dungeon_icon_frame.png"
    );

    private static final int BACKDROP = 0xD808090B;
    private static final int PANEL_SHADOW = 0xE0000000;
    private static final int PANEL_DARK = 0xF018191A;
    private static final int PANEL_MID = 0xF0232422;
    private static final int PANEL_LIGHT = 0xF02D2C28;
    private static final int PANEL_INSET = 0xFF151615;
    private static final int BORDER_DARK = 0xFF4A3824;
    private static final int BORDER_GOLD = 0xFFB88A43;
    private static final int BORDER_BRIGHT = 0xFFE3BC6B;
    private static final int BORDER_DIM = 0xFF70532E;
    private static final int TEXT_PRIMARY = 0xFFF2E8CF;
    private static final int TEXT_MUTED = 0xFFAAA28F;
    private static final int TEXT_DARK = 0xFF4A3828;
    private static final int LOCKED = 0xFF625E56;
    private static final int SUCCESS = 0xFF8FD17B;
    private static final int FAILURE = 0xFFFF7D6B;

    private final Screen previousScreen;
    private ResourceLocation selectedSkill;
    private ResourceLocation selectedAbility;
    private PlayerProgressSnapshot lastSnapshot = PlayerProgressSnapshot.EMPTY;
    private long lastCacheRevision;
    private int skillScroll;
    private int abilityScroll;
    private int detailScroll;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int leftWidth;
    private int rightWidth;
    private int contentTop;
    private int contentBottom;
    private int centerX;
    private int centerWidth;
    private int detailX;
    private int animationTicks;

    public AbilityScreen(Screen previousScreen) {
        super(Component.translatable("gui.ability.title"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_PANEL_WIDTH, width - 12);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 12);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        leftWidth = panelWidth >= 620 ? 132 : panelWidth >= 500 ? 116 : 92;
        rightWidth = panelWidth >= 620 ? 214 : panelWidth >= 500 ? 184 : 146;
        contentTop = panelY + HEADER_HEIGHT + 4;
        contentBottom = panelY + panelHeight - FOOTER_HEIGHT - 4;
        centerX = panelX + leftWidth + PANE_GAP;
        detailX = panelX + panelWidth - rightWidth;
        centerWidth = detailX - PANE_GAP - centerX;
        lastSnapshot = ClientProgressCache.snapshot();
        lastCacheRevision = ClientProgressCache.uiRevision();

        addRenderableWidget(new DungeonsButton(
                panelX + 9,
                panelY + 9,
                leftWidth - 18,
                20,
                Component.translatable("gui.ability.inventory_tab"),
                ButtonStyle.BACK,
                false,
                () -> minecraft.setScreen(previousScreen)
        ));

        List<Map.Entry<ResourceKey<SkillDefinition>, SkillDefinition>> skills = skills();
        if (selectedSkill == null || skills.stream().noneMatch(entry ->
                entry.getKey().location().equals(selectedSkill))) {
            selectedSkill = skills.isEmpty() ? null : skills.getFirst().getKey().location();
            skillScroll = 0;
            abilityScroll = 0;
        }
        addSkillButtons(skills);

        List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> abilities = abilitiesForSelectedSkill();
        if (selectedAbility == null || abilities.stream().noneMatch(entry ->
                entry.getKey().location().equals(selectedAbility))) {
            selectedAbility = abilities.isEmpty() ? null : abilities.getFirst().getKey().location();
            abilityScroll = 0;
            detailScroll = 0;
        }
        addAbilityTiles(abilities);
        addAbilityPageButtons(abilities);
        addPurchaseButton();
    }

    @Override
    public void tick() {
        animationTicks++;
        long currentRevision = ClientProgressCache.uiRevision();
        if (currentRevision != lastCacheRevision) {
            lastCacheRevision = currentRevision;
            lastSnapshot = ClientProgressCache.snapshot();
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (inside(mouseX, mouseY, panelX + 4, contentTop, panelX + leftWidth, contentBottom)) {
            int updated = ScrollWindow.scroll(skillScroll, scrollY, skills().size(), visibleSkillCount());
            if (updated != skillScroll) {
                skillScroll = updated;
                rebuildWidgets();
            }
            return true;
        }
        if (inside(mouseX, mouseY, detailX, contentTop, panelX + panelWidth - 5, contentBottom)) {
            int maximum = Math.max(0, detailDescriptionLines().size() - detailVisibleLines());
            int updated = Math.clamp(detailScroll + (scrollY > 0.0D ? -1 : 1), 0, maximum);
            if (updated != detailScroll) {
                detailScroll = updated;
            }
            return true;
        }
        if (inside(mouseX, mouseY, centerX, contentTop, centerX + centerWidth, contentBottom)) {
            moveAbilityPage(scrollY > 0.0D ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, BACKDROP);
        renderBackdropPattern(graphics);
        graphics.fill(panelX + 6, panelY + 7, panelX + panelWidth + 6, panelY + panelHeight + 7, 0xB0000000);
        graphics.fill(panelX + 3, panelY + 4, panelX + panelWidth + 3, panelY + panelHeight + 4, PANEL_SHADOW);
        panel(graphics, panelX, panelY, panelWidth, panelHeight, PANEL_DARK, BORDER_GOLD, true);
        renderPanelCorners(graphics, panelX, panelY, panelWidth, panelHeight, BORDER_BRIGHT);
        renderHeader(graphics);
        renderPanes(graphics);
        renderSkillScrollbar(graphics);
        renderSelectedSkill(graphics);
        renderAbilityGridBackground(graphics, partialTick);
        renderAbilityDetails(graphics);
        renderFooter(graphics);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previousScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addSkillButtons(List<Map.Entry<ResourceKey<SkillDefinition>, SkillDefinition>> skills) {
        int visible = visibleSkillCount();
        skillScroll = ScrollWindow.clamp(skillScroll, skills.size(), visible);
        int y = contentTop + 25;
        for (int visibleIndex = 0; visibleIndex < Math.min(visible, skills.size()); visibleIndex++) {
            int index = skillScroll + visibleIndex;
            if (index >= skills.size()) {
                break;
            }
            ResourceLocation skillId = skills.get(index).getKey().location();
            SkillDefinition definition = skills.get(index).getValue();
            DungeonsButton button = new DungeonsButton(
                    panelX + 8,
                    y + visibleIndex * (SKILL_BUTTON_HEIGHT + 3),
                    leftWidth - 21,
                    SKILL_BUTTON_HEIGHT,
                    Component.translatable(definition.display().name()),
                    ButtonStyle.SKILL,
                    skillId.equals(selectedSkill),
                    () -> {
                        selectedSkill = skillId;
                        selectedAbility = null;
                        abilityScroll = 0;
                        detailScroll = 0;
                        ClientProgressCache.clearPurchaseResult();
                        rebuildWidgets();
                    }
            );
            button.setAccent(parseColor(definition.display().color(), BORDER_GOLD));
            addRenderableWidget(button);
        }
    }

    private void addAbilityTiles(List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> abilities) {
        int visible = visibleAbilityCount();
        abilityScroll = clampAbilityPageOffset(abilityScroll, abilities.size(), visible);
        int columns = abilityColumns();
        int gridTop = contentTop + 55;
        int visibleCount = Math.min(visible, Math.max(0, abilities.size() - abilityScroll));
        for (int visibleIndex = 0; visibleIndex < visibleCount; visibleIndex++) {
            int index = abilityScroll + visibleIndex;
            if (index >= abilities.size()) {
                break;
            }
            ResourceLocation abilityId = abilities.get(index).getKey().location();
            AbilityDefinition definition = abilities.get(index).getValue();
            int row = visibleIndex / columns;
            AbilityTile tile = new AbilityTile(
                    abilityTileX(visibleIndex, visibleCount, columns),
                    gridTop + row * (ABILITY_TILE_SIZE + TILE_GAP),
                    abilityId,
                    definition,
                    lastSnapshot.abilityRank(abilityId),
                    abilityId.equals(selectedAbility),
                    () -> {
                        selectedAbility = abilityId;
                        detailScroll = 0;
                        ClientProgressCache.clearPurchaseResult();
                        rebuildWidgets();
                    }
            );
            tile.setTooltip(Tooltip.create(Component.translatable(definition.display().description())));
            addRenderableWidget(tile);
        }
    }

    private void addAbilityPageButtons(List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> abilities) {
        int pageSize = visibleAbilityCount();
        if (abilities.size() <= pageSize) {
            return;
        }
        int y = contentBottom - 20;
        DungeonsButton previous = new DungeonsButton(
                centerX + 8,
                y,
                24,
                16,
                Component.literal("<"),
                ButtonStyle.PAGE,
                false,
                () -> moveAbilityPage(-1)
        );
        previous.active = abilityScroll > 0;
        previous.setTooltip(Tooltip.create(Component.translatable("gui.ability.previous_page")));
        addRenderableWidget(previous);

        DungeonsButton next = new DungeonsButton(
                centerX + centerWidth - 32,
                y,
                24,
                16,
                Component.literal(">"),
                ButtonStyle.PAGE,
                false,
                () -> moveAbilityPage(1)
        );
        next.active = abilityScroll + pageSize < abilities.size();
        next.setTooltip(Tooltip.create(Component.translatable("gui.ability.next_page")));
        addRenderableWidget(next);
    }

    private void moveAbilityPage(int direction) {
        List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> abilities = abilitiesForSelectedSkill();
        int pageSize = visibleAbilityCount();
        int updated = clampAbilityPageOffset(
                abilityScroll + direction * pageSize,
                abilities.size(),
                pageSize
        );
        if (updated == abilityScroll) {
            return;
        }
        abilityScroll = updated;
        if (!abilities.isEmpty() && updated < abilities.size()) {
            selectedAbility = abilities.get(updated).getKey().location();
        }
        detailScroll = 0;
        ClientProgressCache.clearPurchaseResult();
        rebuildWidgets();
    }

    private void addPurchaseButton() {
        SelectedAbility selected = selectedAbility();
        if (selected == null) {
            return;
        }
        PurchaseState state = purchaseState(selected.id(), selected.definition());
        Component label = state.maxed()
                ? Component.translatable("gui.ability.max_rank")
                : state.purchasedRank() > 0
                        ? Component.translatable("gui.ability.upgrade")
                        : state.canPurchase()
                                ? Component.translatable("gui.ability.purchase")
                                : Component.translatable("gui.ability.locked");
        DungeonsButton purchase = new DungeonsButton(
                detailX + 10,
                contentBottom - 29,
                rightWidth - 20,
                21,
                label,
                ButtonStyle.PURCHASE,
                false,
                () -> requestPurchase(selected.id())
        );
        purchase.active = state.canPurchase() && ClientProgressCache.pendingPurchase() == null;
        purchase.setAccent(parseColor(selected.definition().display().color(), BORDER_BRIGHT));
        addRenderableWidget(purchase);
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.fill(panelX + 2, panelY + 2, panelX + panelWidth - 2, panelY + HEADER_HEIGHT, 0xFF101111);
        graphics.fill(panelX + 5, panelY + HEADER_HEIGHT - 2,
                panelX + panelWidth - 5, panelY + HEADER_HEIGHT - 1, BORDER_DARK);
        graphics.fill(panelX + leftWidth, panelY + 7, panelX + leftWidth + 1, panelY + HEADER_HEIGHT - 5, BORDER_DIM);
        int titleX = panelX + panelWidth / 2;
        graphics.drawCenteredString(font, title, titleX, panelY + 11, TEXT_PRIMARY);
        graphics.fill(titleX - 54, panelY + 27, titleX + 54, panelY + 28, BORDER_DIM);
        graphics.fill(titleX - 31, panelY + 26, titleX + 31, panelY + 29, BORDER_GOLD);
        graphics.fill(titleX - 10, panelY + 29, titleX + 10, panelY + 30, BORDER_BRIGHT);
        int shimmer = (animationTicks / 2) % 52;
        graphics.fill(titleX - 26 + shimmer, panelY + 26,
                titleX - 23 + shimmer, panelY + 27, 0xFFFFE4A0);
        pixelDiamond(graphics, titleX - 60, panelY + 27, 3, BORDER_GOLD);
        pixelDiamond(graphics, titleX + 60, panelY + 27, 3, BORDER_GOLD);
    }

    private void renderPanes(GuiGraphics graphics) {
        panel(graphics, panelX + 5, contentTop, leftWidth - 10, contentBottom - contentTop,
                PANEL_MID, BORDER_DARK, false);
        panel(graphics, centerX, contentTop, centerWidth, contentBottom - contentTop,
                PANEL_MID, BORDER_DARK, false);
        panel(graphics, detailX, contentTop, rightWidth - 5, contentBottom - contentTop,
                PANEL_LIGHT, BORDER_DARK, false);
        renderStoneTexture(graphics, panelX + 7, contentTop + 3,
                leftWidth - 14, contentBottom - contentTop - 6, 3);
        renderStoneTexture(graphics, centerX + 3, contentTop + 3,
                centerWidth - 6, contentBottom - contentTop - 6, 11);
        renderStoneTexture(graphics, detailX + 3, contentTop + 3,
                rightWidth - 11, contentBottom - contentTop - 6, 19);
        graphics.drawCenteredString(
                font,
                Component.translatable("gui.ability.skills_heading"),
                panelX + leftWidth / 2,
                contentTop + 9,
                TEXT_MUTED
        );
        graphics.fill(panelX + 18, contentTop + 22, panelX + leftWidth - 19, contentTop + 23, BORDER_DIM);
        pixelDiamond(graphics, panelX + leftWidth / 2, contentTop + 22, 2, BORDER_GOLD);
    }

    private void renderSkillScrollbar(GuiGraphics graphics) {
        scrollbar(
                graphics,
                panelX + leftWidth - 10,
                contentTop + 26,
                contentBottom - contentTop - 34,
                skills().size(),
                visibleSkillCount(),
                skillScroll
        );
    }

    private void renderSelectedSkill(GuiGraphics graphics) {
        if (selectedSkill == null || minecraft.level == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.ability.no_skills"),
                    centerX + centerWidth / 2, contentTop + 20, TEXT_MUTED);
            return;
        }
        SkillDefinition definition = skillRegistry().get(selectedSkill);
        if (definition == null) {
            return;
        }
        PlayerProgressSnapshot.SkillSnapshot progress = lastSnapshot.skills().getOrDefault(
                selectedSkill,
                PlayerProgressSnapshot.SkillSnapshot.EMPTY
        );
        int accent = parseColor(definition.display().color(), BORDER_GOLD);
        renderDiamondIcon(graphics, selectedSkill, definition.display().icon(),
                centerX + 21, contentTop + 20, 24, false);
        renderDiamondSelection(graphics, centerX + 21, contentTop + 20, 24, animatedGold());
        graphics.drawString(font, Component.translatable(definition.display().name()),
                centerX + 42, contentTop + 7, TEXT_PRIMARY, false);
        graphics.drawString(font, Component.translatable("gui.ability.skill_progress",
                        progress.level(), definition.maxLevel()),
                centerX + 42, contentTop + 19, TEXT_MUTED, false);
        Component points = Component.translatable("gui.ability.skill_points",
                lastSnapshot.availableSkillPoints(selectedSkill));
        graphics.drawString(font, points, centerX + 42, contentTop + 31, BORDER_BRIGHT, false);

        long consumed = 0L;
        for (int index = 0; index < Math.min(progress.level(), definition.xpToNext().size()); index++) {
            consumed += definition.xpToNext().get(index);
        }
        int required = progress.level() >= definition.maxLevel() ? 0 : definition.xpToNext().get(progress.level());
        long withinLevel = Math.max(0L, progress.totalXp() - consumed);
        if (centerWidth >= 220) {
            Component xp = required == 0
                    ? Component.translatable("gui.ability.max_level")
                    : Component.translatable("gui.ability.xp", withinLevel, required);
            graphics.drawString(font, xp, centerX + centerWidth - 8 - font.width(xp),
                    contentTop + 31, TEXT_MUTED, false);
        }
        int barX = centerX + 7;
        int barY = contentTop + 44;
        int barWidth = centerWidth - 14;
        graphics.fill(barX, barY, barX + barWidth, barY + 7, 0xFF0D0E0E);
        graphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + 6, 0xFF3C3932);
        int filled = required == 0 ? barWidth - 2 : (int) Math.min(barWidth - 2,
                withinLevel * (barWidth - 2) / required);
        graphics.fill(barX + 1, barY + 1, barX + 1 + filled, barY + 6, accent);
        for (int separator = 12; separator < barWidth - 2; separator += 12) {
            graphics.fill(barX + separator, barY + 1, barX + separator + 1, barY + 6, 0x55111111);
        }
        if (filled > 5) {
            int glint = (animationTicks / 2) % filled;
            graphics.fill(barX + 1 + glint, barY + 1, barX + 3 + glint, barY + 2, 0x99FFF0B0);
        }
    }

    private void renderAbilityGridBackground(GuiGraphics graphics, float partialTick) {
        renderGoldenDust(graphics, partialTick);
        List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> abilities = abilitiesForSelectedSkill();
        if (abilities.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.ability.no_abilities"),
                    centerX + centerWidth / 2, contentTop + 80, TEXT_MUTED);
            return;
        }
        int visible = visibleAbilityCount();
        if (abilities.size() > visible) {
            int currentPage = abilityScroll / visible + 1;
            int totalPages = (int) Math.ceil(abilities.size() / (double) visible);
            Component position = Component.translatable("gui.ability.page", currentPage, totalPages);
            graphics.drawCenteredString(font, position, centerX + centerWidth / 2,
                    contentBottom - 16, TEXT_MUTED);
        }
    }

    private void renderGoldenDust(GuiGraphics graphics, float partialTick) {
        int left = centerX + 9;
        int top = contentTop + 55;
        int dustWidth = centerWidth - 18;
        int dustHeight = contentBottom - top - 25;
        if (dustWidth <= 0 || dustHeight <= 0) {
            return;
        }
        float animationTime = animationTicks + partialTick;
        for (int index = 0; index < 9; index++) {
            float x = left + positiveModulo(index * 47.0F + animationTime / 12.0F, dustWidth);
            float y = top + positiveModulo(index * 31.0F - animationTime / 4.0F, dustHeight);
            int phase = Math.floorMod((int) animationTime + index * 13, 48);
            int color = phase < 8 ? 0x997B5C31 : phase < 20 ? 0x665B4529 : 0x3D4C3B26;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0.0F);
            graphics.fill(0, 0, 1, 1, color);
            if (phase < 3) {
                graphics.fill(-1, 0, 2, 1, 0x66785A30);
                graphics.fill(0, -1, 1, 2, 0x66785A30);
            }
            graphics.pose().popPose();
        }
    }

    private static float positiveModulo(float value, int modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }

    private int abilityTileX(int visibleIndex, int visibleCount, int columns) {
        int row = visibleIndex / columns;
        int column = visibleIndex % columns;
        int rowStart = row * columns;
        int rowCount = Math.min(columns, visibleCount - rowStart);
        int rowWidth = rowCount * ABILITY_TILE_SIZE + Math.max(0, rowCount - 1) * TILE_GAP;
        return centerX + Math.max(5, (centerWidth - rowWidth) / 2)
                + column * (ABILITY_TILE_SIZE + TILE_GAP);
    }

    private void renderAbilityDetails(GuiGraphics graphics) {
        SelectedAbility selected = selectedAbility();
        int x = detailX + 10;
        int width = rightWidth - 25;
        if (selected == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.ability.no_abilities"),
                    detailX + (rightWidth - 5) / 2, contentTop + 30, TEXT_MUTED);
            return;
        }
        AbilityDefinition definition = selected.definition();
        PurchaseState state = purchaseState(selected.id(), definition);
        int accent = parseColor(definition.display().color(), BORDER_GOLD);
        renderDiamondFill(graphics, x + 24, contentTop + 32, 26, 0xFF111212);
        renderDiamondIcon(graphics, selected.id(), definition.display().icon(),
                x + 24, contentTop + 32, 34, true);
        renderDiamondSelection(graphics, x + 24, contentTop + 32, 34, animatedGold());

        int textX = x + 57;
        int nameWidth = Math.max(35, width - 57);
        List<FormattedCharSequence> nameLines = font.split(
                Component.translatable(definition.display().name()), nameWidth);
        for (int line = 0; line < Math.min(2, nameLines.size()); line++) {
            graphics.drawString(font, nameLines.get(line), textX, contentTop + 10 + line * 10, TEXT_PRIMARY, false);
        }
        graphics.drawString(font, Component.translatable("gui.ability.rank",
                        Math.min(state.purchasedRank(), state.maxRank()), state.maxRank()),
                textX, contentTop + 35, state.purchasedRank() > 0 ? accent : LOCKED, false);

        int rankY = contentTop + 62;
        int dotWidth = Math.max(4, Math.min(7, (width - Math.max(0, state.maxRank() - 1) * 3) / state.maxRank()));
        int totalWidth = state.maxRank() * dotWidth + Math.max(0, state.maxRank() - 1) * 3;
        int rankX = x + Math.max(0, (width - totalWidth) / 2);
        for (int index = 0; index < state.maxRank(); index++) {
            int color = index < state.purchasedRank() ? accent : 0xFF4B4943;
            renderDiamondFill(graphics,
                    rankX + index * (dotWidth + 3) + dotWidth / 2,
                    rankY,
                    dotWidth,
                    color);
        }

        graphics.drawString(font, Component.translatable("gui.ability.description_heading"),
                x, contentTop + 75, BORDER_GOLD, false);
        graphics.fill(x + font.width(Component.translatable("gui.ability.description_heading")) + 5,
                contentTop + 79, x + width, contentTop + 80, BORDER_DIM);
        int descriptionY = detailDescriptionY();
        int descriptionBottom = detailDescriptionBottom();
        List<FormattedCharSequence> description = detailDescriptionLines();
        int visibleDescriptionLines = detailVisibleLines();
        detailScroll = Math.clamp(detailScroll, 0, Math.max(0, description.size() - visibleDescriptionLines));
        graphics.enableScissor(x, descriptionY, x + width - 6, descriptionBottom);
        for (int line = 0; line < visibleDescriptionLines; line++) {
            int index = detailScroll + line;
            if (index >= description.size()) {
                break;
            }
            graphics.drawString(font, description.get(index), x, descriptionY + line * 10, TEXT_MUTED, false);
        }
        graphics.disableScissor();
        scrollbar(
                graphics,
                x + width - 4,
                descriptionY,
                descriptionBottom - descriptionY,
                description.size(),
                visibleDescriptionLines,
                detailScroll
        );

        int requirementHeadingY = contentBottom - 70;
        Component requirementHeading = Component.translatable("gui.ability.requirement_heading");
        graphics.drawString(font, requirementHeading, x, requirementHeadingY, BORDER_GOLD, false);
        graphics.fill(x + font.width(requirementHeading) + 5, requirementHeadingY + 4,
                x + width, requirementHeadingY + 5, BORDER_DIM);
        List<FormattedCharSequence> requirement = font.split(
                Component.translatable("gui.ability.purchase_requirement",
                        state.requiredLevel(), state.pointCost()),
                width
        );
        for (int line = 0; line < Math.min(2, requirement.size()); line++) {
            graphics.drawString(font, requirement.get(line), x, contentBottom - 57 + line * 10,
                    state.canPurchase() || state.maxed() ? TEXT_PRIMARY : FAILURE, false);
        }
    }

    private void renderFooter(GuiGraphics graphics) {
        int y = panelY + panelHeight - FOOTER_HEIGHT;
        graphics.fill(panelX + 2, y, panelX + panelWidth - 2, panelY + panelHeight - 2, 0xFF121313);
        graphics.fill(panelX + 8, y, panelX + panelWidth - 8, y + 1, BORDER_DIM);
        pixelDiamond(graphics, panelX + 14, y, 2, BORDER_GOLD);
        pixelDiamond(graphics, panelX + panelWidth - 14, y, 2, BORDER_GOLD);
        ClientboundPurchaseResultPayload result = ClientProgressCache.lastPurchaseResult();
        if (result == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.ability.ui_hint"),
                    panelX + panelWidth / 2, y + 7, 0xFF817A6B);
            return;
        }
        int color = result.status() == ClientboundPurchaseResultPayload.Status.SUCCESS ? SUCCESS : FAILURE;
        graphics.drawCenteredString(font, PurchaseFeedback.message(result),
                panelX + panelWidth / 2, y + 7, color);
    }

    private void renderDiamondIcon(
            GuiGraphics graphics,
            ResourceLocation definitionId,
            ResourceLocation fallbackItemId,
            int centerX,
            int centerY,
            int size,
            boolean ability
    ) {
        String iconDirectory = ability ? "ability_icons/" : "skill_icons/";
        ResourceLocation finishedDiamondTexture = ResourceLocation.fromNamespaceAndPath(
                definitionId.getNamespace(),
                "textures/gui/" + iconDirectory + "diamond/" + definitionId.getPath() + ".png"
        );
        ResourceLocation squareTexture = ResourceLocation.fromNamespaceAndPath(
                definitionId.getNamespace(),
                "textures/gui/" + iconDirectory + definitionId.getPath() + ".png"
        );
        if (minecraft.getResourceManager().getResource(finishedDiamondTexture).isPresent()) {
            int finishedSize = Math.round(size * 1.08F);
            graphics.blit(finishedDiamondTexture,
                    centerX - finishedSize / 2, centerY - finishedSize / 2,
                    0.0F, 0.0F, finishedSize, finishedSize, finishedSize, finishedSize);
        } else if (minecraft.getResourceManager().getResource(squareTexture).isPresent()) {
            renderRotatedTexture(graphics, squareTexture, centerX, centerY, size);
        } else {
            ItemStack stack = BuiltInRegistries.ITEM.getOptional(fallbackItemId)
                    .map(ItemStack::new)
                    .orElseGet(() -> new ItemStack(Items.BARRIER));
            int itemSize = Math.max(12, Math.round(size * 0.53F));
            graphics.pose().pushPose();
            graphics.pose().translate(centerX - itemSize / 2.0F, centerY - itemSize / 2.0F, 0.0F);
            float scale = itemSize / 16.0F;
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
        }
        renderRotatedTexture(graphics, DUNGEON_ICON_FRAME, centerX, centerY, size);
    }

    private static void renderRotatedTexture(
            GuiGraphics graphics,
            ResourceLocation texture,
            int centerX,
            int centerY,
            int size
    ) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(45.0F));
        graphics.blit(texture, -size / 2, -size / 2, 0.0F, 0.0F, size, size, size, size);
        graphics.pose().popPose();
    }

    private static void renderDiamondFill(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int size,
            int color
    ) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(45.0F));
        graphics.fill(-size / 2, -size / 2, size / 2, size / 2, color);
        graphics.pose().popPose();
    }

    private static void renderDiamondShade(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int size,
            int color
    ) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(45.0F));
        graphics.fill(-size / 2, -size / 2, size / 2, size / 2, color);
        graphics.pose().popPose();
    }

    private static void renderDiamondSelection(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int size,
            int color
    ) {
        int extent = Math.round(size * 0.53F);
        graphics.fill(centerX - 2, centerY - extent, centerX + 2, centerY - extent + 2, color);
        graphics.fill(centerX - 2, centerY + extent - 2, centerX + 2, centerY + extent, color);
        graphics.fill(centerX - extent, centerY - 2, centerX - extent + 2, centerY + 2, color);
        graphics.fill(centerX + extent - 2, centerY - 2, centerX + extent, centerY + 2, color);
    }

    private int animatedGold() {
        float pulse = (float) ((Math.sin(animationTicks * 0.18D) + 1.0D) * 0.5D);
        return mixColor(BORDER_GOLD, BORDER_BRIGHT, 0.35F + pulse * 0.65F);
    }

    private void renderUnlockedSparkle(
            GuiGraphics graphics,
            ResourceLocation abilityId,
            int centerX,
            int centerY,
            int size
    ) {
        int cycle = Math.floorMod(animationTicks + abilityId.hashCode(), 80);
        if (cycle >= 12) {
            return;
        }
        int extent = Math.round(size * 0.46F);
        int point = cycle / 3;
        int sparkleX = switch (point) {
            case 0 -> centerX;
            case 1 -> centerX + extent;
            case 2 -> centerX;
            default -> centerX - extent;
        };
        int sparkleY = switch (point) {
            case 0 -> centerY - extent;
            case 1 -> centerY;
            case 2 -> centerY + extent;
            default -> centerY;
        };
        float intensity = 1.0F - Math.abs((cycle % 3) - 1) * 0.28F;
        pixelDiamond(graphics, sparkleX, sparkleY, 1,
                mixColor(BORDER_GOLD, 0xFFFFEDB0, intensity));
    }

    private static int mixColor(int from, int to, float amount) {
        float value = Math.clamp(amount, 0.0F, 1.0F);
        int red = Math.round(((from >> 16) & 0xFF) * (1.0F - value) + ((to >> 16) & 0xFF) * value);
        int green = Math.round(((from >> 8) & 0xFF) * (1.0F - value) + ((to >> 8) & 0xFF) * value);
        int blue = Math.round((from & 0xFF) * (1.0F - value) + (to & 0xFF) * value);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private PurchaseState purchaseState(ResourceLocation abilityId, AbilityDefinition definition) {
        int purchasedRank = lastSnapshot.abilityRank(abilityId);
        int maxRank = definition.ranks().values().size();
        boolean maxed = purchasedRank >= maxRank;
        int nextRankIndex = Math.min(purchasedRank, maxRank - 1);
        PlayerProgressSnapshot.SkillSnapshot skill = lastSnapshot.skills().getOrDefault(
                definition.skill(), PlayerProgressSnapshot.SkillSnapshot.EMPTY);
        int requiredLevel = Math.max(
                definition.purchase().skillLevel(),
                definition.ranks().unlockSkillLevels().get(nextRankIndex)
        );
        int pointCost = definition.ranks().skillPointCosts().get(nextRankIndex);
        boolean canPurchase = !maxed
                && skill.level() >= requiredLevel
                && lastSnapshot.availableSkillPoints(definition.skill()) >= pointCost;
        return new PurchaseState(purchasedRank, maxRank, requiredLevel, pointCost, maxed, canPurchase);
    }

    private void requestPurchase(ResourceLocation abilityId) {
        if (!ClientProgressCache.beginPurchase(abilityId)) {
            return;
        }
        PacketDistributor.sendToServer(new ServerboundPurchaseAbilityPayload(abilityId));
        rebuildWidgets();
    }

    private int visibleSkillCount() {
        return Math.max(1, (contentBottom - contentTop - 31) / (SKILL_BUTTON_HEIGHT + 3));
    }

    private int abilityColumns() {
        return Math.max(1, (centerWidth - 10 + TILE_GAP) / (ABILITY_TILE_SIZE + TILE_GAP));
    }

    private int visibleAbilityCount() {
        int gridHeight = contentBottom - (contentTop + 55) - 17;
        int rows = Math.max(1, (gridHeight + TILE_GAP) / (ABILITY_TILE_SIZE + TILE_GAP));
        return rows * abilityColumns();
    }

    private static int clampAbilityPageOffset(int offset, int itemCount, int pageSize) {
        if (itemCount <= 0 || pageSize <= 0) {
            return 0;
        }
        int lastPageStart = (itemCount - 1) / pageSize * pageSize;
        return Math.clamp(offset, 0, lastPageStart);
    }

    private int detailDescriptionY() {
        return contentTop + 87;
    }

    private int detailDescriptionBottom() {
        return contentBottom - 76;
    }

    private int detailVisibleLines() {
        return Math.max(1, (detailDescriptionBottom() - detailDescriptionY()) / 10);
    }

    private List<FormattedCharSequence> detailDescriptionLines() {
        SelectedAbility selected = selectedAbility();
        if (selected == null) {
            return List.of();
        }
        return font.split(
                Component.translatable(selected.definition().display().description()),
                Math.max(20, rightWidth - 31)
        );
    }

    private SelectedAbility selectedAbility() {
        if (selectedAbility == null || minecraft.level == null) {
            return null;
        }
        AbilityDefinition definition = abilityRegistry().get(selectedAbility);
        return definition == null ? null : new SelectedAbility(selectedAbility, definition);
    }

    private List<Map.Entry<ResourceKey<SkillDefinition>, SkillDefinition>> skills() {
        if (minecraft.level == null) {
            return List.of();
        }
        return skillRegistry().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<ResourceKey<SkillDefinition>, SkillDefinition> entry) ->
                                entry.getValue().display().sortOrder())
                        .thenComparing(entry -> entry.getKey().location()))
                .toList();
    }

    private List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> abilitiesForSelectedSkill() {
        if (minecraft.level == null || selectedSkill == null) {
            return List.of();
        }
        return abilityRegistry().entrySet().stream()
                .filter(entry -> entry.getValue().skill().equals(selectedSkill))
                .sorted(Comparator.comparingInt((Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition> entry) ->
                                entry.getValue().display().sortOrder())
                        .thenComparing(entry -> entry.getKey().location()))
                .toList();
    }

    private Registry<SkillDefinition> skillRegistry() {
        return minecraft.level.registryAccess().registryOrThrow(ModDataRegistries.SKILLS);
    }

    private Registry<AbilityDefinition> abilityRegistry() {
        return minecraft.level.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
    }

    private void renderBackdropPattern(GuiGraphics graphics) {
        int drift = animationTicks % 24;
        for (int x = -24 + drift; x < width + 24; x += 24) {
            graphics.fill(x, 0, x + 1, height, 0x141F211F);
        }
        for (int y = 12; y < height; y += 24) {
            graphics.fill(0, y, width, y + 1, 0x10181918);
        }
    }

    private static void renderPanelCorners(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        int arm = Math.min(14, Math.min(width, height) / 4);
        graphics.fill(x + 2, y + 2, x + 2 + arm, y + 3, color);
        graphics.fill(x + 2, y + 2, x + 3, y + 2 + arm, color);
        graphics.fill(x + width - 2 - arm, y + 2, x + width - 2, y + 3, color);
        graphics.fill(x + width - 3, y + 2, x + width - 2, y + 2 + arm, color);
        graphics.fill(x + 2, y + height - 3, x + 2 + arm, y + height - 2, color);
        graphics.fill(x + 2, y + height - 2 - arm, x + 3, y + height - 2, color);
        graphics.fill(x + width - 2 - arm, y + height - 3, x + width - 2, y + height - 2, color);
        graphics.fill(x + width - 3, y + height - 2 - arm, x + width - 2, y + height - 2, color);
    }

    private static void renderStoneTexture(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int seed
    ) {
        if (width < 8 || height < 8) {
            return;
        }
        int count = Math.max(5, width * height / 850);
        for (int index = 0; index < count; index++) {
            int px = x + 3 + Math.floorMod(index * 37 + seed * 13, width - 6);
            int py = y + 3 + Math.floorMod(index * 23 + seed * 7, height - 6);
            int color = (index & 1) == 0 ? 0xFF2B2C29 : 0xFF1B1C1B;
            graphics.fill(px, py, px + 1 + index % 2, py + 1, color);
        }
    }

    private static void pixelDiamond(GuiGraphics graphics, int centerX, int centerY, int size, int color) {
        for (int row = -size; row <= size; row++) {
            int halfWidth = size - Math.abs(row);
            graphics.fill(centerX - halfWidth, centerY + row,
                    centerX + halfWidth + 1, centerY + row + 1, color);
        }
    }

    private static void panel(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int fill,
            int border,
            boolean doubleBorder
    ) {
        graphics.fill(x + 2, y, x + width - 2, y + height, fill);
        graphics.fill(x, y + 2, x + width, y + height - 2, fill);
        graphics.fill(x + 2, y, x + width - 2, y + 1, border);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, border);
        graphics.fill(x, y + 2, x + 1, y + height - 2, border);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, border);
        if (doubleBorder && width > 8 && height > 8) {
            graphics.fill(x + 4, y + 3, x + width - 4, y + 4, BORDER_DARK);
            graphics.fill(x + 4, y + height - 4, x + width - 4, y + height - 3, BORDER_DARK);
            graphics.fill(x + 3, y + 4, x + 4, y + height - 4, BORDER_DARK);
            graphics.fill(x + width - 4, y + 4, x + width - 3, y + height - 4, BORDER_DARK);
        }
    }

    private static void scrollbar(
            GuiGraphics graphics,
            int x,
            int y,
            int height,
            int itemCount,
            int visibleCount,
            int offset
    ) {
        if (height <= 0) {
            return;
        }
        graphics.fill(x, y, x + 3, y + height, 0xFF111212);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFF4A4439);
        if (itemCount <= visibleCount || itemCount <= 0) {
            graphics.fill(x, y, x + 3, y + height, 0xFF6A5A3E);
            return;
        }
        int thumbHeight = Math.max(10, visibleCount * height / itemCount);
        int maximumOffset = itemCount - visibleCount;
        int thumbY = y + (height - thumbHeight) * Math.clamp(offset, 0, maximumOffset) / maximumOffset;
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, BORDER_GOLD);
        graphics.fill(x + 1, thumbY + 1, x + 2, thumbY + thumbHeight - 1, BORDER_BRIGHT);
    }

    private static int parseColor(String value, int fallback) {
        if (value == null || value.length() != 7 || value.charAt(0) != '#') {
            return fallback;
        }
        try {
            return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean inside(double mouseX, double mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private final class AbilityTile extends AbstractButton {
        private final ResourceLocation abilityId;
        private final AbilityDefinition definition;
        private final int purchasedRank;
        private final boolean selected;
        private final Runnable action;

        private AbilityTile(
                int x,
                int y,
                ResourceLocation abilityId,
                AbilityDefinition definition,
                int purchasedRank,
                boolean selected,
                Runnable action
        ) {
            super(x, y, ABILITY_TILE_SIZE, ABILITY_TILE_SIZE,
                    Component.translatable(definition.display().name()));
            this.abilityId = abilityId;
            this.definition = definition;
            this.purchasedRank = purchasedRank;
            this.selected = selected;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int accent = parseColor(definition.display().color(), BORDER_GOLD);
            int fill = purchasedRank > 0 ? 0xFF302A21 : 0xFF191A19;
            int iconX = getX() + getWidth() / 2;
            int iconY = getY() + 22 - (isHovered ? 1 : 0);
            renderDiamondFill(graphics, iconX, iconY, DIAMOND_ICON_SIZE - 8, fill);
            renderDiamondIcon(graphics, abilityId, definition.display().icon(),
                    iconX, iconY, DIAMOND_ICON_SIZE, true);
            if (purchasedRank <= 0) {
                renderDiamondShade(graphics, iconX, iconY, DIAMOND_ICON_SIZE - 4, 0x55101010);
            } else {
                renderUnlockedSparkle(graphics, abilityId, iconX, iconY, DIAMOND_ICON_SIZE);
            }
            if (selected) {
                renderDiamondSelection(graphics, iconX, iconY, DIAMOND_ICON_SIZE, animatedGold());
            }
            Component name = Component.translatable(definition.display().name());
            String clipped = font.plainSubstrByWidth(name.getString(), getWidth() - 8);
            int nameColor = selected
                    ? BORDER_BRIGHT
                    : purchasedRank > 0 || isHovered ? TEXT_PRIMARY : TEXT_MUTED;
            graphics.drawCenteredString(font, clipped, getX() + getWidth() / 2, getY() + 46,
                    nameColor);
            if (selected) {
                int selectionColor = animatedGold();
                graphics.fill(getX() + getWidth() / 2 - 10, getY() + 56,
                        getX() + getWidth() / 2 + 10, getY() + 57, selectionColor);
            } else if (isHovered) {
                graphics.fill(getX() + getWidth() / 2 - 5, getY() + 56,
                        getX() + getWidth() / 2 + 5, getY() + 57, BORDER_DIM);
            }
            int maxRank = definition.ranks().values().size();
            if (purchasedRank > 0) {
                String rank = purchasedRank + "/" + maxRank;
                int badgeWidth = font.width(rank) + 4;
                graphics.fill(getX() + getWidth() - badgeWidth - 1, getY() + 1,
                        getX() + getWidth() - 1, getY() + 11, 0xDD111212);
                graphics.drawString(font, rank, getX() + getWidth() - badgeWidth + 1,
                        getY() + 2, accent, false);
            } else {
                graphics.drawString(font, "◆", getX() + getWidth() - 9, getY() + 2, LOCKED, false);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static final class DungeonsButton extends AbstractButton {
        private final ButtonStyle style;
        private final boolean selected;
        private final Runnable action;
        private int accent = BORDER_GOLD;

        private DungeonsButton(
                int x,
                int y,
                int width,
                int height,
                Component message,
                ButtonStyle style,
                boolean selected,
                Runnable action
        ) {
            super(x, y, width, height, message);
            this.style = style;
            this.selected = selected;
            this.action = action;
        }

        private void setAccent(int accent) {
            this.accent = accent;
        }

        @Override
        public void onPress() {
            if (active) {
                action.run();
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int border = !active ? 0xFF4A4741 : selected || isHovered ? BORDER_BRIGHT : BORDER_DARK;
            int fill = switch (style) {
                case BACK -> 0xFF24231F;
                case SKILL -> selected ? 0xFF3B3021 : isHovered ? 0xFF302B23 : 0xFF232421;
                case PURCHASE -> active ? 0xFF4B3921 : 0xFF252522;
                case PAGE -> active ? 0xFF292720 : 0xFF1C1D1C;
            };
            panel(graphics, getX(), getY(), getWidth(), getHeight(), fill, border, selected || isHovered);
            if (style == ButtonStyle.SKILL) {
                graphics.fill(getX() + 3, getY() + 4, getX() + 6, getY() + getHeight() - 4,
                        selected ? accent : 0xFF4F4A40);
                if (selected) {
                    pixelDiamond(graphics, getX() + getWidth() - 6,
                            getY() + getHeight() / 2, 2, BORDER_BRIGHT);
                }
            } else if (style == ButtonStyle.PURCHASE && active) {
                pixelDiamond(graphics, getX() + 7, getY() + getHeight() / 2, 2, accent);
                pixelDiamond(graphics, getX() + getWidth() - 7,
                        getY() + getHeight() / 2, 2, accent);
            }
            int color = !active ? LOCKED : style == ButtonStyle.PURCHASE ? 0xFFFFE6A6 : TEXT_PRIMARY;
            String clipped = Minecraft.getInstance().font.plainSubstrByWidth(
                    getMessage().getString(), Math.max(4, getWidth() - (style == ButtonStyle.SKILL ? 18 : 8)));
            graphics.drawCenteredString(Minecraft.getInstance().font, clipped,
                    getX() + getWidth() / 2,
                    getY() + (getHeight() - 8) / 2,
                    color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private enum ButtonStyle {
        BACK,
        SKILL,
        PURCHASE,
        PAGE
    }

    private record SelectedAbility(ResourceLocation id, AbilityDefinition definition) {
    }

    private record PurchaseState(
            int purchasedRank,
            int maxRank,
            int requiredLevel,
            int pointCost,
            boolean maxed,
            boolean canPurchase
    ) {
    }
}
