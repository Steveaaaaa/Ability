package com.steveaaaaa.ability.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClientProgressCacheTest {
    private static final ResourceLocation MINING = ResourceLocation.fromNamespaceAndPath("fantasypower", "mining");

    @AfterEach
    void clearCache() {
        ClientProgressCache.clear();
    }

    @Test
    void acceptsSupportedSnapshotsAndClearsUnsupportedOnes() {
        PlayerProgressSnapshot supported = new PlayerProgressSnapshot(
                PlayerProgressSnapshot.CURRENT_SCHEMA_VERSION,
                Map.of(MINING, new PlayerProgressSnapshot.SkillSnapshot(100L, 1, 1, 0)),
                Map.of(),
                0
        );
        ClientProgressCache.accept(supported);

        assertEquals(supported, ClientProgressCache.snapshot());

        ClientProgressCache.accept(new PlayerProgressSnapshot(99, Map.of(), Map.of(), 0));

        assertEquals(PlayerProgressSnapshot.EMPTY, ClientProgressCache.snapshot());
    }

    @Test
    void storesAndClearsPurchaseFeedback() {
        ClientboundPurchaseResultPayload result = new ClientboundPurchaseResultPayload(
                MINING,
                ClientboundPurchaseResultPayload.Status.SUCCESS,
                2,
                3,
                ""
        );

        ClientProgressCache.acceptPurchaseResult(result);
        assertEquals(result, ClientProgressCache.lastPurchaseResult());

        ClientProgressCache.clearPurchaseResult();
        assertNull(ClientProgressCache.lastPurchaseResult());
    }

    @Test
    void allowsOnlyOnePendingPurchaseAndClearsItOnMatchingResult() {
        ResourceLocation farming = ResourceLocation.fromNamespaceAndPath("fantasypower", "farming");

        assertTrue(ClientProgressCache.beginPurchase(MINING));
        assertEquals(MINING, ClientProgressCache.pendingPurchase());
        assertFalse(ClientProgressCache.beginPurchase(farming));

        ClientProgressCache.acceptPurchaseResult(new ClientboundPurchaseResultPayload(
                farming,
                ClientboundPurchaseResultPayload.Status.UNKNOWN_ABILITY,
                0,
                0,
                ""
        ));
        assertEquals(MINING, ClientProgressCache.pendingPurchase());

        ClientProgressCache.acceptPurchaseResult(new ClientboundPurchaseResultPayload(
                MINING,
                ClientboundPurchaseResultPayload.Status.SUCCESS,
                1,
                0,
                ""
        ));
        assertNull(ClientProgressCache.pendingPurchase());
    }
}
