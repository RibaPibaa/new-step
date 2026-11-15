package com.multiverse.mod;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class MultiverseEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MultiverseMod.MOD_ID);

    /** Burnout: forced kneel cutscene. Cannot move and cannot be damaged. */
    public static final RegistryObject<MobEffect> BURNOUT =
            EFFECTS.register("burnout", () -> new LockEffect(0xB0102A, true));
    /** Stun: cannot move. Used by Clap (on the victim) and Launch (on Homelander during his cutscene). */
    public static final RegistryObject<MobEffect> STUN =
            EFFECTS.register("stun", () -> new LockEffect(0xFFD24A, true));
    /** Grabbed: held by Homelander. Position is controlled by the grab logic, not by movement. */
    public static final RegistryObject<MobEffect> GRABBED =
            EFFECTS.register("grabbed", () -> new LockEffect(0x3C5AA6, false));

    private MultiverseEffects() {}

    public static class LockEffect extends MobEffect {
        private final boolean freezeMovement;

        LockEffect(int color, boolean freezeMovement) {
            super(MobEffectCategory.HARMFUL, color);
            this.freezeMovement = freezeMovement;
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return true;
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            if (freezeMovement) {
                Vec3 v = entity.getDeltaMovement();
                entity.setDeltaMovement(0, Math.min(0, v.y), 0);
            }
        }
    }
}
