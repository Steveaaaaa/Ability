package com.steveaaaaa.ability.client;

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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AbilityScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 224;
    private static final int SIDEBAR_WIDTH = 112;
    private static final int SKILL_BUTTON_HEIGHT = 18;
    private static final int ABILITY_ROW_HEIGHT = 58;

    private final Screen previousScreen;
    private ResourceLocation selectedSkill;
    private PlayerProgressSnapshot lastSnapshot = PlayerProgressSnapshot.EMPTY;
    private long lastCacheRevision;
    private int skillScroll;
    private int abilityScroll;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public AbilityScreen(Screen previousScreen) {
        super(Component.translatable("gui.ability.title"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(PANEL_WIDTH, width - 16);
        panelHeight = Math.min(PANEL_HEIGHT, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        lastSnapshot = ClientProgressCache.snapshot();
        lastCacheRevision = ClientProgressCache.uiRevision();

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.ability.inventory_tab"),
                        button -> minecraft.setScreen(previousScreen)
                )
                .bounds(panelX + 8, panelY + 8, 72, 20)
                .build());

        List<Map.Entry<ResourceKey<SkillDefinition>, SkillDefinition>> skills = skills();
        if (selectedSkill == null || skills.stream().noneMatch(entry -> entry.getKey().location().equals(selectedSkill))) {
            selectedSkill = skills.isEmpty() ? null : skills.getFirst().getKey().location();
            abilityScroll = 0;
        }

        int skillStartY = panelY + 44;
        int visibleSkillCount = visibleSkillCount();
        skillScroll = ScrollWindow.clamp(skillScroll, skills.size(), visibleSkillCount);
        for (int visibleIndex = 0; visibleIndex < Math.min(skills.size(), visibleSkillCount); visibleIndex++) {
            int index = skillScroll + visibleIndex;
            if (index >= skills.size()) {
                break;
            }
            ResourceLocation skillId = skills.get(index).getKey().location();
            SkillDefinition definition = skills.get(index).getValue();
            Button skillButton = Button.builder(
                            Component.translatable(definition.display().name()),
                            button -> {
                                selectedSkill = skillId;
                                abilityScroll = 0;
                                ClientProgressCache.clearPurchaseResult();
                                rebuildWidgets();
                            }
                    )
                    .bounds(
                            panelX + 8,
                            skillStartY + visibleIndex * (SKILL_BUTTON_HEIGHT + 2),
                            SIDEBAR_WIDTH - 16,
                            SKILL_BUTTON_HEIGHT
                    )
                    .build();
            skillButton.active = !skillId.equals(selectedSkill);
            addRenderableWidget(skillButton);
        }

        int abilityX = panelX + SIDEBAR_WIDTH + 8;
        int abilityWidth = panelWidth - SIDEBAR_WIDTH - 16;
        int rowY = panelY + 88;
        List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> abilities = abilitiesForSelectedSkill();
        int visibleAbilityCount = visibleAbilityCount();
        abilityScroll = ScrollWindow.clamp(abilityScroll, abilities.size(), visibleAbilityCount);
        for (int visibleIndex = 0; visibleIndex < Math.min(abilities.size(), visibleAbilityCount); visibleIndex++) {
            int index = abilityScroll + visibleIndex;
            if (index >= abilities.size()) {
                break;
            }
            ResourceLocation abilityId = abilities.get(index).getKey().location();
            AbilityDefinition definition = abilities.get(index).getValue();
            boolean purchased = lastSnapshot.purchasedAbilities().contains(abilityId);
            PlayerProgressSnapshot.SkillSnapshot skill = lastSnapshot.skills().getOrDefault(
                    definition.skill(),
                    PlayerProgressSnapshot.SkillSnapshot.EMPTY
            );
            boolean meetsBasicRequirements = skill.level() >= definition.purchase().skillLevel()
                    && lastSnapshot.availableSkillPoints(definition.skill()) >= definition.purchase().skillPoints();
            Component label = purchased
                    ? Component.translatable("gui.ability.purchased")
                    : meetsBasicRequirements
                            ? Component.translatable("gui.ability.purchase")
                            : Component.translatable("gui.ability.locked");
            Button purchase = Button.builder(label, button -> requestPurchase(abilityId))
                    .bounds(
                            abilityX + abilityWidth - 68,
                            rowY + visibleIndex * ABILITY_ROW_HEIGHT + 24,
                            60,
                            20
                    )
                    .tooltip(Tooltip.create(Component.translatable(definition.display().description())))
                    .build();
            purchase.active = !purchased
                    && meetsBasicRequirements
                    && ClientProgressCache.pendingPurchase() == null;
            addRenderableWidget(purchase);
        }
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
        if (isInside(mouseX, mouseY, panelX + 4, panelY + 36, panelX + SIDEBAR_WIDTH, panelY + panelHeight - 6)) {
            int updated = ScrollWindow.scroll(skillScroll, scrollY, skills().size(), visibleSkillCount());
            if (updated != skillScroll) {
                skillScroll = updated;
                rebuildWidgets();
            }
            return true;
        }
        if (isInside(mouseX, mouseY, panelX + SIDEBAR_WIDTH, panelY + 82, panelX + panelWidth - 4, panelY + panelHeight - 6)) {
            int updated = ScrollWindow.scroll(
                    abilityScroll,
                    scrollY,
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
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE8101018);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFF6D8AB4);
        graphics.fill(
                panelX + SIDEBAR_WIDTH,
                panelY + 36,
                panelX + SIDEBAR_WIDTH + 1,
                panelY + panelHeight - 8,
                0xFF3A3A48
        );

        graphics.drawCenteredString(
                font,
                title,
                panelX + panelWidth / 2,
                panelY + 14,
                0xFFFFFF
        );
        renderSelectedSkill(graphics);
        renderAbilities(graphics);
        renderPurchaseFeedback(graphics);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previousScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderSelectedSkill(GuiGraphics graphics) {
        if (selectedSkill == null || minecraft.level == null) {
            graphics.drawString(font, Component.translatable("gui.ability.no_skills"), panelX + 124, panelY + 48, 0xAAAAAA);
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
        int contentX = panelX + SIDEBAR_WIDTH + 8;
        graphics.drawString(font, Component.translatable(definition.display().name()), contentX, panelY + 42, 0xFFFFFF);
        graphics.drawString(
                font,
                Component.translatable("gui.ability.skill_progress", progress.level(), definition.maxLevel()),
                contentX,
                panelY + 55,
                0xC8D8F0
        );
        graphics.drawString(
                font,
                Component.translatable(
                        "gui.ability.skill_points",
                        lastSnapshot.availableSkillPoints(selectedSkill)
                ),
                contentX + 102,
                panelY + 55,
                0xFFD166
        );

        long consumed = 0L;
        for (int index = 0; index < Math.min(progress.level(), definition.xpToNext().size()); index++) {
            consumed += definition.xpToNext().get(index);
        }
        int required = progress.level() >= definition.maxLevel()
                ? 0
                : definition.xpToNext().get(progress.level());
        long withinLevel = Math.max(0L, progress.totalXp() - consumed);
        int barX = contentX;
        int barY = panelY + 70;
        int barWidth = panelWidth - SIDEBAR_WIDTH - 18;
        graphics.fill(barX, barY, barX + barWidth, barY + 6, 0xFF282834);
        int filled = required == 0
                ? barWidth
                : (int) Math.min(barWidth, withinLevel * barWidth / required);
        graphics.fill(barX, barY, barX + filled, barY + 6, 0xFF6D8AB4);
        Component xpText = required == 0
                ? Component.translatable("gui.ability.max_level")
                : Component.translatable("gui.ability.xp", withinLevel, required);
        graphics.drawString(font, xpText, barX, barY + 8, 0xA8A8B8);
    }

    private void renderAbilities(GuiGraphics graphics) {
        int abilityX = panelX + SIDEBAR_WIDTH + 8;
        int abilityWidth = panelWidth - SIDEBAR_WIDTH - 16;
        int rowY = panelY + 88;
        List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> abilities = abilitiesForSelectedSkill();
        if (abilities.isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable("gui.ability.no_abilities"),
                    abilityX,
                    rowY + 8,
                    0x999999
            );
            return;
        }

        int visibleAbilityCount = visibleAbilityCount();
        for (int visibleIndex = 0; visibleIndex < Math.min(abilities.size(), visibleAbilityCount); visibleIndex++) {
            int index = abilityScroll + visibleIndex;
            if (index >= abilities.size()) {
                break;
            }
            ResourceLocation abilityId = abilities.get(index).getKey().location();
            AbilityDefinition definition = abilities.get(index).getValue();
            int y = rowY + visibleIndex * ABILITY_ROW_HEIGHT;
            graphics.fill(abilityX, y, abilityX + abilityWidth, y + ABILITY_ROW_HEIGHT - 4, 0xB820202A);
            graphics.drawString(font, Component.translatable(definition.display().name()), abilityX + 6, y + 6, 0xFFFFFF);
            if (lastSnapshot.purchasedAbilities().contains(abilityId)) {
                int skillLevel = lastSnapshot.skills().getOrDefault(
                        definition.skill(),
                        PlayerProgressSnapshot.SkillSnapshot.EMPTY
                ).level();
                graphics.drawString(
                        font,
                        Component.translatable(
                                "gui.ability.rank",
                                definition.ranks().rankForSkillLevel(skillLevel),
                                definition.ranks().values().size()
                        ),
                        abilityX + abilityWidth - 66,
                        y + 7,
                        0x80D8FF
                );
            }
            graphics.drawString(
                    font,
                    Component.translatable(
                            "gui.ability.purchase_requirement",
                            definition.purchase().skillLevel(),
                            definition.purchase().skillPoints()
                    ),
                    abilityX + 6,
                    y + 21,
                    0xB8B8C8
            );
            List<net.minecraft.util.FormattedCharSequence> description = font.split(
                    Component.translatable(definition.display().description()),
                    abilityWidth - 82
            );
            for (int line = 0; line < Math.min(2, description.size()); line++) {
                graphics.drawString(font, description.get(line), abilityX + 6, y + 37 + line * 9, 0x888898);
            }
        }
        if (abilities.size() > visibleAbilityCount) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "gui.ability.scroll_position",
                            abilityScroll + 1,
                            Math.min(abilities.size(), abilityScroll + visibleAbilityCount),
                            abilities.size()
                    ),
                    abilityX,
                    panelY + panelHeight - 14,
                    0x999999
            );
        }
    }

    private void renderPurchaseFeedback(GuiGraphics graphics) {
        ClientboundPurchaseResultPayload result = ClientProgressCache.lastPurchaseResult();
        if (result == null) {
            return;
        }
        Component message = PurchaseFeedback.message(result);
        int color = result.status() == ClientboundPurchaseResultPayload.Status.SUCCESS ? 0x70E070 : 0xFF7070;
        graphics.drawCenteredString(font, message, panelX + panelWidth / 2, panelY + panelHeight - 13, color);
    }

    private void requestPurchase(ResourceLocation abilityId) {
        if (!ClientProgressCache.beginPurchase(abilityId)) {
            return;
        }
        PacketDistributor.sendToServer(new ServerboundPurchaseAbilityPayload(abilityId));
        rebuildWidgets();
    }

    private int visibleSkillCount() {
        return Math.max(1, (panelHeight - 54) / (SKILL_BUTTON_HEIGHT + 2));
    }

    private int visibleAbilityCount() {
        return Math.max(1, (panelHeight - 100) / ABILITY_ROW_HEIGHT);
    }

    private static boolean isInside(double mouseX, double mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
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
}
