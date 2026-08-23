package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class WorldTravelerEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("world_traveler");
    private WorldTravelerEffect() {}

    public static void activate(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = event.getItemStack();
        ActiveComponent component = activeComponents(player).stream()
                .filter(value -> stack.is(value.config().item())).findFirst().orElse(null);
        if (component == null || player.getCooldowns().isOnCooldown(stack.getItem())) return;
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        GlobalPos target = tracker == null ? null : tracker.target().orElse(null);
        if (target == null || (!component.rank().crossDimension()
                && !target.dimension().equals(player.level().dimension()))) return;
        ServerLevel targetLevel = player.getServer().getLevel(target.dimension());
        if (targetLevel == null || !targetLevel.getBlockState(target.pos()).isSolid()) return;
        player.teleportTo(targetLevel, target.pos().getX() + 0.5D, target.pos().getY() + 1.0D,
                target.pos().getZ() + 0.5D, player.getYRot(), player.getXRot());
        player.getCooldowns().addCooldown(stack.getItem(), component.config().cooldownTicks());
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        try {
            parse(Config.CODEC, definition.effect().config(), "effect.config");
            for (int i = 0; i < definition.ranks().values().size(); i++)
                parse(Rank.CODEC, definition.ranks().values().get(i), "ranks.values[" + i + "]");
            return List.of();
        } catch (RuntimeException exception) { return List.of(exception.getMessage()); }
    }

    private static List<ActiveComponent> activeComponents(ServerPlayer player) {
        ArrayList<ActiveComponent> result = new ArrayList<>();
        Registry<AbilityDefinition> registry = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        registry.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().location())).forEach(entry -> {
            Optional<AbilityService.ActiveAbility> active = AbilityService.active(player, entry.getKey().location());
            if (active.isEmpty()) return;
            for (CompositeEffect.ComponentView view : CompositeEffect.componentsOfType(entry.getValue(), TYPE)) {
                AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active.get(), view);
                result.add(new ActiveComponent(parse(Config.CODEC, view.config(), "effect.config"),
                        parse(Rank.CODEC, projected.unlockedRankValues().getLast(), "rank")));
            }
        });
        return result;
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder();
        Optional<T> result = codec.parse(input).resultOrPartial(error::append);
        return result.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }
    public record Config(Item item, int cooldownTicks) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Config::item),
                Codec.intRange(0, 72000).optionalFieldOf("cooldown_ticks", 1200).forGetter(Config::cooldownTicks)
        ).apply(instance, Config::new));
    }
    public record Rank(boolean crossDimension) {
        public static final Codec<Rank> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("cross_dimension", false).forGetter(Rank::crossDimension)
        ).apply(instance, Rank::new));
    }
    private record ActiveComponent(Config config, Rank rank) {}
}
