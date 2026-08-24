package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record ClientboundAbilityCuePayload(AbilityCue cue) implements CustomPacketPayload {
    public static final Type<ClientboundAbilityCuePayload> TYPE = new Type<>(AbilityMod.id("ability_cue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAbilityCuePayload> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundAbilityCuePayload::encode, ClientboundAbilityCuePayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation.STREAM_CODEC.encode(buffer, cue.abilityId());
        ResourceLocation.STREAM_CODEC.encode(buffer, cue.cueId());
        buffer.writeByte(cue.action().ordinal());
        buffer.writeVarInt(cue.sourceEntityId() + 1);
        buffer.writeVarInt(cue.targetEntityId() + 1);
        writeVec3(buffer, cue.position());
        writeVec3(buffer, cue.direction());
        buffer.writeByte(cue.rank());
        buffer.writeVarInt(cue.durationTicks() + 1);
        buffer.writeVarLong(cue.instanceId());
        buffer.writeLong(cue.randomSeed());
    }

    private static ClientboundAbilityCuePayload decode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation abilityId = ResourceLocation.STREAM_CODEC.decode(buffer);
        ResourceLocation cueId = ResourceLocation.STREAM_CODEC.decode(buffer);
        int actionOrdinal = buffer.readUnsignedByte();
        AbilityCue.Action[] actions = AbilityCue.Action.values();
        if (actionOrdinal >= actions.length) {
            throw new IllegalArgumentException("Unknown ability cue action: " + actionOrdinal);
        }
        return new ClientboundAbilityCuePayload(new AbilityCue(
                abilityId,
                cueId,
                actions[actionOrdinal],
                buffer.readVarInt() - 1,
                buffer.readVarInt() - 1,
                readVec3(buffer),
                readVec3(buffer),
                buffer.readUnsignedByte(),
                buffer.readVarInt() - 1,
                buffer.readVarLong(),
                buffer.readLong()
        ));
    }

    private static void writeVec3(RegistryFriendlyByteBuf buffer, Vec3 value) {
        buffer.writeDouble(value.x);
        buffer.writeDouble(value.y);
        buffer.writeDouble(value.z);
    }

    private static Vec3 readVec3(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
