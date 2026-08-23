package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.network.ClientboundPurchaseResultPayload;
import net.minecraft.network.chat.Component;

final class PurchaseFeedback {
    private PurchaseFeedback() {}

    static Component message(ClientboundPurchaseResultPayload result) {
        String abilityId = result.abilityId().toString();
        return switch (result.status()) {
            case SUCCESS -> Component.translatable(
                    "command.ability.purchase.success", abilityId, result.required(), result.actual());
            case UNKNOWN_ABILITY -> Component.translatable("gui.ability.purchase.unknown", abilityId);
            case ALREADY_PURCHASED -> Component.translatable(
                    "command.ability.purchase.already_purchased", abilityId);
            case SKILL_LEVEL_TOO_LOW -> Component.translatable(
                    "command.ability.purchase.skill_level_too_low", result.required(), result.actual());
            case NOT_ENOUGH_SKILL_POINTS -> Component.translatable(
                    "command.ability.purchase.not_enough_points", result.required(), result.actual());
            case REQUIREMENT_NOT_MET -> Component.translatable(
                    "command.ability.purchase.requirement_not_met", result.detail());
            case INVALID_DEFINITION -> Component.translatable(
                    "command.ability.purchase.invalid_definition", result.detail());
        };
    }
}
