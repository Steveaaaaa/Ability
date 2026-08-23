package com.steveaaaaa.ability.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ActiveAbilityInputTest {
    @Test
    void roundTripsEveryNetworkInputIncludingFourDodgeDirections() {
        for (ActiveAbilityInput input : ActiveAbilityInput.values()) {
            assertEquals(input, ActiveAbilityInput.fromNetworkId(input.networkId()));
        }
        assertThrows(IllegalArgumentException.class, () -> ActiveAbilityInput.fromNetworkId(255));
    }
}
