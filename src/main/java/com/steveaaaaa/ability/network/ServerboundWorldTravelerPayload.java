package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundWorldTravelerPayload(Action action, int slot) implements CustomPacketPayload {
    public static final Type<ServerboundWorldTravelerPayload> TYPE =
            new Type<>(AbilityMod.id("world_traveler_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundWorldTravelerPayload> STREAM_CODEC =
            StreamCodec.ofMember(ServerboundWorldTravelerPayload::encode, ServerboundWorldTravelerPayload::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(action.ordinal());
        buffer.writeVarInt(slot);
    }
    private static ServerboundWorldTravelerPayload decode(RegistryFriendlyByteBuf buffer) {
        int action = buffer.readVarInt();
        if (action < 0 || action >= Action.values().length) throw new DecoderException("Invalid world traveler action");
        return new ServerboundWorldTravelerPayload(Action.values()[action], buffer.readVarInt());
    }
    public enum Action { REQUEST, SET_FILTER, CLEAR_FILTER, OPEN_REMOTE }
}
