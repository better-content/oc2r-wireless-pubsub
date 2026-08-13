package com.bettercontent.oc2rwirelesspubsub;

import com.bettercontent.oc2rwirelesspubsub.registry.ModBlockEntities;
import com.bettercontent.oc2rwirelesspubsub.registry.ModBlocks;
import com.bettercontent.oc2rwirelesspubsub.registry.ModItemDeviceProviders;
import com.bettercontent.oc2rwirelesspubsub.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Oc2rWirelessMod.MOD_ID)
public final class Oc2rWirelessMod {
    public static final String MOD_ID = "oc2r_wireless_pubsub";

    public Oc2rWirelessMod() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModItemDeviceProviders.ITEM_DEVICE_PROVIDERS.register(modBus);
    }
}
