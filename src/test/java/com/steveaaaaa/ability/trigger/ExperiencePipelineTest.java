package com.steveaaaaa.ability.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExperiencePipelineTest {
    @Test
    void scalesAndRoundsRawExperience() {
        assertEquals(13L, ExperiencePipeline.calculateRawXp(5, 2.5D));
    }

    @Test
    void keepsPositiveAwardsAtLeastOne() {
        assertEquals(1L, ExperiencePipeline.calculateRawXp(1, 0.1D));
    }

    @Test
    void rejectsInvalidMultipliers() {
        assertEquals(0L, ExperiencePipeline.calculateRawXp(5, Double.NaN));
        assertEquals(0L, ExperiencePipeline.calculateRawXp(5, Double.POSITIVE_INFINITY));
        assertEquals(0L, ExperiencePipeline.calculateRawXp(5, 0.0D));
    }
}
