package com.oc2rwireless.oc2rwireless.device;

import com.oc2rwireless.oc2rwireless.blockentity.WirelessRelayBlockEntity;
import com.oc2rwireless.oc2rwireless.data.WirelessNetworkSavedData;
import li.cil.oc2.api.bus.device.ItemDevice;
import li.cil.oc2.api.bus.device.object.Callback;
import li.cil.oc2.api.bus.device.object.NamedDevice;
import li.cil.oc2.api.bus.device.object.ObjectDevice;
import li.cil.oc2.api.bus.device.object.Parameter;
import li.cil.oc2.api.bus.device.provider.ItemDeviceQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class WirelessCardItemDevice {
    private static final int BASE_ENERGY_COST = 4;
    private static final int ENERGY_PER_32_BYTES = 1;

    private WirelessCardItemDevice() {
    }

    public static ItemDevice create(final ItemDeviceQuery query) {
        return new ObjectDevice(new CardRpcDevice(query), "wireless_card", "wireless", "message_bus");
    }

    private record HostContext(ServerLevel level, BlockPos hostPos, String hostId) {
    }

    private static final class CardRpcDevice implements NamedDevice {
        private final ItemDeviceQuery query;

        private CardRpcDevice(final ItemDeviceQuery query) {
            this.query = query;
        }

        @Callback(synchronize = false)
        public String open(@Parameter("topic") @Nullable final String topic) {
            return requireTopic(topic);
        }

        @Callback(synchronize = false)
        public List<Integer> push(@Parameter("topic") @Nullable final String topic,
                                  @Parameter("payload") @Nullable final String payload) {
            return send(topic, payload);
        }

        @Callback(synchronize = false)
        public List<Integer> send(@Parameter("topic") @Nullable final String topic,
                                  @Parameter("payload") @Nullable final String payload) {
            final String normalizedTopic = requireTopic(topic);
            if (payload == null) {
                throw new IllegalArgumentException("payload cannot be null");
            }

            final HostContext host = getHostContext();
            final WirelessRelayBlockEntity sourceRelay = nearestRelay(host.level(), host.hostPos(), 16)
                .orElseThrow(() -> new IllegalStateException("No wireless relay in range of this computer."));

            final int payloadBytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            final int energyCost = BASE_ENERGY_COST + ((payloadBytes + 31) / 32) * ENERGY_PER_32_BYTES;
            if (!sourceRelay.consumeEnergy(energyCost)) {
                throw new IllegalStateException("Source relay has insufficient energy.");
            }

            WirelessNetworkSavedData.get().publish(normalizedTopic, payload);
            return List.of(energyCost, payloadBytes);
        }

        @Callback
        public List<String> pop(@Parameter("topic") @Nullable final String topic,
                                @Parameter("max") final int max,
                                @Parameter("consumerId") @Nullable final String consumerId) {
            final String normalizedTopic = requireTopic(topic);
            final String normalizedConsumer = normalizeConsumerId(consumerId);
            return WirelessNetworkSavedData.get().poll(normalizedTopic, normalizedConsumer, max);
        }

        @Callback
        public List<String> poll(@Parameter("topic") @Nullable final String topic,
                                 @Parameter("max") final int max,
                                 @Parameter("consumerId") @Nullable final String consumerId) {
            return pop(topic, max, consumerId);
        }

        @Callback
        public List<String> pollMatch(@Parameter("pattern") @Nullable final String pattern,
                                      @Parameter("maxPerTopic") final int maxPerTopic,
                                      @Parameter("consumerId") @Nullable final String consumerId) {
            if (pattern == null || pattern.isBlank()) {
                throw new IllegalArgumentException("pattern cannot be empty");
            }
            return WirelessNetworkSavedData.get().pollMatch(pattern, normalizeConsumerId(consumerId), maxPerTopic);
        }

        @Callback(synchronize = false)
        public List<String> listTopics() {
            return WirelessNetworkSavedData.get().listTopics();
        }

        @Callback(synchronize = false)
        public int getTopicDepth(@Parameter("topic") @Nullable final String topic) {
            return WirelessNetworkSavedData.get().topicDepth(requireTopic(topic));
        }

        @Override
        public Collection<String> getDeviceTypeNames() {
            return List.of("wireless_card", "wireless", "message_bus");
        }

        private String normalizeConsumerId(@Nullable final String consumerId) {
            if (consumerId != null && !consumerId.isBlank()) {
                return consumerId;
            }
            return getHostContext().hostId();
        }

        private static String requireTopic(@Nullable final String topic) {
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("topic cannot be empty");
            }
            return topic;
        }

        private HostContext getHostContext() {
            final Optional<BlockEntity> maybeBe = query.getContainerBlockEntity();
            if (maybeBe.isPresent() && maybeBe.get().getLevel() instanceof ServerLevel serverLevel) {
                final BlockPos pos = maybeBe.get().getBlockPos();
                return new HostContext(serverLevel, pos, "block:" + pos.toShortString());
            }

            final Optional<net.minecraft.world.entity.Entity> maybeEntity = query.getContainerEntity();
            if (maybeEntity.isPresent() && maybeEntity.get().level() instanceof ServerLevel serverLevel) {
                final var entity = maybeEntity.get();
                return new HostContext(serverLevel, entity.blockPosition(), "entity:" + entity.getUUID());
            }

            throw new IllegalStateException("Wireless card is not in a server-side OC2R host context.");
        }

        private Optional<WirelessRelayBlockEntity> nearestRelay(final Level level, final BlockPos origin, final int range) {
            WirelessRelayBlockEntity closest = null;
            double closestDist = Double.MAX_VALUE;
            final BlockPos min = origin.offset(-range, -range, -range);
            final BlockPos max = origin.offset(range, range, range);

            for (final BlockPos cursor : BlockPos.betweenClosed(min, max)) {
                final BlockEntity blockEntity = level.getBlockEntity(cursor);
                if (blockEntity instanceof WirelessRelayBlockEntity relay) {
                    final double dist = relay.getBlockPos().distSqr(origin);
                    if (dist < closestDist) {
                        closest = relay;
                        closestDist = dist;
                    }
                }
            }

            return Optional.ofNullable(closest);
        }
    }
}
