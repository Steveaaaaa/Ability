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
    private static final int MAX_PANEL_WIDTH = 580;
    private static final int MAX_PANEL_HEIGHT = 326;
    private static final int HEADER_HEIGHT = 38;
    private static final int FOOTER_HEIGHT = 22;
    private static final int PANE_GAP = 6;
    private static final int SKILL_BUTTON_HEIGHT = 30;
    private static final int ABILITY_TILE_SIZE = 58;
    private static final int TILE_GAP = 6;
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
    private static final int BORDER_DARK = 0xFF4A3824;
    private static final int BORDER_GOLD = 0xFFB88A43;
    private static final int BORDER_BRIGHT = 0xFFE3BC6B;
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

    public AbilityScreen(Screen previousScreen) {
        super(Component.translatable("gui.ability.title"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_PANEL_WIDTH, width - 16);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        leftWidth = panelWidth >= 500 ? 116 : 92;
        rightWidth = panelWidth >= 500 ? 184 : 146;
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
        addPurchaseButton();
    }

    @Override
    public void tick() {
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
            int direction = scrollY > 0.0D ? -abilityColumns() : abilityColumns();
            int updated = ScrollWindow.clamp(
                    abilityScroll + direction,
                    abilitiesForSelectedSkill().size(),
                    visibleAbilityCount()
            );
            if (updated != abilityScroll) {
                abilityScroll = updated;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, BACKDROP);
        graphics.fill(panelX + 4, panelY + 5, panelX + panelWidth + 4, panelY + panelHeight + 5, PANEL_SHADOW);
        panel(graphics, panelX, panelY, panelWidth, panelHeight, PANEL_DARK, BORDER_GOLD, true);
        renderHeader(graphics);
        renderPanes(graphics);
        renderSkillScrollbar(graphics);
        renderSelectedSkill(graphics);
        renderAbilityGridBackground(graphics);
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
        abilityScroll = ScrollWindow.clamp(abilityScroll, abilities.size(), visible);
        int columns = abilityColumns();
        int gridTop = contentTop + 55;
        int occupiedWidth = columns * ABILITY_TILE_SIZE + (columns - 1) * TILE_GAP;
        int startX = centerX + Math.max(5, (centerWidth - occupiedWidth) / 2);
        for (int visibleIndex = 0; visibleIndex < Math.min(visible, abilities.size()); visibleIndex++) {
            int index = abilityScroll + visibleIndex;
            if (index >= abilities.size()) {
                break;
            }
            ResourceLocation abilityId = abilities.get(index).getKey().location();
            AbilityDefinition definition = abilities.get(index).getValue();
            int column = visibleIndex % columns;
            int row = visibleIndex / columns;
            AbilityTile tile = new AbilityTile(
                    startX + column * (ABILITY_TILE_SIZE + TILE_GAP),
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
                contentBottom - 31,
                rightWidth - 20,
                23,
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
        graphics.fill(panelX + 2, panelY + 2, panelX + panelWidth - 2, panelY + HEADER_HEIGHT, 0xFF121313);
        graphics.fill(panelX + leftWidth, panelY + 7, panelX + leftWidth + 1, panelY + HEADER_HEIGHT - 5, BORDER_DARK);
        graphics.drawCenteredString(font, title, panelX + panelWidth / 2, panelY + 12, TEXT_PRIMARY);
        graphics.fill(panelX + panelWidth / 2 - 34, panelY + 25, panelX + panelWidth / 2 + 34, panelY + 26, BORDER_GOLD);
        graphics.fill(panelX + panelWidth / 2 - 12, panelY + 27, panelX + panelWidth / 2 + 12, panelY + 28, BORDER_BRIGHT);
    }

    private void renderPanes(GuiGraphics graphics) {
        panel(graphics, panelX + 5, contentTop, leftWidth - 10, contentBottom - contentTop,
                PANEL_MID, BORDER_DARK, false);
        panel(graphics, centerX, contentTop, centerWidth, contentBottom - contentTop,
                PANEL_MID, BORDER_DARK, false);
        panel(graphics, detailX, contentTop, rightWidth - 5, contentBottom - contentTop,
                PANEL_LIGHT, BORDER_DARK, false);
        graphics.drawCenteredString(
                font,
                Component.translatable("gui.ability.skills_heading"),
                panelX + leftWidth / 2,
                contentTop + 9,
                TEXT_MUTED
        );
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
        int barX = centerX + 7;
        int barY = contentTop + 44;
        int barWidth = centerWidth - 14;
        graphics.fill(barX, barY, barX + barWidth, barY + 7, 0xFF0D0E0E);
        graphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + 6, 0xFF3C3932);
        int filled = required == 0 ? barWidth - 2 : (int) Math.min(barWidth - 2,
                withinLevel * (barWidth - 2) / required);
        graphics.fill(barX + 1, barY + 1, barX + 1 + filled, barY + 6, accent);
    }

    private void renderAbilityGridBackground(GuiGraphics graphics) {
        List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> abilities = abilitiesForSelectedSkill();
        if (abilities.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.ability.no_abilities"),
                    centerX + centerWidth / 2, contentTop + 80, TEXT_MUTED);
            return;
        }
        int visible = visibleAbilityCount();
        if (abilities.size() > visible) {
            Component position = Component.translatable(
                    "gui.ability.scroll_position",
                    abilityScroll + 1,
                    Math.min(abilities.size(), abilityScroll + visible),
                    abilities.size()
            );
            graphics.drawCenteredString(font, position, centerX + centerWidth / 2,
                    contentBottom - 12, 0xFF777165);
        }
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

        int rankY = contentTop + 61;
        int dotWidth = Math.max(2, Math.min(8, (width - Math.max(0, state.maxRank() - 1) * 2) / state.maxRank()));
        int totalWidth = state.maxRank() * dotWidth + Math.max(0, state.maxRank() - 1) * 2;
        int rankX = x + Math.max(0, (width - totalWidth) / 2);
        for (int index = 0; index < state.maxRank(); index++) {
            int color = index < state.purchasedRank() ? accent : 0xFF4B4943;
            graphics.fill(rankX + index * (dotWidth + 2), rankY,
                    rankX + index * (dotWidth + 2) + dotWidth, rankY + 4, color);
        }

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

        int requirementY = contentBottom - 53;
        graphics.fill(x, requirementY - 4, x + width, requirementY - 3, BORDER_DARK);
        graphics.drawString(font, Component.translatable("gui.ability.purchase_requirement",
                        state.requiredLevel(), state.pointCost()),
                x, requirementY + 2, state.canPurchase() || state.maxed() ? TEXT_PRIMARY : FAILURE, false);
    }

    private void renderFooter(GuiGraphics graphics) {
        int y = panelY + panelHeight - FOOTER_HEIGHT;
        graphics.fill(panelX + 2, y, panelX + panelWidth - 2, panelY + panelHeight - 2, 0xFF121313);
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
            int finishedSize = Math.round(size * 1.2F);
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

    private int detailDescriptionY() {
        return contentTop + 74;
    }

    private int detailDescriptionBottom() {
        return contentBottom - 61;
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
            int iconY = getY() + 22;
            if (selected || isHovered) {
                renderRotatedTexture(graphics, DUNGEON_ICON_FRAME,
                        iconX, iconY, DIAMOND_ICON_SIZE + 5);
            }
            renderDiamondFill(graphics, iconX, iconY, DIAMOND_ICON_SIZE - 8, fill);
            renderDiamondIcon(graphics, abilityId, definition.display().icon(),
                    iconX, iconY, DIAMOND_ICON_SIZE, true);
            if (purchasedRank <= 0) {
                renderDiamondShade(graphics, iconX, iconY, DIAMOND_ICON_SIZE - 4, 0x55101010);
            }
            Component name = Component.translatable(definition.display().name());
            String clipped = font.plainSubstrByWidth(name.getString(), getWidth() - 8);
            graphics.drawCenteredString(font, clipped, getX() + getWidth() / 2, getY() + 46,
                    purchasedRank > 0 ? TEXT_PRIMARY : TEXT_MUTED);
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
            };
            panel(graphics, getX(), getY(), getWidth(), getHeight(), fill, border, selected || isHovered);
            if (style == ButtonStyle.SKILL) {
                graphics.fill(getX() + 3, getY() + 4, getX() + 6, getY() + getHeight() - 4,
                        selected ? accent : 0xFF4F4A40);
            }
            int color = !active ? LOCKED : style == ButtonStyle.PURCHASE ? 0xFFFFE6A6 : TEXT_PRIMARY;
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
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
        PURCHASE
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
