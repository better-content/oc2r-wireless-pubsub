package com.bettercontent.oc2rwirelesspubsub.blockentity;

import com.bettercontent.oc2rwirelesspubsub.data.WirelessNetworkSavedData;
import com.bettercontent.oc2rwirelesspubsub.registry.ModBlockEntities;
import li.cil.oc2.api.bus.device.object.Callback;
import li.cil.oc2.api.bus.device.object.NamedDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

public final class WirelessRelayBlockEntity extends BlockEntity implements NamedDevice {
    public static final int CAPACITY = 200_000;
    public static final int MAX_RECEIVE = 1_000;
    public static final int MAX_EXTRACT = 1_000;

    private final EnergyStorage energy = new EnergyStorage(CAPACITY, MAX_RECEIVE, MAX_EXTRACT);
    private final LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> energy);
    private String alias = "";

    public WirelessRelayBlockEntity(final BlockPos pos, final BlockState state) {
        super(ModBlockEntities.WIRELESS_RELAY.get(), pos, state);
    }

    @Override
    public void load(final CompoundTag tag) {
        super.load(tag);
        if (tag.contains("energy")) {
            energy.deserializeNBT(tag.get("energy"));
        }
        alias = tag.getString("alias");
    }

    @Override
    protected void saveAdditional(final CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("energy", energy.serializeNBT());
        tag.putString("alias", alias);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        energyCapability.invalidate();
    }

    @Override
    public <T> LazyOptional<T> getCapability(final Capability<T> cap, @Nullable final net.minecraft.core.Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    public int getEnergyStored() {
        return energy.getEnergyStored();
    }

    public boolean consumeEnergy(final int amount) {
        if (amount <= 0) {
            return true;
        }
        if (energy.getEnergyStored() < amount) {
            return false;
        }
        energy.extractEnergy(amount, false);
        setChanged();
        return true;
    }

    @Callback(synchronize = false)
    public int getStoredEnergy() {
        return energy.getEnergyStored();
    }

    @Callback(synchronize = false)
    public int getMaxEnergy() {
        return energy.getMaxEnergyStored();
    }

    @Callback
    public List<String> listTopics() {
        if (!(level instanceof ServerLevel)) {
            return List.of();
        }
        return WirelessNetworkSavedData.get((ServerLevel) level).listTopics();
    }

    @Callback
    public int getTopicDepth(final String topic) {
        if (!(level instanceof ServerLevel)) {
            return 0;
        }
        return WirelessNetworkSavedData.get((ServerLevel) level).topicDepth(topic);
    }

    @Callback(synchronize = false)
    public String getAlias() {
        return alias;
    }

    @Callback
    public void setAlias(final String value) {
        alias = value == null ? "" : value.trim();
        setChanged();
    }

    @Override
    public Collection<String> getDeviceTypeNames() {
        return List.of("wireless_relay", "wireless");
    }
}
