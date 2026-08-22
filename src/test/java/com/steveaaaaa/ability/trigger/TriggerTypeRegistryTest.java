package com.steveaaaaa.ability.trigger;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TriggerTypeRegistryTest {
    @Test
    void registersBuiltInTriggerTypes() {
        assertTrue(TriggerTypeRegistry.isRegistered(TriggerTypeRegistry.BREAK_BLOCK));
        assertTrue(TriggerTypeRegistry.isRegistered(TriggerTypeRegistry.KILL_ENTITY));
        assertTrue(TriggerTypeRegistry.isRegistered(TriggerTypeRegistry.HARVEST_CROP));
    }
}
