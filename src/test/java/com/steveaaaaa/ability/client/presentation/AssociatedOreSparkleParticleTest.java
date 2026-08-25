package com.steveaaaaa.ability.client.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AssociatedOreSparkleParticleTest {
    @Test
    void fadesContinuouslyInsteadOfDisappearingAtOnce() {
        float start = AssociatedOreSparkleParticle.alphaMultiplier(0, 30);
        float early = AssociatedOreSparkleParticle.alphaMultiplier(5, 30);
        float middle = AssociatedOreSparkleParticle.alphaMultiplier(15, 30);
        float late = AssociatedOreSparkleParticle.alphaMultiplier(25, 30);

        assertEquals(1.0F, start);
        assertTrue(start > early);
        assertTrue(early > middle);
        assertTrue(middle > late);
        assertEquals(0.0F, AssociatedOreSparkleParticle.alphaMultiplier(30, 30));
    }

    @Test
    void invalidLifetimeIsAlreadyInvisible() {
        assertEquals(0.0F, AssociatedOreSparkleParticle.alphaMultiplier(0, 0));
    }
}
