package com.steveaaaaa.ability.network;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;

public final class ClientWorldTravelerCache {
    private static volatile ClientboundWorldTravelerStatePayload state =
            new ClientboundWorldTravelerStatePayload(Optional.empty(), List.of());
    private ClientWorldTravelerCache() {}
    public static void accept(ClientboundWorldTravelerStatePayload payload) { state = payload; }
    public static Optional<GlobalPos> boundContainer() { return state.boundContainer(); }
    public static ItemStack filter(int slot) {
        return state.filters().stream().filter(entry -> entry.slot() == slot)
                .map(entry -> new ItemStack(entry.item())).findFirst().orElse(ItemStack.EMPTY);
    }
    public static void clear() { state = new ClientboundWorldTravelerStatePayload(Optional.empty(), List.of()); }
}
