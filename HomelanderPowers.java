package com.multiverse.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Homelander's server-authoritative abilities. Every timer runs in server ticks (20 per second).
 * Slots: 0 laser, 1 clap, 2 launch, 3 grab, 4 fly toggle.
 */
public final class HomelanderPowers {
    public static final int LASER = 0, CLAP = 1, LAUNCH = 2, GRAB = 3, FLY = 4;

    private static final double LASER_RANGE = 30.0;
    private static final long LASER_LIMIT = 60L * 20L;     // 60 s before Burnout
    private static final long BURNOUT_TICKS = 120L;       // 6 s kneel cutscene
    private static final String ESCAPE_LETTERS = "WASDER";
    private static final int ESCAPE_LENGTH = 8;

    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> LASER_START = new HashMap<>();
    private static final Map<UUID, Long> GRAB_HOLD = new HashMap<>();
    private static final Map<UUID, Grab> GRABS = new HashMap<>();
    private static final Random RANDOM = new Random();

    private static final class Grab {
        final UUID target;
        final long startTick;
        final String letters;
        int index;

        Grab(UUID target, long startTick, String letters) {
            this.target = target;
            this.startTick = startTick;
            this.letters = letters;
        }
    }

    private HomelanderPowers() {}

    // ----- entry points -----

    public static void handle(ServerPlayer p, int slot, boolean pressed) {
        if (slot == FLY) {
            if (pressed) FlightHandler.requestFly(p);
            return;
        }
        if (!Heroes.isWearingHomelander(p) || isLocked(p)) return;

        switch (slot) {
            case LASER -> laser(p, pressed);
            case CLAP -> { if (pressed) clap(p); }
            case LAUNCH -> { if (pressed) launch(p); }
            case GRAB -> grab(p, pressed);
            default -> { }
        }
    }

