package com.oc2rwireless.oc2rwireless.registry;

import com.oc2rwireless.oc2rwireless.Oc2rWirelessMod;
import com.oc2rwireless.oc2rwireless.blockentity.WirelessRelayBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Oc2rWirelessMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<WirelessRelayBlockEntity>> WIRELESS_RELAY = BLOCK_ENTITY_TYPES.register("wireless_relay", () ->
        BlockEntityType.Builder.of(WirelessRelayBlockEntity::new, ModBlocks.WIRELESS_RELAY.get()).build(null));

    private ModBlockEntities() {
    }
}
