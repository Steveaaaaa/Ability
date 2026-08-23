package com.steveaaaaa.ability.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import org.junit.jupiter.api.Test;

class DodgeInputEventsTest {
    @Test
    void mapsMovementAxesToEightDirectionsAndDefaultsBackward() {
        assertEquals(ActiveAbilityInput.FORWARD, DodgeInputEvents.directionForAxes(1, 0));
        assertEquals(ActiveAbilityInput.BACKWARD, DodgeInputEvents.directionForAxes(-1, 0));
        assertEquals(ActiveAbilityInput.LEFT, DodgeInputEvents.directionForAxes(0, -1));
        assertEquals(ActiveAbilityInput.RIGHT, DodgeInputEvents.directionForAxes(0, 1));
        assertEquals(ActiveAbilityInput.FORWARD_LEFT, DodgeInputEvents.directionForAxes(1, -1));
        assertEquals(ActiveAbilityInput.FORWARD_RIGHT, DodgeInputEvents.directionForAxes(1, 1));
        assertEquals(ActiveAbilityInput.BACKWARD_LEFT, DodgeInputEvents.directionForAxes(-1, -1));
        assertEquals(ActiveAbilityInput.BACKWARD_RIGHT, DodgeInputEvents.directionForAxes(-1, 1));
        assertEquals(ActiveAbilityInput.BACKWARD, DodgeInputEvents.directionForAxes(0, 0));
    }
}
