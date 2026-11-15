package com.multiverse.mod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

/** All server-side hooks: ticking, damage rules for Tear-A, and immunities. */
public class ServerEvents {
    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("6f1c2a7e-3b4d-4e8f-9a10-6b2c7d8e9f01");
    private static final double TEAR_A_EXTRA_HEALTH = 40.0; // +2 rows on top of vanilla's 1 row = 3 rows

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerTasks.tick(server.getTickCount());
        HomelanderPowers.tick(server);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            FlightHandler.serverTick(p);
            applyTearAHealth(p);
            if (Heroes.isWearingHomelander(p)) p.clearFire();
        }
    }

    private static void applyTearAHealth(ServerPlayer p) {
        AttributeInstance maxHealth = p.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        boolean suit = Heroes.isWearingHomelander(p);
        boolean has = maxHealth.getModifier(HEALTH_MODIFIER_ID) != null;
        if (suit && !has) {
            maxHealth.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER_ID, "multiverse_tear_a",
                    TEAR_A_EXTRA_HEALTH, AttributeModifier.Operation.ADDITION));
        } else if (!suit && has) {
            maxHealth.removeModifier(HEALTH_MODIFIER_ID);
        }
    }

    /** Tear-A: 4x damage reduction taken, 3.5-heart fists dealt. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        float amount = event.getAmount();

        if (event.getSource().getEntity() instanceof Player attacker
                && event.getSource().getDirectEntity() == attacker
                && Heroes.isWearingHomelander(attacker)
                && attacker.getMainHandItem().isEmpty()) {
            amount = 7.0F; // 3.5 hearts per fist hit
        }

        if (Heroes.isWearingHomelander(event.getEntity())) {
            amount = amount / 4.0F;
        }

        event.setAmount(amount);
    }

    /** Tear-A: no fall damage. */
    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (Heroes.isWearingHomelander(event.getEntity())) {
            event.setDistance(0);
        }
    }

    /** Burnout makes the user untouchable. Tear-A is immune to fire. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.hasEffect(MultiverseEffects.BURNOUT.get())) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_FIRE) && Heroes.isWearingHomelander(victim)) {
            event.setCanceled(true);
        }
    }

    /** Tear-A is immune to poison and wither. */
    @SubscribeEvent
    public void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!Heroes.isWearingHomelander(event.getEntity())) return;
        var effect = event.getEffectInstance().getEffect();
        if (effect == MobEffects.POISON || effect == MobEffects.WITHER) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Projectiles bounce off Tear-A. Approximated by reversing the projectile and cancelling the hit. */
    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity target) || !Heroes.isWearingHomelander(target)) return;
        Projectile projectile = event.getProjectile();
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-0.5));
        projectile.hasImpulse = true;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            HomelanderPowers.clear(p.getUUID());
        }
    }
}
