package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AbilityHungerCostServiceTest {
    @Test
    void preservesWholeFoodCostAndConvertsOnlyFraction() {
        AbilityHungerCostService.CostParts full = AbilityHungerCostService.splitFoodPointCost(1.0D);
        AbilityHungerCostService.CostParts reduced = AbilityHungerCostService.splitFoodPointCost(0.85D);

        assertEquals(1, full.wholeFoodPoints());
        assertEquals(0.0F, full.fractionalExhaustion());
        assertEquals(0, reduced.wholeFoodPoints());
        assertEquals(3.4F, reduced.fractionalExhaustion(), 0.0001F);
    }

    @Test
    void ignoresInvalidCosts() {
        assertEquals(0, AbilityHungerCostService.splitFoodPointCost(Double.NaN).wholeFoodPoints());
        assertEquals(0, AbilityHungerCostService.splitFoodPointCost(-1.0D).wholeFoodPoints());
    }
}
