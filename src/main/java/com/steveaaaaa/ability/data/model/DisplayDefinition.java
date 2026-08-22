package com.steveaaaaa.ability.data.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record DisplayDefinition(
        String name,
        String description,
        ResourceLocation icon,
        String color,
        int sortOrder
) {
    public static final Codec<DisplayDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(DisplayDefinition::name),
            Codec.STRING.fieldOf("description").forGetter(DisplayDefinition::description),
            ResourceLocation.CODEC.fieldOf("icon").forGetter(DisplayDefinition::icon),
            Codec.STRING.optionalFieldOf("color", "#FFFFFF").forGetter(DisplayDefinition::color),
            Codec.INT.optionalFieldOf("sort_order", 0).forGetter(DisplayDefinition::sortOrder)
    ).apply(instance, DisplayDefinition::new));
}
