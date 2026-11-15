package com.multiverse.mod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

/**
 * Server-side flight state. Vanilla movement is used for the actual motion, with the
 * flying speed raised for the suit. Momentum, banking and full rotation come in Stage 2.
 */
public final class FlightHandler {
    private static final float SUIT_FLY_SPEED = 0.12F;

    private FlightHandler() {}

    public static void serverTick(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return;

        Abilities ab = p.getAbilities();
        if (Heroes.isWearingHomelander(p)) {
            boolean changed = false;
            if (!ab.mayfly) { ab.mayfly = true; changed = true; }
            if (ab.getFlyingSpeed() != SUIT_FLY_SPEED) { ab.setFlyingSpeed(SUIT_FLY_SPEED); changed = true; }
            if (changed) p.onUpdateAbilities();
        } else if (ab.mayfly) {
            ab.mayfly = false;
            ab.flying = false;
            ab.setFlyingSpeed(0.05F);
            p.onUpdateAbilities();
        }
    }

    /** Called when the player double-jumps in the air while wearing the suit. */
    public static void requestFly(ServerPlayer p) {
        if (!Heroes.isWearingHomelander(p) || p.onGround()) return;
        Abilities ab = p.getAbilities();
        ab.mayfly = true;
        ab.flying = true;
        p.onUpdateAbilities();
    }
}
