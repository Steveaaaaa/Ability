package com.steveaaaaa.ability.ability.effect;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.ActiveAbilityRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.minecraft.world.entity.animal.Wolf;

@EventBusSubscriber(modid = AbilityMod.MOD_ID)
public final class AbilityEffectEventHandler {
    private AbilityEffectEventHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.getBreaker() instanceof ServerPlayer player) {
            BlockDropEffectTypeRegistry.process(event, player);
            LootInjectionEffect.processBlock(event, player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        CeilingWireEffect.preventFallingDripstonePlacement(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            LootInjectionEffect.processEntity(event, player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (EnchantedEdgeEffect.isApplyingConvertedDamage()) return;
        DamageModifierEffect.process(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSpecialDamage(LivingIncomingDamageEvent event) {
        if (EnchantedEdgeEffect.isApplyingConvertedDamage()) return;
        BlastExcavationEffect.reduceSelfDamage(event);
        IronCavalryEffect.modifyOutgoingDamage(event);
        WolfPackEffect.modifyDamage(event);
        CeilingWireEffect.modifyFallingDripstoneDamage(event);
        EnchantedEdgeEffect.modifyDamage(event);
        GolemEnhancementEffect.blockWithObsidianShield(event);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        CeilingWireEffect.releaseDripstone(event);
        if (event.isCanceled()) {
            return;
        }
        WorldTravelerEffect.bind(event);
        if (!event.isCanceled()) {
            ChorusTransmutationEffect.transmute(event);
        }
        if (!event.isCanceled()) {
            BlastExcavationEffect.placeCharge(event);
        }
        if (!event.isCanceled()) {
            SupportAuraEffect.activate(event);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        CeilingWireEffect.releaseDripstone(event);
        if (!event.isCanceled()) {
            SupportAuraEffect.activate(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        WorldTravelerEffect.routePickup(event);
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        GolemEnhancementEffect.enhance(event);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Wolf wolf) {
            WolfPackEffect.processTick(wolf);
        }
        GolemEnhancementEffect.processTick(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHarvestDamage(LivingIncomingDamageEvent event) {
        if (EnchantedEdgeEffect.isApplyingConvertedDamage()) return;
        HarvestEffect.modifyDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChargedLeapFallDamage(LivingIncomingDamageEvent event) {
        if (EnchantedEdgeEffect.isApplyingConvertedDamage()) return;
        ChargedLeapEffect.preventFallDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWellPreparedInvulnerability(LivingIncomingDamageEvent event) {
        if (EnchantedEdgeEffect.isApplyingConvertedDamage()) return;
        WellPreparedEffect.preventDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCounterSniperDamage(LivingIncomingDamageEvent event) {
        if (EnchantedEdgeEffect.isApplyingConvertedDamage()) return;
        CounterSniperEffect.modifyOutgoingDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWeakPointDamage(LivingIncomingDamageEvent event) {
        if (EnchantedEdgeEffect.isApplyingConvertedDamage()) return;
        StealthEffect.modifyOutgoingDamage(event);
        WeakPointEffect.process(event);
        ChargedLeapEffect.replaceImpactDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDodgeDamage(LivingIncomingDamageEvent event) {
        if (EnchantedEdgeEffect.isApplyingConvertedDamage()) return;
        DodgeEffect.reduceIncomingDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPrimerExplosionDamage(LivingIncomingDamageEvent event) {
        if (EnchantedEdgeEffect.isApplyingConvertedDamage()) return;
        PrimerEffect.modifyExplosionDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDangerousChargeDamage(LivingIncomingDamageEvent event) {
        if (EnchantedEdgeEffect.isApplyingConvertedDamage()) return;
        DangerousChargeEffect.modifyFireworkDamage(event);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        DangerousChargeEffect.trackCrossbowRocket(event);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        GolemEnhancementEffect.processSnowballImpact(event);
    }

    @SubscribeEvent
    public static void onFinalDamage(LivingDamageEvent.Post event) {
        AttributeModifierEffect.processFinalDamage(event);
        DamageModifierEffect.processFinalDamage(event);
        DamageResponseEffect.process(event);
        CounterSniperEffect.processFinalDamage(event);
        StealthEffect.processFinalDamage(event);
        ChargedLeapEffect.processImpact(event);
        ExhaustionEffect.process(event);
        HarvestEffect.consumeFood(event);
        GolemEnhancementEffect.processFinalDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        WellPreparedEffect.preventDeath(event);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FrugalityEffect.refundNaturalHealingCost(player);
            SurvivalSkillsEffect.processTick(player);
            RetaliatoryFlameEffect.processTick(player);
            FineFeedEffect.processTick(player);
            SupportAuraEffect.processTick(player);
            CeilingWireEffect.processTick(player);
            if (player.tickCount % 5 == 0) {
                WorldTravelerEffect.processInventoryRouting(player);
            }
            if (player.tickCount % 5 == 0) {
                StealthEffect.processTick(player);
            }
            if (player.tickCount % 10 == 0) {
                ConditionalMobEffect.process(player);
            }
            if (player.tickCount % 20 == 0) {
                AttributeModifierEffect.reconcile(player);
                GreedEffect.reconcile(player);
                IronCavalryEffect.reconcileMountArmor(player);
            }
            if (player.tickCount % 100 == 0) {
                PrimerStateTracker.cleanup(player.level().getGameTime());
                DangerousChargeTracker.cleanup(player.level().getGameTime());
                BlastExcavationEffect.cleanup(player.level().getGameTime());
                CeilingWireEffect.cleanup(player.level().getGameTime());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTickBefore(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FrugalityEffect.captureBeforeTick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttributeModifierEffect.forget(player);
            InactivityTracker.forget(player.getUUID());
            ActiveAbilityRuntime.forget(player.getUUID());
            ChargedLeapStateTracker.forget(player.getUUID());
            PrimerStateTracker.forgetPlayer(player.getUUID());
            FrugalityEffect.forget(player.getUUID());
            WellPreparedTracker.forget(player.getUUID());
            GreedEffect.forget(player);
            IronCavalryEffect.forget(player);
            SupportAuraEffect.forget(player.getUUID());
            WolfPackEffect.forgetOwner(player.getUUID());
            CeilingWireEffect.forget(player);
            WorldTravelerEffect.forget(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CeilingWireEffect.forget(player);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GolemEnhancementEffect.syncObsidianStateTo(player, event.getTarget());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InactivityTracker.recordActivity(player);
            ActiveAbilityRuntime.forget(player.getUUID());
            ChargedLeapStateTracker.forget(player.getUUID());
            PrimerStateTracker.forgetPlayer(player.getUUID());
            FrugalityEffect.forget(player.getUUID());
            WellPreparedTracker.forget(player.getUUID());
            GreedEffect.forget(player);
            IronCavalryEffect.forget(player);
            SupportAuraEffect.forget(player.getUUID());
            WolfPackEffect.forgetOwner(player.getUUID());
            CeilingWireEffect.forget(player);
            WorldTravelerEffect.forget(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InactivityTracker.recordActivity(player);
            ActiveAbilityRuntime.forget(player.getUUID());
            ChargedLeapStateTracker.forget(player.getUUID());
            PrimerStateTracker.forgetPlayer(player.getUUID());
            FrugalityEffect.forget(player.getUUID());
            GreedEffect.forget(player);
            IronCavalryEffect.forget(player);
            SupportAuraEffect.forget(player.getUUID());
            WolfPackEffect.forgetOwner(player.getUUID());
            CeilingWireEffect.forget(player);
            WorldTravelerEffect.forget(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerSound(PlayLevelSoundEvent.AtEntity event) {
        if (!event.isCanceled()
                && event.getOriginalVolume() > 0.0F
                && event.getSound() != null
                && event.getEntity() instanceof ServerPlayer player) {
            InactivityTracker.recordActivity(player);
        }
    }
}
