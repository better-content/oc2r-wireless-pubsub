package com.oc2rwireless.oc2rwireless.registry;

import com.oc2rwireless.oc2rwireless.Oc2rWirelessMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Oc2rWirelessMod.MOD_ID);

    public static final RegistryObject<Item> WIRELESS_RELAY = ITEMS.register("wireless_relay", () ->
        new BlockItem(ModBlocks.WIRELESS_RELAY.get(), new Item.Properties()));

    public static final RegistryObject<Item> WIRELESS_CARD = ITEMS.register("wireless_card", () ->
        new Item(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