    /** Called from the server tick: laser beams and grab upkeep. */
    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        tickLaser(server, now);
        tickGrabs(server, now);
    }

    public static void clear(UUID id) {
        COOLDOWNS.remove(id);
        LASER_START.remove(id);
        GRAB_HOLD.remove(id);
        GRABS.remove(id);
    }

    /** Typed letter from a grabbed player. Correct letters advance, a wrong letter resets. */
    public static void escape(ServerPlayer target, char letter) {
        for (Map.Entry<UUID, Grab> entry : GRABS.entrySet()) {
            Grab g = entry.getValue();
            if (!g.target.equals(target.getUUID())) continue;
            if (letter == g.letters.charAt(g.index)) {
                g.index++;
                if (g.index >= g.letters.length()) {
                    releaseGrab(target.getServer(), entry.getKey());
                }
            } else {
                g.index = 0;
            }
            return;
        }
    }

    private static boolean isLocked(LivingEntity e) {
        return e.hasEffect(MultiverseEffects.STUN.get()) || e.hasEffect(MultiverseEffects.BURNOUT.get());
    }

    // ----- cooldowns -----

    private static long now(ServerPlayer p) {
        return p.getServer().getTickCount();
    }

    private static boolean ready(ServerPlayer p, int slot) {
        return now(p) >= COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new long[4])[slot];
    }

    private static void setCooldown(ServerPlayer p, int slot, long ticks) {
        COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new long[4])[slot] = now(p) + ticks;
    }

    // ----- laser vision (Z) -----

    private static void laser(ServerPlayer p, boolean pressed) {
        UUID id = p.getUUID();
        if (pressed) {
            if (LASER_START.containsKey(id) || !ready(p, LASER)) return;
            LASER_START.put(id, now(p));
        } else if (LASER_START.remove(id) != null) {
            setCooldown(p, LASER, 20); // 1 second after release
        }
    }

    private static void tickLaser(MinecraftServer server, long now) {
        Iterator<Map.Entry<UUID, Long>> it = LASER_START.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || !Heroes.isWearingHomelander(p) || isLocked(p)) {
                it.remove();
                continue;
            }
            if (now - entry.getValue() >= LASER_LIMIT) {
                it.remove();
                p.addEffect(new MobEffectInstance(MultiverseEffects.BURNOUT.get(), (int) BURNOUT_TICKS, 0, false, false, true));
                ServerTasks.schedule(now + BURNOUT_TICKS, () -> setCooldown(p, LASER, 200));
                continue;
            }
            fireBeam(p, now);
        }
    }

    private static void fireBeam(ServerPlayer p, long now) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(LASER_RANGE));

        BlockHitResult block = p.level().clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = block.getType() != HitResult.Type.MISS ? block.getLocation() : end;

        // Visual: a red dust line from the eyes to the impact point.
        if (p.level() instanceof ServerLevel level) {
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.15F), 1.0F);
            Vec3 dir = stop.subtract(eye);
            int steps = (int) (dir.length() / 0.5);
            for (int i = 0; i <= steps; i += 2) {
                Vec3 pt = eye.add(dir.scale(i / (double) Math.max(1, steps)));
                level.sendParticles(dust, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            }
        }

        // Damage every 5 ticks, so roughly 4 HP per second.
        if (now % 5 != 0) return;
        AABB box = new AABB(eye, stop).inflate(0.6);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, stop, box,
                e -> e instanceof LivingEntity && e != p && e.isPickable());
        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            target.hurt(p.damageSources().playerAttack(p), 2.0F);
        }
    }

    // ----- clap (X) -----

    private static void clap(ServerPlayer p) {
        if (!ready(p, CLAP)) return;

        Vec3 fwd = flatForward(p);
        Vec3 point = p.position().add(fwd.scale(2.0));
        Player victim = nearest(p, Player.class, point, 2.0);

        setCooldown(p, CLAP, 400); // 20 s

        if (victim != null) {
            // Ear clap: 4 hearts, 4 second stun, and no shockwave.
            victim.hurt(p.damageSources().playerAttack(p), 8.0F);
            victim.addEffect(new MobEffectInstance(MultiverseEffects.STUN.get(), 80, 0, false, false, true));
            return;
        }

        // Shockwave: 3 hearts within 3 blocks in front, breaks glass.
        if (p.level() instanceof ServerLevel level) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(point, 6, 4, 6), e -> e != p && e.isAlive() && e.position().distanceTo(point) <= 3.0)) {
                e.hurt(p.damageSources().playerAttack(p), 6.0F);
                e.knockback(0.8, -fwd.x, -fwd.z);
            }
            BlockPos center = BlockPos.containing(point);
            for (BlockPos bp : BlockPos.betweenClosed(center.offset(-3, -2, -3), center.offset(3, 2, 3))) {
                BlockState state = level.getBlockState(bp);
                if (state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES)) {
                    level.destroyBlock(bp, true);
                }
            }
            for (int i = 0; i < 24; i++) {
                double a = i / 24.0 * Math.PI * 2;
                level.sendParticles(ParticleTypes.END_ROD,
                        point.x + Math.cos(a) * 3, point.y + 1, point.z + Math.sin(a) * 3, 1, 0, 0, 0, 0);
            }
        }
    }

    // ----- launch (C) -----

    private static void launch(ServerPlayer p) {
        if (!ready(p, LAUNCH)) return;

        Vec3 fwd = flatForward(p);
        LivingEntity target = nearest(p, LivingEntity.class, p.position().add(fwd.scale(2.0)), 2.0);
        if (target == null) return; // no target: animation only, cooldown stays ready

        setCooldown(p, LAUNCH, 600); // 30 s
        target.hurt(p.damageSources().playerAttack(p), 4.0F);
        target.setDeltaMovement(0, 1.1, 0);
        target.hasImpulse = true;
        p.addEffect(new MobEffectInstance(MultiverseEffects.STUN.get(), 40, 0, false, false, true));

        UUID targetId = target.getUUID();
        long start = now(p);
        // About 1 second later, Homelander meets the target in the air and slams them down.
        ServerTasks.schedule(start + 20, () -> {
            if (!(p.level() instanceof ServerLevel level)) return;
            Entity e = level.getEntity(targetId);
            if (!(e instanceof LivingEntity t) || !t.isAlive() || !p.isAlive()) return;

            Vec3 above = t.position().add(0, 3.5, 0);
            p.connection.teleport(above.x, above.y, above.z, p.getYRot(), 60.0F);
            p.setDeltaMovement(0, -2.8, 0);
            p.hasImpulse = true;
            t.setDeltaMovement(0, -2.8, 0);
            t.hasImpulse = true;

            ServerTasks.schedule(start + 28, () -> {
                if (!t.isAlive()) return;
                Vec3 impact = t.position();
                t.hurt(p.damageSources().playerAttack(p), 36.0F); // 18 hearts
                for (LivingEntity n : level.getEntitiesOfClass(LivingEntity.class,
                        AABB.ofSize(impact, 6, 4, 6), n -> n != p && n != t && n.isAlive())) {
                    n.hurt(p.damageSources().playerAttack(p), 6.0F);
                }
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
                p.setDeltaMovement(0, -1.0, 0);
                p.hasImpulse = true;
            });
        });
    }

    // ----- grab (R) -----

    private static void grab(ServerPlayer p, boolean pressed) {
        UUID id = p.getUUID();
        Grab g = GRABS.get(id);

        if (pressed) {
            if (g != null) {
                GRAB_HOLD.put(id, now(p)); // hold to strangle, tap to release
                return;
            }
            if (!ready(p, GRAB)) return;

            Vec3 point = p.getEyePosition().add(p.getLookAngle().scale(2.5));
            LivingEntity target = nearest(p, LivingEntity.class, point, 2.0);
            if (target == null) return; // no target: cooldown stays ready

            setCooldown(p, GRAB, 400); // 20 s
            String letters = randomLetters();
            GRABS.put(id, new Grab(target.getUUID(), now(p), letters));
            target.addEffect(new MobEffectInstance(MultiverseEffects.GRABBED.get(), 20 * 60 * 5, 0, false, false, true));
            if (target instanceof ServerPlayer tp) {
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> tp), new Net.GrabPromptPacket(letters));
            }
        } else if (g != null) {
            Long held = GRAB_HOLD.remove(id);
            if (held != null && now(p) - held < 6) {
                releaseGrab(p.getServer(), id);
            }
        }
    }

    private static void tickGrabs(MinecraftServer server, long now) {
        Iterator<Map.Entry<UUID, Grab>> it = GRABS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Grab> entry = it.next();
            ServerPlayer grabber = server.getPlayerList().getPlayer(entry.getKey());
            Grab g = entry.getValue();
            if (grabber == null || !Heroes.isWearingHomelander(grabber)) {
                it.remove();
                GRAB_HOLD.remove(entry.getKey());
                if (grabber == null) continue;
                releaseGrab(server, entry.getKey());
                continue;
            }

            LivingEntity t = findLiving(grabber, g.target);
            boolean stillValid = t != null && t.isAlive() && grabber.distanceTo(t) <= 8.0;
            boolean mobTimeout = t != null && !(t instanceof ServerPlayer) && now - g.startTick > 100;
            if (!stillValid || mobTimeout) {
                releaseGrab(server, entry.getKey());
                continue;
            }

            // Carry the target in front of Homelander.
            Vec3 hold = grabber.position().add(grabber.getLookAngle().scale(1.2)).add(0, 1.0, 0);
            t.setPos(hold.x, hold.y, hold.z);
            t.setDeltaMovement(Vec3.ZERO);
            t.fallDistance = 0;

            // Strangle: 2 hearts every 2 seconds while the key is held.
            Long held = GRAB_HOLD.get(entry.getKey());
            if (held != null && now - held >= 6 && now % 40 == 0) {
                t.hurt(grabber.damageSources().playerAttack(grabber), 4.0F);
            }
        }
    }

    private static void releaseGrab(MinecraftServer server, UUID grabberId) {
        Grab g = GRABS.remove(grabberId);
        GRAB_HOLD.remove(grabberId);
        if (g == null || server == null) return;
        ServerPlayer grabber = server.getPlayerList().getPlayer(grabberId);
        LivingEntity t = grabber != null ? findLiving(grabber, g.target) : null;
        if (t != null) {
            t.removeEffect(MultiverseEffects.GRABBED.get());
            if (t instanceof ServerPlayer tp) {
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> tp), new Net.GrabPromptPacket(""));
            }
        }
    }

    // ----- helpers -----

    private static Vec3 flatForward(ServerPlayer p) {
        Vec3 f = p.getLookAngle();
        Vec3 flat = new Vec3(f.x, 0, f.z);
        return flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    private static <T extends LivingEntity> T nearest(ServerPlayer p, Class<T> type, Vec3 point, double radius) {
        T best = null;
        double bestDist = radius;
        for (T e : p.level().getEntitiesOfClass(type,
                AABB.ofSize(point, radius * 2, radius * 2, radius * 2), e -> e != p && e.isAlive())) {
            double d = e.position().distanceTo(point);
            if (d <= bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private static LivingEntity findLiving(ServerPlayer near, UUID id) {
        Entity e = near.serverLevel().getEntity(id);
        return e instanceof LivingEntity le ? le : null;
    }

    private static String randomLetters() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ESCAPE_LENGTH; i++) {
            sb.append(ESCAPE_LETTERS.charAt(RANDOM.nextInt(ESCAPE_LETTERS.length())));
        }
        return sb.toString();
    }
}
