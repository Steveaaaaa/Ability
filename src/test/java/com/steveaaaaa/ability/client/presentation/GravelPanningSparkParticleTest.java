package com.steveaaaaa.ability.client.presentation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GravelPanningSparkParticleTest {
    @Test
    void slowBurstStaysWithinTwoBlocksAtMaximumSpeedAndLifetime() {
        double travel = GravelPanningSparkParticle.maximumHorizontalTravel();

        assertTrue(travel >= 1.0D, () -> "Expected a visible outward flight, got " + travel);
        assertTrue(travel <= 2.0D, () -> "Expected the flight to stay within two blocks, got " + travel);
    }
}
