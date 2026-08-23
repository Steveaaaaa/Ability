package com.steveaaaaa.ability.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PlayerProgressTest {
    private static final ResourceLocation ABILITY = ResourceLocation.fromNamespaceAndPath("ability", "test");
    private static final ResourceLocation MINING = ResourceLocation.fromNamespaceAndPath("ability", "mining");
    private static final ResourceLocation FARMING = ResourceLocation.fromNamespaceAndPath("ability", "farming");

    @Test
    void purchasingAbilitySpendsPointsAndRecordsPurchase() {
        PlayerProgress before = new PlayerProgress(
                3,
                Map.of(MINING, new SkillProgress(0L, 10, 2)),
                Map.of(),
                0
        );

        PlayerProgress after = before.purchaseRank(ABILITY, MINING, 6);

        assertTrue(after.purchasedAbilities().contains(ABILITY));
        assertEquals(1, after.abilityRank(ABILITY));
        assertEquals(8, after.skill(MINING).spentSkillPoints());
        assertEquals(2, after.availableSkillPoints(MINING));
        assertTrue(before.purchasedAbilities().isEmpty());
    }

    @Test
    void doesNotBorrowPointsFromAnotherSkill() {
        PlayerProgress progress = new PlayerProgress(
                3,
                Map.of(
                        MINING, new SkillProgress(0L, 5, 2),
                        FARMING, new SkillProgress(0L, 100, 0)
                ),
                Map.of(),
                0
        );

        assertThrows(IllegalStateException.class, () -> progress.purchaseRank(ABILITY, MINING, 4));
    }

    @Test
    void purchasingAgainSpendsPointsAndRaisesOnlyOneRank() {
        PlayerProgress purchased = new PlayerProgress(
                3,
                Map.of(MINING, new SkillProgress(0L, 10, 6)),
                Map.of(ABILITY, 1),
                0
        );

        PlayerProgress upgraded = purchased.purchaseRank(ABILITY, MINING, 1);

        assertEquals(2, upgraded.abilityRank(ABILITY));
        assertEquals(7, upgraded.skill(MINING).spentSkillPoints());
    }

    @Test
    void grantsPointsOnlyToTheSkillThatLeveled() {
        PlayerProgress before = PlayerProgress.EMPTY;

        PlayerProgress after = before.withSkill(MINING, new SkillProgress(100L, 0, 0), 3);

        assertEquals(3, after.availableSkillPoints(MINING));
        assertEquals(0, after.availableSkillPoints(FARMING));
    }

    @Test
    void capsLegacyGrantedPointsAtTheCurrentSkillMaximum() {
        PlayerProgress progress = new PlayerProgress(
                3,
                Map.of(MINING, new SkillProgress(100L, 31, 5)),
                Map.of(),
                0
        );

        assertEquals(25, progress.availableSkillPoints(MINING, 30));
    }

    @Test
    void migratesUnspentVersionOneGlobalPointsWithoutDuplicatingThem() {
        var json = JsonParser.parseString("""
                {
                  "data_version": 1,
                  "skills": { "ability:mining": { "total_xp": 1200 } },
                  "purchased_abilities": [],
                  "granted_skill_points": 10,
                  "spent_skill_points": 3
                }
                """);

        PlayerProgress migrated = PlayerProgress.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        PlayerProgress afterPurchase = migrated.purchaseRank(ABILITY, MINING, 5);

        assertEquals(3, migrated.dataVersion());
        assertEquals(7, migrated.legacyUnassignedSkillPoints());
        assertEquals(2, afterPurchase.legacyUnassignedSkillPoints());
        assertEquals(2, afterPurchase.availableSkillPoints(FARMING));
    }

    @Test
    void versionThreeCodecPreservesPerSkillPointBalancesAndRanks() {
        PlayerProgress before = new PlayerProgress(
                3,
                Map.of(
                        MINING, new SkillProgress(1200L, 10, 6),
                        FARMING, new SkillProgress(500L, 4, 1)
                ),
                Map.of(ABILITY, 4),
                0
        );

        JsonElement encoded = PlayerProgress.CODEC.encodeStart(JsonOps.INSTANCE, before).result().orElseThrow();
        PlayerProgress decoded = PlayerProgress.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(before, decoded);
        assertEquals(4, decoded.availableSkillPoints(MINING));
        assertEquals(3, decoded.availableSkillPoints(FARMING));
        assertEquals(4, decoded.abilityRank(ABILITY));
    }

    @Test
    void migratesVersionTwoPurchasedAbilitiesToRankOne() {
        var json = JsonParser.parseString("""
                {
                  "data_version": 2,
                  "skills": {},
                  "purchased_abilities": ["ability:test"]
                }
                """);

        PlayerProgress migrated = PlayerProgress.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(3, migrated.dataVersion());
        assertEquals(1, migrated.abilityRank(ABILITY));
    }
}
