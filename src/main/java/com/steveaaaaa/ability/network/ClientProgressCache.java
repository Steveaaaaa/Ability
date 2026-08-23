package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.resources.ResourceLocation;

public final class ClientProgressCache {
    private static volatile PlayerProgressSnapshot snapshot = PlayerProgressSnapshot.EMPTY;
    private static volatile ClientboundPurchaseResultPayload lastPurchaseResult;
    private static volatile ResourceLocation pendingPurchase;
    private static volatile long uiRevision;

    private ClientProgressCache() {
    }

    public static PlayerProgressSnapshot snapshot() {
        return snapshot;
    }

    public static void accept(PlayerProgressSnapshot updated) {
        if (updated.schemaVersion() != PlayerProgressSnapshot.CURRENT_SCHEMA_VERSION) {
            AbilityMod.LOGGER.error(
                    "Ignoring player progress snapshot with unsupported schema version {}",
                    updated.schemaVersion()
            );
            clear();
            return;
        }
        snapshot = updated;
        uiRevision++;
    }

    public static void clear() {
        snapshot = PlayerProgressSnapshot.EMPTY;
        lastPurchaseResult = null;
        pendingPurchase = null;
        uiRevision++;
    }

    public static ClientboundPurchaseResultPayload lastPurchaseResult() {
        return lastPurchaseResult;
    }

    public static void acceptPurchaseResult(ClientboundPurchaseResultPayload result) {
        lastPurchaseResult = result;
        if (result.abilityId().equals(pendingPurchase)) {
            pendingPurchase = null;
        }
        uiRevision++;
    }

    public static void clearPurchaseResult() {
        if (lastPurchaseResult != null) {
            lastPurchaseResult = null;
            uiRevision++;
        }
    }

    public static ResourceLocation pendingPurchase() {
        return pendingPurchase;
    }

    public static boolean beginPurchase(ResourceLocation abilityId) {
        if (pendingPurchase != null) {
            return false;
        }
        pendingPurchase = abilityId;
        lastPurchaseResult = null;
        uiRevision++;
        return true;
    }

    public static long uiRevision() {
        return uiRevision;
    }
}
