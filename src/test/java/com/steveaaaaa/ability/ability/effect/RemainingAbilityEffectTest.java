package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class RemainingAbilityEffectTest {
    @Test
    void registersEveryBuildingAndMagicEffect() {
        for (var type : List.of(
                ChorusTransmutationEffect.TYPE, CeilingWireEffect.TYPE, WorldTravelerEffect.TYPE,
                GolemEnhancementEffect.CRUSHING_BLOW, EnchantedEdgeEffect.TYPE,
                GolemEnhancementEffect.OBSIDIAN_REINFORCEMENT, GolemEnhancementEffect.COLD_CURRENT
        )) assertTrue(AbilityEffectTypeRegistry.isRegistered(type), type::toString);
    }

    @Test
    void transmutationHonorsRankAndDirection() {
        var twoWay = new ChorusTransmutationEffect.Recipe(2, Blocks.SAND, Blocks.SOUL_SAND, true, 0, 0);
        assertTrue(ChorusTransmutationEffect.outputFor(Blocks.SAND, 1, twoWay).isEmpty());
        assertEquals(Blocks.SOUL_SAND, ChorusTransmutationEffect.outputFor(Blocks.SAND, 2, twoWay).orElseThrow());
        assertEquals(Blocks.SAND, ChorusTransmutationEffect.outputFor(Blocks.SOUL_SAND, 2, twoWay).orElseThrow());
        var oneWay = new ChorusTransmutationEffect.Recipe(6, Blocks.IRON_ORE, Blocks.GOLD_ORE, false, 1, 5);
        assertTrue(ChorusTransmutationEffect.outputFor(Blocks.GOLD_ORE, 6, oneWay).isEmpty());
    }

    @Test
    void coldCurrentUnlocksAndScalesSnowballDamage() {
        assertEquals(0.0F, GolemEnhancementEffect.coldProjectileDamage(599, 0, 1.0D));
        assertEquals(4.0F, GolemEnhancementEffect.coldProjectileDamage(600, 0, 1.0D));
        assertEquals(8.0F, GolemEnhancementEffect.coldProjectileDamage(1800, 0, 1.0D));
        assertEquals(4.0F, GolemEnhancementEffect.coldProjectileDamage(400, 200, 0.4D));
    }

    @Test
    void worldTravelerMatchesTheRecordedItemType() {
        assertTrue(WorldTravelerEffect.matches(Items.DIAMOND, new net.minecraft.world.item.ItemStack(Items.DIAMOND)));
        assertTrue(!WorldTravelerEffect.matches(Items.DIAMOND, new net.minecraft.world.item.ItemStack(Items.EMERALD)));
    }
}
