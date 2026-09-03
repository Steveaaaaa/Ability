package com.steveaaaaa.ability.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ActiveAbilityRuntimeTest {
    private static final ResourceLocation DODGE =
            ResourceLocation.fromNamespaceAndPath("fantasypower", "dodge");

    @AfterEach
    void resetRuntime() {
        ActiveAbilityRuntime.resetForTests();
    }

    @Test
    void cooldownAndActiveWindowUseServerGameTime() {
        UUID player = UUID.randomUUID();

        assertTrue(ActiveAbilityRuntime.tryActivate(player, DODGE, 100L, 12, 4, 0.7D));
        assertEquals(12L, ActiveAbilityRuntime.remainingCooldown(player, DODGE, 100L));
        assertEquals(0.7D, ActiveAbilityRuntime.incomingDamageMultiplier(player, 103L), 1.0E-9D);
        assertEquals(1.0D, ActiveAbilityRuntime.incomingDamageMultiplier(player, 104L), 1.0E-9D);
        assertFalse(ActiveAbilityRuntime.tryActivate(player, DODGE, 111L, 12, 4, 0.7D));
        assertTrue(ActiveAbilityRuntime.tryActivate(player, DODGE, 112L, 12, 4, 0.7D));
    }

    @Test
    void activeWindowsAreIsolatedByPlayerAndStackMultiplicatively() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ResourceLocation other = ResourceLocation.fromNamespaceAndPath("example", "other_dodge");

        ActiveAbilityRuntime.tryActivate(first, DODGE, 20L, 5, 4, 0.8D);
        ActiveAbilityRuntime.tryActivate(first, other, 20L, 5, 4, 0.5D);
        ActiveAbilityRuntime.tryActivate(second, DODGE, 20L, 5, 4, 0.9D);

        assertEquals(0.4D, ActiveAbilityRuntime.incomingDamageMultiplier(first, 21L), 1.0E-9D);
        assertEquals(0.9D, ActiveAbilityRuntime.incomingDamageMultiplier(second, 21L), 1.0E-9D);
    }
}
