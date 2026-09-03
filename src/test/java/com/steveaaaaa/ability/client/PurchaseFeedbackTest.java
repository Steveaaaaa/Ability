package com.steveaaaaa.ability.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.steveaaaaa.ability.network.ClientboundPurchaseResultPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PurchaseFeedbackTest {
    @Test
    void everyPurchaseStatusProducesAValidTranslatableComponent() {
        ResourceLocation abilityId = ResourceLocation.fromNamespaceAndPath("fantasypower", "support_aura");
        for (ClientboundPurchaseResultPayload.Status status : ClientboundPurchaseResultPayload.Status.values()) {
            ClientboundPurchaseResultPayload result = new ClientboundPurchaseResultPayload(
                    abilityId, status, 6, 12, "detail");
            assertDoesNotThrow(() -> PurchaseFeedback.message(result), status::name);
        }
    }
}
