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
    void resolvesReductionAndComputesDirectionalMotion() {
        DodgeEffect.ResolvedRank rank = DodgeEffect.resolve(new DodgeEffect.RankValues(Map.of(
                "damage_reduction", 0.9D
        )));

        assertEquals(0.9D, rank.damageReduction());
        Vec3 right = DodgeEffect.directionalMotion(
                new Vec3(0.0D, 0.0D, 1.0D),
                ActiveAbilityInput.RIGHT,
                0.9D
        );
        Vec3 left = DodgeEffect.directionalMotion(
                new Vec3(0.0D, 0.0D, 1.0D),
                ActiveAbilityInput.LEFT,
                0.9D
        );
        assertEquals(-0.9D, right.x, 1.0E-9D);
        assertEquals(0.0D, right.z, 1.0E-9D);
        assertEquals(0.9D, left.x, 1.0E-9D);
        assertEquals(0.0D, left.z, 1.0E-9D);
        Vec3 forward = DodgeEffect.directionalMotion(
                new Vec3(0.0D, 0.0D, 1.0D),
                ActiveAbilityInput.FORWARD,
                0.9D
        );
        Vec3 backward = DodgeEffect.directionalMotion(
                new Vec3(0.0D, 0.0D, 1.0D),
                ActiveAbilityInput.BACKWARD,
                0.9D
        );
        assertEquals(0.9D, forward.z, 1.0E-9D);
        assertEquals(-0.9D, backward.z, 1.0E-9D);
        Vec3 forwardRight = DodgeEffect.directionalMotion(
                new Vec3(0.0D, 0.0D, 1.0D),
                ActiveAbilityInput.FORWARD_RIGHT,
                0.9D
        );
        assertEquals(0.9D, forwardRight.length(), 1.0E-9D);
        assertTrue(forwardRight.x < 0.0D);
        assertTrue(forwardRight.z > 0.0D);
        Vec3 backwardLeft = DodgeEffect.directionalMotion(
                new Vec3(0.0D, 0.0D, 1.0D),
                ActiveAbilityInput.BACKWARD_LEFT,
                0.9D
        );
        assertEquals(0.9D, backwardLeft.length(), 1.0E-9D);
        assertTrue(backwardLeft.x > 0.0D);
        assertTrue(backwardLeft.z < 0.0D);
    }

    @Test
    void acceptsOnlyDedicatedDodgeDirections() {
        assertTrue(DodgeEffect.isDodgeDirection(ActiveAbilityInput.FORWARD));
        assertTrue(DodgeEffect.isDodgeDirection(ActiveAbilityInput.BACKWARD));
        assertTrue(DodgeEffect.isDodgeDirection(ActiveAbilityInput.LEFT));
        assertTrue(DodgeEffect.isDodgeDirection(ActiveAbilityInput.RIGHT));
        assertTrue(DodgeEffect.isDodgeDirection(ActiveAbilityInput.FORWARD_LEFT));
        assertTrue(DodgeEffect.isDodgeDirection(ActiveAbilityInput.FORWARD_RIGHT));
        assertTrue(DodgeEffect.isDodgeDirection(ActiveAbilityInput.BACKWARD_LEFT));
        assertTrue(DodgeEffect.isDodgeDirection(ActiveAbilityInput.BACKWARD_RIGHT));
        assertFalse(DodgeEffect.isDodgeDirection(ActiveAbilityInput.CHARGE_START));
    }

    @Test
    void registersAndValidatesBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = DodgeEffectTest.class.getResourceAsStream(
                "/data/fantasypower/fantasypower/abilities/dodge.json"
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
