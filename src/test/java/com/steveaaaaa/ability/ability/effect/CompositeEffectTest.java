package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeEffectTest {
    @Test
    void registersAndValidatesBreakthroughAsAComposite() throws Exception {
        AbilityDefinition definition;
        try (var stream = CompositeEffectTest.class.getResourceAsStream(
                "/data/fantasypower/fantasypower/abilities/breakthrough.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing breakthrough definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(CompositeEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
        List<CompositeEffect.ComponentView> components = CompositeEffect.components(definition);
        assertEquals(2, components.size());
        assertEquals(DamageModifierEffect.DAMAGE_MODIFIER, components.get(0).type());
        assertEquals(DamageResponseEffect.TYPE, components.get(1).type());
        assertEquals(10, components.get(0).rankValues().size());
        assertEquals(10, components.get(1).rankValues().size());
    }
}
