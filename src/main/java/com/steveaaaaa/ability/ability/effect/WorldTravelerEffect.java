package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.menu.WorldTravelerRemoteMenu;
import com.steveaaaaa.ability.network.ClientboundWorldTravelerStatePayload;
import com.steveaaaaa.ability.network.ClientboundWorldTravelerVisualPayload;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.presentation.AbilityPresentationService;
import com.steveaaaaa.ability.progress.ModAttachments;
import com.steveaaaaa.ability.progress.WorldTravelerState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WorldTravelerEffect {
    public static final net.minecraft.resources.ResourceLocation TYPE = AbilityMod.id("world_traveler");
    private WorldTravelerEffect() {}

    public static void bind(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND
                || !player.isShiftKeyDown() || !event.getItemStack().isEmpty()) return;
        ActiveComponent component = activeComponent(player).orElse(null);
        if (component == null) return;
        IItemHandler handler = findHandler((ServerLevel) event.getLevel(), event.getPos());
        if (handler == null || handler.getSlots() <= 0) return;
        WorldTravelerState updated = state(player).bind(GlobalPos.of(player.level().dimension(), event.getPos()));
        player.setData(ModAttachments.WORLD_TRAVELER_STATE, updated);
        sync(player);
        sendVisual(player, component, ClientboundWorldTravelerVisualPayload.Action.BIND,
                new Target((ServerLevel) player.level(), event.getPos(), handler), Items.AIR, 0);
        player.displayClientMessage(Component.translatable("message.ability.world_traveler.bound",
                event.getPos().getX(), event.getPos().getY(), event.getPos().getZ()), true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    public static void routePickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ActiveComponent component = activeComponent(player).orElse(null);
        if (component == null) return;
        WorldTravelerState state = state(player);
        ItemStack picked = event.getItemEntity().getItem();
        if (picked.isEmpty() || state.boundContainer().isEmpty()
                || state.filters().stream().noneMatch(entry -> matches(entry.item(), picked))) return;
        Target target = target(player, state.boundContainer().get(), component.rank().crossDimension()).orElse(null);
        if (target == null) return;
        net.minecraft.world.item.Item routedItem = picked.getItem();
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(target.handler(), picked.copy(), false);
        int inserted = picked.getCount() - remainder.getCount();
        picked.setCount(remainder.getCount());
        if (inserted > 0) {
            sendVisual(player, component, ClientboundWorldTravelerVisualPayload.Action.ROUTE,
                    target, routedItem, inserted);
        }
    }

    public static void requestState(ServerPlayer player) {
        if (activeComponent(player).isPresent()) sync(player);
    }

    public static void setFilter(ServerPlayer player, int slot, boolean clear) {
        ActiveComponent component = activeComponent(player).orElse(null);
        if (component == null || slot < 0 || slot >= Math.min(WorldTravelerState.FILTER_SLOTS,
                component.config().maxFilterSlots())) return;
        ItemStack exemplar = clear ? ItemStack.EMPTY : player.containerMenu.getCarried();
        if (!clear && exemplar.isEmpty()) return;
        WorldTravelerState updated = state(player).setFilter(slot, exemplar);
        player.setData(ModAttachments.WORLD_TRAVELER_STATE, updated);
        sync(player);
    }

    public static void openRemote(ServerPlayer player) {
        ActiveComponent component = activeComponent(player).orElse(null);
        WorldTravelerState state = state(player);
        if (component == null || !component.config().remoteAccess() || state.boundContainer().isEmpty()) return;
        Target target = target(player, state.boundContainer().get(), component.rank().crossDimension()).orElse(null);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.ability.world_traveler.unavailable"), true);
            return;
        }
        int slots = Math.min(54, target.handler().getSlots());
        sendVisual(player, component, ClientboundWorldTravelerVisualPayload.Action.REMOTE_OPEN,
                target, Items.AIR, 0);
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new WorldTravelerRemoteMenu(id, inventory, target.handler(), slots,
                        () -> activeComponent(player).isPresent() && target.level().hasChunkAt(target.position())),
                Component.translatable("menu.ability.world_traveler.remote")
        ), buffer -> buffer.writeVarInt(slots));
    }

    public static WorldTravelerState state(ServerPlayer player) {
        return player.getData(ModAttachments.WORLD_TRAVELER_STATE);
    }

    static boolean matches(net.minecraft.world.item.Item filter, ItemStack candidate) {
        return candidate.is(filter);
    }


    private static void sendVisual(ServerPlayer player, ActiveComponent component,
            ClientboundWorldTravelerVisualPayload.Action action, Target target,
            net.minecraft.world.item.Item item, int count) {
        boolean crossDimension = !player.level().dimension().equals(target.level().dimension());
        long seed = player.getRandom().nextLong();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new ClientboundWorldTravelerVisualPayload(
                        action,
                        player.getId(),
                        target.level().dimension().location(),
                        target.position(),
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item),
                        count,
                        crossDimension,
                        seed
                ));
        net.minecraft.resources.ResourceLocation cueId = switch (action) {
            case BIND -> AbilityMod.id("bind");
            case ROUTE -> AbilityMod.id("route");
            case REMOTE_OPEN -> AbilityMod.id("remote_open");
        };
        AbilityPresentationService.sendTracking(player, AbilityCue.pulse(
                component.abilityId(), cueId, player.getId(), player.getId(),
                action == ClientboundWorldTravelerVisualPayload.Action.BIND
                        ? target.position().getCenter() : player.position(),
                net.minecraft.world.phys.Vec3.ZERO,
                component.abilityRank(),
                seed
        ));
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        try {
            parse(Config.CODEC, definition.effect().config(), "effect.config");
            for (int i = 0; i < definition.ranks().values().size(); i++)
                parse(Rank.CODEC, definition.ranks().values().get(i), "ranks.values[" + i + "]");
            return List.of();
        } catch (RuntimeException exception) { return List.of(exception.getMessage()); }
    }

    private static Optional<Target> target(ServerPlayer player, GlobalPos pos, boolean crossDimension) {
        if (!crossDimension && !player.level().dimension().equals(pos.dimension())) return Optional.empty();
        ServerLevel level = player.getServer().getLevel(pos.dimension());
        if (level == null) return Optional.empty();
        // Load only on an actual routing/open request. No permanent forced-chunk ticket is installed.
        level.getChunk(pos.pos());
        IItemHandler handler = findHandler(level, pos.pos());
        return handler == null ? Optional.empty() : Optional.of(new Target(level, pos.pos(), handler));
    }

    private static IItemHandler findHandler(ServerLevel level, net.minecraft.core.BlockPos pos) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) return handler;
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            if (handler != null) return handler;
        }
        return null;
    }

    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, ClientboundWorldTravelerStatePayload.from(state(player)));
    }

    private static Optional<ActiveComponent> activeComponent(ServerPlayer player) {
        Registry<AbilityDefinition> registry = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        ArrayList<ActiveComponent> result = new ArrayList<>();
        registry.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().location())).forEach(entry -> {
            Optional<AbilityService.ActiveAbility> active = AbilityService.active(player, entry.getKey().location());
            if (active.isEmpty()) return;
            for (CompositeEffect.ComponentView view : CompositeEffect.componentsOfType(entry.getValue(), TYPE)) {
                AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active.get(), view);
                result.add(new ActiveComponent(
                        entry.getKey().location(),
                        projected.rank(),
                        parse(Config.CODEC, view.config(), "effect.config"),
                        parse(Rank.CODEC, projected.unlockedRankValues().getLast(), "rank")
                ));
            }
        });
        return result.stream().findFirst();
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder();
        Optional<T> result = codec.parse(input).resultOrPartial(error::append);
        return result.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }

    public record Config(int maxFilterSlots, boolean remoteAccess) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, WorldTravelerState.FILTER_SLOTS).optionalFieldOf("max_filter_slots", 36)
                        .forGetter(Config::maxFilterSlots),
                Codec.BOOL.optionalFieldOf("remote_access", true).forGetter(Config::remoteAccess)
        ).apply(instance, Config::new));
    }
    public record Rank(boolean crossDimension) {
        public static final Codec<Rank> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("cross_dimension", false).forGetter(Rank::crossDimension)
        ).apply(instance, Rank::new));
    }
    private record ActiveComponent(
            net.minecraft.resources.ResourceLocation abilityId,
            int abilityRank,
            Config config,
            Rank rank
    ) {}
    private record Target(ServerLevel level, net.minecraft.core.BlockPos position, IItemHandler handler) {}
}
