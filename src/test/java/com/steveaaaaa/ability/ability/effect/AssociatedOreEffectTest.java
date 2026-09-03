package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssociatedOreEffectTest {
    @Test
    void requiresTheCoalBonusRollBeforeChoosingAnOutput() {
        assertEquals(
                AssociatedOreEffect.CoalBonus.NONE,
                AssociatedOreEffect.selectCoalBonus(0.25D, 0.0D, 0.0D, 0.25D, 0.2D, 0.7D)
        );
        assertEquals(
                AssociatedOreEffect.CoalBonus.COAL,
                AssociatedOreEffect.selectCoalBonus(0.24D, 0.2D, 0.0D, 0.25D, 0.2D, 0.7D)
        );
    }

    @Test
    void replacesSuccessfulCoalBonusUsingConfiguredWeights() {
        assertEquals(
                AssociatedOreEffect.CoalBonus.EMERALD,
                AssociatedOreEffect.selectCoalBonus(0.1D, 0.1D, 0.69D, 0.25D, 0.2D, 0.7D)
        );
        assertEquals(
                AssociatedOreEffect.CoalBonus.DIAMOND,
                AssociatedOreEffect.selectCoalBonus(0.1D, 0.1D, 0.7D, 0.25D, 0.2D, 0.7D)
        );
    }

    @Test
    void laterSparseRanksPreserveEarlierParameters() {
        AssociatedOreEffect.RankValues earlier = new AssociatedOreEffect.RankValues(
                Optional.of(0.25D),
                Optional.of(0.25D),
                Optional.of(0.25D),
                Optional.of(0.25D),
                Optional.empty(),
                Optional.empty()
        );
        AssociatedOreEffect.RankValues finalRank = new AssociatedOreEffect.RankValues(
                Optional.of(0.25D),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(0.2D),
                Optional.of(0.7D)
        );

        AssociatedOreEffect.RankValues merged = AssociatedOreEffect.RankValues.merge(earlier, finalRank);

        assertEquals(Optional.of(0.25D), merged.copperToRawIronChance());
        assertEquals(Optional.of(0.25D), merged.ironToRawGoldChance());
        assertEquals(Optional.of(0.25D), merged.netherGoldBonusChance());
        assertEquals(Optional.of(0.2D), merged.rareReplacementChance());
        assertEquals(Optional.of(0.7D), merged.emeraldWeight());
    }

    @Test
    void registersAssociatedOreAsABlockDropEffect() {
        assertTrue(BlockDropEffectTypeRegistry.isRegistered(
                com.steveaaaaa.ability.AbilityMod.id("associated_ore")
        ));
    }

    @Test
    void decodesTheBuiltInDefinitionWithTypedEffectCodecs() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/data/fantasypower/fantasypower/abilities/associated_ore.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing built-in associated_ore definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            AbilityDefinition definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                    .result()
                    .orElseThrow();
            AssociatedOreEffect.Config.CODEC.parse(definition.effect().config()).result().orElseThrow();
            for (var rank : definition.ranks().values()) {
                AssociatedOreEffect.RankValues.CODEC.parse(rank).result().orElseThrow();
            }
        }
    }
}
