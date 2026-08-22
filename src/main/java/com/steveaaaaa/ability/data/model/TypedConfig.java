package com.steveaaaaa.ability.data.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record TypedConfig(ResourceLocation type, Dynamic<?> config) {
    public static final Codec<TypedConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(TypedConfig::type),
            Codec.PASSTHROUGH.fieldOf("config").forGetter(TypedConfig::config)
    ).apply(instance, TypedConfig::new));
}
