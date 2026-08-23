package com.steveaaaaa.ability.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.steveaaaaa.ability.presentation.AbilityCue;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class ClientboundAbilityCuePayloadTest {
    @Test
    void roundTripsEveryCueField() {
        AbilityCue cue = new AbilityCue(
                id("dodge"),
                id("activate"),
                AbilityCue.Action.START,
                12,
                34,
                new Vec3(1.25D, -2.5D, 8.0D),
                new Vec3(-0.5D, 0.0D, 1.0D),
                7,
                99L,
                123456L
        );
        ClientboundAbilityCuePayload original = new ClientboundAbilityCuePayload(cue);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            ClientboundAbilityCuePayload.STREAM_CODEC.encode(buffer, original);
            assertEquals(original, ClientboundAbilityCuePayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsInvalidCueData() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityCue(
                id("dodge"), id("activate"), AbilityCue.Action.PULSE,
                -2, -1, Vec3.ZERO, Vec3.ZERO, 0, 0L, 0L
        ));
        assertThrows(IllegalArgumentException.class, () -> new AbilityCue(
                id("dodge"), id("activate"), AbilityCue.Action.PULSE,
                -1, -1, new Vec3(Double.NaN, 0.0D, 0.0D), Vec3.ZERO, 0, 0L, 0L
        ));
        assertThrows(IllegalArgumentException.class, () -> new AbilityCue(
                id("dodge"), id("activate"), AbilityCue.Action.PULSE,
                -1, -1, Vec3.ZERO, Vec3.ZERO, 256, 0L, 0L
        ));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ability", path);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }
}
