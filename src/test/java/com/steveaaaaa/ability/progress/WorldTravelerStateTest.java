package com.steveaaaaa.ability.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class WorldTravelerStateTest {
    @Test
    void filterSlotsStoreOneItemWithoutConsumingTheSource() {
        ItemStack source = new ItemStack(Items.DIAMOND, 32);
        WorldTravelerState state = WorldTravelerState.EMPTY.setFilter(7, source);
        assertEquals(32, source.getCount());
        assertEquals(1, state.filter(7).getCount());
        assertTrue(state.filter(6).isEmpty());
        assertTrue(state.setFilter(7, ItemStack.EMPTY).filter(7).isEmpty());
    }

    @Test
    void codecRoundTripsBindingAndFilters() {
        WorldTravelerState original = new WorldTravelerState(
                Optional.of(GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, -4))),
                java.util.List.of(new WorldTravelerState.FilterEntry(3, Items.IRON_INGOT))
        );
        var encoded = WorldTravelerState.CODEC.encodeStart(JsonOps.INSTANCE, original).result().orElseThrow();
        WorldTravelerState decoded = WorldTravelerState.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(original.boundContainer(), decoded.boundContainer());
        assertTrue(decoded.filter(3).is(Items.IRON_INGOT));
    }
}
