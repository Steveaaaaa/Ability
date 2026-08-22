package com.steveaaaaa.ability.data.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbilityDefinitionTest {
    private final AbilityDefinition.Ranks ranks = new AbilityDefinition.Ranks(
            List.of(12, 15, 18, 21, 24),
            List.of(value(), value(), value(), value(), value())
    );

    @Test
    void resolvesRankFromOwningSkillLevel() {
        assertEquals(0, ranks.rankForSkillLevel(11));
        assertEquals(1, ranks.rankForSkillLevel(12));
        assertEquals(1, ranks.rankForSkillLevel(14));
        assertEquals(3, ranks.rankForSkillLevel(18));
        assertEquals(5, ranks.rankForSkillLevel(24));
        assertEquals(5, ranks.rankForSkillLevel(100));
    }

    private static Dynamic<?> value() {
        return new Dynamic<>(JsonOps.INSTANCE, new JsonObject());
    }
}
