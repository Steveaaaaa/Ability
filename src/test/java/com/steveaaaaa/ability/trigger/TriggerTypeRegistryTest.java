package com.steveaaaaa.ability.trigger;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TriggerTypeRegistryTest {
    @Test
    void registersBuiltInTriggerTypes() {
        assertTrue(TriggerTypeRegistry.isRegistered(TriggerTypeRegistry.BREAK_BLOCK));
        assertTrue(TriggerTypeRegistry.isRegistered(TriggerTypeRegistry.KILL_ENTITY));
        assertTrue(TriggerTypeRegistry.isRegistered(TriggerTypeRegistry.HARVEST_CROP));
        assertTrue(TriggerTypeRegistry.isRegistered(TriggerTypeRegistry.BREED_ANIMAL));
        assertTrue(TriggerTypeRegistry.isRegistered(TriggerTypeRegistry.PLACE_BLOCK));
        assertTrue(TriggerTypeRegistry.isRegistered(TriggerTypeRegistry.TRAVEL));
        assertTrue(TriggerTypeRegistry.isRegistered(TriggerTypeRegistry.RANGED_KILL));
        assertTrue(TriggerTypeRegistry.isRegistered(TriggerTypeRegistry.TAKE_DAMAGE));
        assertTrue(TriggerTypeRegistry.isRegistered(TriggerTypeRegistry.ENCHANT_ITEM));
    }
}
