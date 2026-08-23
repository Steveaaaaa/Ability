package com.steveaaaaa.ability.ability.effect;

import net.minecraft.server.level.ServerPlayer;

public final class AbilityHungerCostService {
    private static final double EXHAUSTION_PER_FOOD_POINT = 4.0D;

    private AbilityHungerCostService() {
    }

    public static void applyFoodPointCost(ServerPlayer player, double foodPointCost) {
        CostParts parts = splitFoodPointCost(foodPointCost);
        if (parts.wholeFoodPoints() > 0) {
            player.getFoodData().setFoodLevel(Math.max(
                    0,
                    player.getFoodData().getFoodLevel() - parts.wholeFoodPoints()
            ));
        }
        if (parts.fractionalExhaustion() > 0.0F) {
            player.causeFoodExhaustion(parts.fractionalExhaustion());
        }
    }

    static CostParts splitFoodPointCost(double foodPointCost) {
        if (!Double.isFinite(foodPointCost) || foodPointCost <= 0.0D) {
            return new CostParts(0, 0.0F);
        }
        double clamped = Math.min(foodPointCost, Integer.MAX_VALUE);
        int whole = (int) Math.floor(clamped);
        float fractionalExhaustion = (float) ((clamped - whole) * EXHAUSTION_PER_FOOD_POINT);
        return new CostParts(whole, fractionalExhaustion);
    }

    record CostParts(int wholeFoodPoints, float fractionalExhaustion) {
    }
}
