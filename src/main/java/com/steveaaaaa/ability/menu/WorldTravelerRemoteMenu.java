package com.steveaaaaa.ability.menu;

import java.util.function.BooleanSupplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class WorldTravelerRemoteMenu extends AbstractContainerMenu {
    private final int remoteSlots;
    private final BooleanSupplier valid;

    public WorldTravelerRemoteMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, readClientData(buffer));
    }

    private WorldTravelerRemoteMenu(int id, Inventory inventory, ClientData data) {
        this(id, inventory, new ItemStackHandler(data.slots()), data.slots(), () -> true);
    }

    public WorldTravelerRemoteMenu(int id, Inventory inventory, IItemHandler handler, int slots,
            BooleanSupplier valid) {
        super(ModMenus.WORLD_TRAVELER_REMOTE.get(), id);
        this.remoteSlots = validateSlots(slots);
        this.valid = valid;
        int rows = rows();
        for (int slot = 0; slot < remoteSlots; slot++) {
            addSlot(new SlotItemHandler(handler, slot, 8 + (slot % 9) * 18, 18 + (slot / 9) * 18));
        }
        int playerY = 31 + rows * 18;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, playerY + row * 18));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column, 8 + column * 18, playerY + 58));
    }

    public int remoteSlots() { return remoteSlots; }
    public int rows() { return Math.max(1, (remoteSlots + 8) / 9); }

    @Override public boolean stillValid(Player player) { return valid.getAsBoolean(); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        boolean moved = index < remoteSlots
                ? moveItemStackTo(original, remoteSlots, slots.size(), true)
                : moveItemStackTo(original, 0, remoteSlots, false);
        if (!moved) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, original);
        return copy;
    }

    private static int validateSlots(int slots) {
        if (slots < 1 || slots > 54) throw new IllegalArgumentException("Remote slot count must be between 1 and 54");
        return slots;
    }

    private static ClientData readClientData(RegistryFriendlyByteBuf buffer) {
        return new ClientData(validateSlots(buffer.readVarInt()));
    }

    private record ClientData(int slots) {}
}
