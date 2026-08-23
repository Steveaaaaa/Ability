package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class CompanionGiftEffectTest {
    @Test
    void registersTheCompanionGiftEffectType() {
        assertTrue(AbilityEffectTypeRegistry.isRegistered(CompanionGiftEffect.TYPE));
    }

    @Test
    void weightedSelectionAlwaysReturnsAConfiguredEntry() {
        List<CompanionGiftEffect.WeightedEntry> entries = List.of(
                new CompanionGiftEffect.WeightedEntry(Items.IRON_INGOT, 3, 1, 1),
                new CompanionGiftEffect.WeightedEntry(Items.DIAMOND, 1, 1, 1)
        );
        RandomSource random = RandomSource.create(42L);

        for (int index = 0; index < 100; index++) {
            assertTrue(entries.contains(CompanionGiftEffect.choose(entries, random)));
        }
    }
}
