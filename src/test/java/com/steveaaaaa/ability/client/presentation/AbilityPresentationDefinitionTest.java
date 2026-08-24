package com.steveaaaaa.ability.client.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class AbilityPresentationDefinitionTest {
    @Test
    void parsesResourceDrivenCue() {
        AbilityPresentationDefinition definition = AbilityPresentationDefinition.parse(
                JsonParser.parseString("""
                        {"cues":{"ability:activate":{
                          "duration_ticks":20,
                          "emission_interval_ticks":2,
                          "particles":[{"type":"minecraft:end_rod","anchor":"source","count":5}],
                          "sound":{"event":"minecraft:block.note_block.pling"},
                          "animation":"ability:roll",
                          "orbiting_sprites":[{"texture":"ability:textures/particle/stun_star.png"}]
                        }}}
                        """).getAsJsonObject()
        );
        var cue = definition.cues().get(ResourceLocation.fromNamespaceAndPath("ability", "activate"));
        assertEquals(20, cue.durationTicks());
        assertEquals(2, cue.emissionIntervalTicks());
        assertEquals(5, cue.particles().getFirst().count());
        assertEquals(ResourceLocation.fromNamespaceAndPath("ability", "roll"), cue.animation().orElseThrow());
        assertEquals(3, cue.orbitingSprites().getFirst().minimumCount());
    }

    @Test
    void rejectsUnboundedParticleCounts() {
        assertThrows(RuntimeException.class, () -> AbilityPresentationDefinition.parse(
                JsonParser.parseString("""
                        {"cues":{"ability:test":{"particles":[{"type":"minecraft:end_rod","count":513}]}}}
                        """).getAsJsonObject()
        ));
    }
}
