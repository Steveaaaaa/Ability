package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

public final class AssociatedOreEffect {
    private AssociatedOreEffect() {
    }

    public static void apply(BlockDropsEvent event, Config config, RankValues values) {
        ItemStack tool = event.getTool();
        TagKey<Item> requiredTool = TagKey.create(Registries.ITEM, config.requiredToolTag());
        if (!tool.is(requiredTool) || (config.requireUnenchantedTool() && tool.isEnchanted())) {
            return;
        }

        BlockState state = event.getState();
        ResourceLocation bonusItem = null;
        if (state.is(TagKey.create(Registries.BLOCK, config.coalOreTag()))) {
            CoalBonus coalBonus = selectCoalBonus(
                    event.getLevel().getRandom().nextDouble(),
                    event.getLevel().getRandom().nextDouble(),
                    event.getLevel().getRandom().nextDouble(),
                    values.coalBonusChance().orElse(0.0D),
                    values.rareReplacementChance().orElse(0.0D),
                    values.emeraldWeight().orElse(0.0D)
            );
            bonusItem = switch (coalBonus) {
                case NONE -> null;
                case COAL -> config.coalItem();
                case DIAMOND -> config.diamondItem();
                case EMERALD -> config.emeraldItem();
            };
        } else if (state.is(TagKey.create(Registries.BLOCK, config.copperOreTag()))
                && rolls(event, values.copperToRawIronChance())) {
            bonusItem = config.rawIronItem();
        } else if (state.is(TagKey.create(Registries.BLOCK, config.ironOreTag()))
                && rolls(event, values.ironToRawGoldChance())) {
            bonusItem = config.rawGoldItem();
        } else if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(config.netherGoldOre())
                && rolls(event, values.netherGoldBonusChance())) {
            bonusItem = config.rawGoldItem();
        }

        if (bonusItem != null) {
            ResourceLocation selectedItem = bonusItem;
            Item item = BuiltInRegistries.ITEM.getOptional(selectedItem)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown bonus item: " + selectedItem));
            ItemEntity drop = new ItemEntity(
                    event.getLevel(),
                    event.getPos().getX() + 0.5D,
                    event.getPos().getY() + 0.5D,
                    event.getPos().getZ() + 0.5D,
                    new ItemStack(item)
            );
            drop.setDefaultPickUpDelay();
            event.getDrops().add(drop);
        }
    }

    private static boolean rolls(BlockDropsEvent event, Optional<Double> chance) {
        return chance.isPresent() && event.getLevel().getRandom().nextDouble() < chance.get();
    }

    public static CoalBonus selectCoalBonus(
            double bonusRoll,
            double replacementRoll,
            double outputRoll,
            double bonusChance,
            double replacementChance,
            double emeraldWeight
    ) {
        if (bonusRoll >= bonusChance) {
            return CoalBonus.NONE;
        }
        if (replacementRoll >= replacementChance) {
            return CoalBonus.COAL;
        }
        return outputRoll < emeraldWeight ? CoalBonus.EMERALD : CoalBonus.DIAMOND;
    }

    public enum CoalBonus {
        NONE,
        COAL,
        DIAMOND,
        EMERALD
    }

    public record Config(
            ResourceLocation requiredToolTag,
            boolean requireUnenchantedTool,
            ResourceLocation coalOreTag,
            ResourceLocation copperOreTag,
            ResourceLocation ironOreTag,
            ResourceLocation netherGoldOre,
            ResourceLocation coalItem,
            ResourceLocation rawIronItem,
            ResourceLocation rawGoldItem,
            ResourceLocation diamondItem,
            ResourceLocation emeraldItem
    ) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("required_tool_tag").forGetter(Config::requiredToolTag),
                Codec.BOOL.optionalFieldOf("require_unenchanted_tool", true)
                        .forGetter(Config::requireUnenchantedTool),
                ResourceLocation.CODEC.fieldOf("coal_ore_tag").forGetter(Config::coalOreTag),
                ResourceLocation.CODEC.fieldOf("copper_ore_tag").forGetter(Config::copperOreTag),
                ResourceLocation.CODEC.fieldOf("iron_ore_tag").forGetter(Config::ironOreTag),
                ResourceLocation.CODEC.fieldOf("nether_gold_ore").forGetter(Config::netherGoldOre),
                ResourceLocation.CODEC.fieldOf("coal_item").forGetter(Config::coalItem),
                ResourceLocation.CODEC.fieldOf("raw_iron_item").forGetter(Config::rawIronItem),
                ResourceLocation.CODEC.fieldOf("raw_gold_item").forGetter(Config::rawGoldItem),
                ResourceLocation.CODEC.fieldOf("diamond_item").forGetter(Config::diamondItem),
                ResourceLocation.CODEC.fieldOf("emerald_item").forGetter(Config::emeraldItem)
        ).apply(instance, Config::new));
    }

    public record RankValues(
            Optional<Double> coalBonusChance,
            Optional<Double> copperToRawIronChance,
            Optional<Double> ironToRawGoldChance,
            Optional<Double> netherGoldBonusChance,
            Optional<Double> rareReplacementChance,
            Optional<Double> emeraldWeight
    ) {
        public static final Codec<RankValues> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                chance("coal_bonus_chance").forGetter(RankValues::coalBonusChance),
                chance("copper_to_raw_iron_chance").forGetter(RankValues::copperToRawIronChance),
                chance("iron_to_raw_gold_chance").forGetter(RankValues::ironToRawGoldChance),
                chance("nether_gold_bonus_chance").forGetter(RankValues::netherGoldBonusChance),
                chance("rare_replacement_chance").forGetter(RankValues::rareReplacementChance),
                chance("emerald_weight").forGetter(RankValues::emeraldWeight)
        ).apply(instance, RankValues::new));

        private static MapCodec<Optional<Double>> chance(String field) {
            return Codec.doubleRange(0.0D, 1.0D).optionalFieldOf(field);
        }

        public static RankValues merge(RankValues earlier, RankValues later) {
            return new RankValues(
                    later.coalBonusChance.or(() -> earlier.coalBonusChance),
                    later.copperToRawIronChance.or(() -> earlier.copperToRawIronChance),
                    later.ironToRawGoldChance.or(() -> earlier.ironToRawGoldChance),
                    later.netherGoldBonusChance.or(() -> earlier.netherGoldBonusChance),
                    later.rareReplacementChance.or(() -> earlier.rareReplacementChance),
                    later.emeraldWeight.or(() -> earlier.emeraldWeight)
            );
        }
    }
}
