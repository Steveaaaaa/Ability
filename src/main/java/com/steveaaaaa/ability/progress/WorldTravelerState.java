package com.steveaaaaa.ability.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;

public record WorldTravelerState(Optional<GlobalPos> boundContainer, List<FilterEntry> filters) {
    public static final int FILTER_SLOTS = 36;
    public static final WorldTravelerState EMPTY = new WorldTravelerState(Optional.empty(), List.of());
    public static final Codec<WorldTravelerState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.optionalFieldOf("bound_container").forGetter(WorldTravelerState::boundContainer),
            FilterEntry.CODEC.listOf().optionalFieldOf("filters", List.of()).forGetter(WorldTravelerState::filters)
    ).apply(instance, WorldTravelerState::new));

    public WorldTravelerState {
        boundContainer = boundContainer == null ? Optional.empty() : boundContainer;
        ArrayList<FilterEntry> sanitized = new ArrayList<>();
        boolean[] occupied = new boolean[FILTER_SLOTS];
        for (FilterEntry entry : filters) {
            if (entry.slot() < 0 || entry.slot() >= FILTER_SLOTS || occupied[entry.slot()]) continue;
            occupied[entry.slot()] = true;
            sanitized.add(new FilterEntry(entry.slot(), entry.item()));
        }
        sanitized.sort(Comparator.comparingInt(FilterEntry::slot));
        filters = List.copyOf(sanitized);
    }

    public ItemStack filter(int slot) {
        return filters.stream().filter(entry -> entry.slot() == slot)
                .map(entry -> new ItemStack(entry.item())).findFirst().orElse(ItemStack.EMPTY);
    }

    public WorldTravelerState bind(GlobalPos target) {
        return new WorldTravelerState(Optional.of(target), filters);
    }

    public WorldTravelerState setFilter(int slot, ItemStack stack) {
        if (slot < 0 || slot >= FILTER_SLOTS) return this;
        ArrayList<FilterEntry> updated = new ArrayList<>(filters);
        updated.removeIf(entry -> entry.slot() == slot);
        if (!stack.isEmpty()) updated.add(new FilterEntry(slot, stack.getItem()));
        return new WorldTravelerState(boundContainer, updated);
    }

    public record FilterEntry(int slot, Item item) {
        public static final Codec<FilterEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, FILTER_SLOTS - 1).fieldOf("slot").forGetter(FilterEntry::slot),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(FilterEntry::item)
        ).apply(instance, FilterEntry::new));
    }
}
