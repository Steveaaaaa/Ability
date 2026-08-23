package com.steveaaaaa.ability.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DodgeAnimationEventsTest {
    @Test
    void derivesWorldYawFromActualHorizontalMotion() {
        assertEquals(0.0F, DodgeAnimationEvents.movementYawDegrees(0.0F, 1.0F), 1.0E-5F);
        assertEquals(90.0F, DodgeAnimationEvents.movementYawDegrees(-1.0F, 0.0F), 1.0E-5F);
        assertEquals(-90.0F, DodgeAnimationEvents.movementYawDegrees(1.0F, 0.0F), 1.0E-5F);
        assertEquals(180.0F, Math.abs(DodgeAnimationEvents.movementYawDegrees(0.0F, -1.0F)), 1.0E-5F);
    }

    @Test
    void wrapsMovementAngleRelativeToCurrentBodyYaw() {
        assertEquals(20.0F, DodgeAnimationEvents.relativeYawDegrees(-170.0F, 170.0F), 1.0E-5F);
        assertEquals(-20.0F, DodgeAnimationEvents.relativeYawDegrees(170.0F, -170.0F), 1.0E-5F);
    }
}
