package com.steveaaaaa.ability.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScrollWindowTest {
    @Test
    void clampsOffsetsToAvailableItems() {
        assertEquals(0, ScrollWindow.clamp(-3, 10, 4));
        assertEquals(4, ScrollWindow.clamp(4, 10, 4));
        assertEquals(6, ScrollWindow.clamp(20, 10, 4));
        assertEquals(0, ScrollWindow.clamp(2, 2, 4));
    }

    @Test
    void scrollsOneItemAndStopsAtBothEnds() {
        assertEquals(1, ScrollWindow.scroll(0, -1.0D, 5, 2));
        assertEquals(0, ScrollWindow.scroll(0, 1.0D, 5, 2));
        assertEquals(2, ScrollWindow.scroll(3, 1.0D, 5, 2));
        assertEquals(3, ScrollWindow.scroll(3, -1.0D, 5, 2));
    }
}
