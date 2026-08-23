package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

class CeilingWireEffectTest {
    @Test
    void distinguishesAirSolidAndNonSolidCeilings() {
        assertEquals(
                CeilingWireEffect.CeilingSupport.AIR,
                CeilingWireEffect.ceilingSupport(
                        Blocks.AIR.defaultBlockState(), EmptyBlockGetter.INSTANCE, BlockPos.ZERO
                )
        );
        assertEquals(
                CeilingWireEffect.CeilingSupport.SOLID,
                CeilingWireEffect.ceilingSupport(
                        Blocks.STONE.defaultBlockState(), EmptyBlockGetter.INSTANCE, BlockPos.ZERO
                )
        );
        assertEquals(
                CeilingWireEffect.CeilingSupport.NON_SOLID,
                CeilingWireEffect.ceilingSupport(
                        Blocks.TORCH.defaultBlockState(), EmptyBlockGetter.INSTANCE, BlockPos.ZERO
                )
        );
    }

    @Test
    void firstDripstoneReleaseBypassesCooldownSentinelWithoutOverflow() {
        assertTrue(CeilingWireEffect.isReleaseReady(100L, Long.MIN_VALUE, 10));
        assertFalse(CeilingWireEffect.isReleaseReady(109L, 100L, 10));
        assertTrue(CeilingWireEffect.isReleaseReady(110L, 100L, 10));
    }

    @Test
    void dripstoneStartsBelowPlayerAtPlacementHorizontalCoordinates() {
        assertEquals(
                new BlockPos(12, 63, -8),
                CeilingWireEffect.releaseOrigin(new BlockPos(2, 64, 3), new BlockPos(12, 20, -8))
        );
    }
}
