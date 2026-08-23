package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DodgeEffectTest {
    @Test
    void resolvesReductionAndComputesLateralMotion() {
        DodgeEffect.ResolvedRank rank = DodgeEffect.resolve(new DodgeEffect.RankValues(Map.of(
                "damage_reduction", 0.9D
        )));

        assertEquals(0.9D, rank.damageReduction());
        Vec3 right = DodgeEffect.lateralMotion(
                new Vec3(0.0D, 0.0D, 1.0D),
                ActiveAbilityInput.RIGHT,
                0.9D
        );
        Vec3 left = DodgeEffect.lateralMotion(
                new Vec3(0.0D, 0.0D, 1.0D),
                ActiveAbilityInput.LEFT,
                0.9D
        );
        assertEquals(-0.9D, right.x, 1.0E-9D);
        assertEquals(0.0D, right.z, 1.0E-9D);
        assertEquals(0.9D, left.x, 1.0E-9D);
        assertEquals(0.0D, left.z, 1.0E-9D);
    }

    @Test
    void requiresActualBackwardServerMovement() {
        Vec3 look = new Vec3(0.0D, 0.0D, 1.0D);

        assertTrue(DodgeEffect.isMovingBackward(look, new Vec3(0.1D, 0.0D, -0.1D), 0.01D));
        assertFalse(DodgeEffect.isMovingBackward(look, new Vec3(0.0D, 0.0D, 0.1D), 0.01D));
        assertFalse(DodgeEffect.isMovingBackward(look, Vec3.ZERO, 0.01D));
    }

    @Test
    void registersAndValidatesBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = DodgeEffectTest.class.getResourceAsStream(
                "/data/ability/ability/abilities/dodge.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing dodge definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(DodgeEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
