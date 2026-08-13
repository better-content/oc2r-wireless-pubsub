package com.bettercontent.oc2rwirelesspubsub.registry;

import com.bettercontent.oc2rwirelesspubsub.Oc2rWirelessMod;
import com.bettercontent.oc2rwirelesspubsub.block.WirelessRelayBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Oc2rWirelessMod.MOD_ID);

    public static final RegistryObject<Block> WIRELESS_RELAY = BLOCKS.register("wireless_relay", () ->
        new WirelessRelayBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 6.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()));

    private ModBlocks() {
    }
}
