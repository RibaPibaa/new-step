package com.multiverse.mod;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MultiverseMod.MOD_ID)
public class MultiverseMod {
    public static final String MOD_ID = "multiverse";

    public MultiverseMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        Heroes.ITEMS.register(modBus);
        MultiverseEffects.EFFECTS.register(modBus);
        Net.register();

        MinecraftForge.EVENT_BUS.register(new ServerEvents());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.multiverse.mod.client.ClientSetup.init(modBus));
    }
}
