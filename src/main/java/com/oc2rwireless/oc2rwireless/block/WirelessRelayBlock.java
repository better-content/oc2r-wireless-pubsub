package com.oc2rwireless.oc2rwireless.block;

import com.oc2rwireless.oc2rwireless.blockentity.WirelessRelayBlockEntity;
import com.oc2rwireless.oc2rwireless.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class WirelessRelayBlock extends BaseEntityBlock {
    public WirelessRelayBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(final BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return ModBlockEntities.WIRELESS_RELAY.get().create(pos, state);
    }
}
