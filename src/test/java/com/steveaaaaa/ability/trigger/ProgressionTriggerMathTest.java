package com.steveaaaaa.ability.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProgressionTriggerMathTest {
    @Test
    void scalesDefenseExperienceFromFinalDamageAndCapsSingleHits() {
        TriggerTypeRegistry.TakeDamageConfig config = new TriggerTypeRegistry.TakeDamageConfig(
                1.0D,
                4.0D,
                20.0D,
                true,
                List.of()
        );

        assertEquals(2.0D, TriggerTypeRegistry.calculateDamageMultiplier(8.0F, config));
        assertEquals(5.0D, TriggerTypeRegistry.calculateDamageMultiplier(100.0F, config));
    }

    @Test
    void scalesEnchantingExperienceByAppliedLevelsWithACap() {
        TriggerTypeRegistry.EnchantItemConfig scaled = new TriggerTypeRegistry.EnchantItemConfig(
                Optional.empty(),
                1,
                1,
                true,
                10.0D
        );
        TriggerTypeRegistry.EnchantItemConfig fixed = new TriggerTypeRegistry.EnchantItemConfig(
                Optional.empty(),
                1,
                1,
                false,
                10.0D
        );

        assertEquals(7.0D, TriggerTypeRegistry.calculateEnchantmentMultiplier(7, scaled));
        assertEquals(10.0D, TriggerTypeRegistry.calculateEnchantmentMultiplier(20, scaled));
        assertEquals(1.0D, TriggerTypeRegistry.calculateEnchantmentMultiplier(20, fixed));
    }
}
