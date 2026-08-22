package com.steveaaaaa.ability.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExperienceAntiAbuseServiceTest {
    @Test
    void awardsFullExperienceBelowSoftCap() {
        assertEquals(100L, ExperienceAntiAbuseService.calculateDailyAward(100L, 200L, 500L, 0.1D));
    }

    @Test
    void splitsAwardWhenCrossingSoftCap() {
        assertEquals(55L, ExperienceAntiAbuseService.calculateDailyAward(100L, 450L, 500L, 0.1D));
    }

    @Test
    void reducesEntireAwardAboveSoftCap() {
        assertEquals(10L, ExperienceAntiAbuseService.calculateDailyAward(100L, 500L, 500L, 0.1D));
    }
}
