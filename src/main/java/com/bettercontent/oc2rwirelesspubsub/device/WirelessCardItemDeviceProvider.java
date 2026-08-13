package com.bettercontent.oc2rwirelesspubsub.device;

import com.bettercontent.oc2rwirelesspubsub.registry.ModItems;
import li.cil.oc2.api.bus.device.ItemDevice;
import li.cil.oc2.api.bus.device.provider.ItemDeviceProvider;
import li.cil.oc2.api.bus.device.provider.ItemDeviceQuery;

import java.util.Optional;

public final class WirelessCardItemDeviceProvider implements ItemDeviceProvider {
    @Override
    public Optional<ItemDevice> getDevice(final ItemDeviceQuery query) {
        if (!query.getItemStack().is(ModItems.WIRELESS_CARD.get())) {
            return Optional.empty();
        }
        return Optional.of(WirelessCardItemDevice.create(query));
    }

    @Override
    public int getEnergyConsumption(final ItemDeviceQuery query) {
        return query.getItemStack().is(ModItems.WIRELESS_CARD.get()) ? 1 : 0;
    }
}
