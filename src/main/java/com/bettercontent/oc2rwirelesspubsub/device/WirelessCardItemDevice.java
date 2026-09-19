package com.bettercontent.oc2rwirelesspubsub.device;

import com.bettercontent.oc2rwirelesspubsub.blockentity.WirelessRelayBlockEntity;
import com.bettercontent.oc2rwirelesspubsub.data.WirelessNetworkSavedData;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WirelessCardItemDevice {
    private static final int BASE_ENERGY_COST = 4;
    private static final int ENERGY_PER_32_BYTES = 1;
    private static final int RELAY_RANGE = 16;

    private WirelessCardItemDevice() {
    }

    public static ItemDevice create(final ItemDeviceQuery query) {
        return new ObjectDevice(new CardRpcDevice(query), "wireless_card", "wireless", "message_bus");
    }

    private record HostContext(ServerLevel level, BlockPos hostPos, String hostId, java.util.UUID owner) {
    }

    private static final class CardRpcDevice implements NamedDevice {
        private final ItemDeviceQuery query;
        private final Map<String, BlockPos> relayAssociations = new ConcurrentHashMap<>();

        private CardRpcDevice(final ItemDeviceQuery query) {
            this.query = query;
        }

        @Callback(synchronize = false)
        public String open(@Parameter("topic") @Nullable final String topic) {
            return requireTopic(topic);
        }

        @Callback
        public List<Integer> push(@Parameter("topic") @Nullable final String topic,
                                  @Parameter("payload") @Nullable final String payload) {
            return send(topic, payload);
        }

        @Callback
        public List<Integer> send(@Parameter("topic") @Nullable final String topic,
                                  @Parameter("payload") @Nullable final String payload) {
            final String normalizedTopic = requireTopic(topic);
            if (payload == null) {
                throw new IllegalArgumentException("payload cannot be null");
            }

            final HostContext host = getHostContext();
            final WirelessNetworkSavedData broker = WirelessNetworkSavedData.get(host.level());
            if (!broker.canPublish(normalizedTopic, payload)) {
                throw new IllegalArgumentException("wireless topic, payload, or storage limit rejected publication");
            }
            final WirelessRelayBlockEntity sourceRelay = nearestRelay(host)
                .orElseThrow(() -> new IllegalStateException("No wireless relay in range of this computer."));

            final int payloadBytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            final int energyCost = BASE_ENERGY_COST + ((payloadBytes + 31) / 32) * ENERGY_PER_32_BYTES;
            if (!sourceRelay.consumeEnergy(energyCost)) {
                throw new IllegalStateException("Source relay has insufficient energy.");
            }

            broker.publish(normalizedTopic, payload, new WirelessNetworkSavedData.Origin(host.owner(),host.hostId(),java.util.UUID.randomUUID().toString(),energyCost,payloadBytes));
            return List.of(energyCost, payloadBytes);
        }

        @Callback
        public List<String> pop(@Parameter("topic") @Nullable final String topic,
                                @Parameter("max") final int max,
                                @Parameter("consumerId") @Nullable final String consumerId) {
            final String normalizedTopic = requireTopic(topic);
            final String normalizedConsumer = normalizeConsumerId(consumerId);
            var host=getHostContext();
            var messages=WirelessNetworkSavedData.get(host.level()).pollDelivered(normalizedTopic, normalizedConsumer, max);
            received(host,messages);
            return messages.stream().map(WirelessNetworkSavedData.Delivery::payload).toList();
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
            var host=getHostContext();
            var messages=WirelessNetworkSavedData.get(host.level()).pollMatchDelivered(pattern, normalizeConsumerId(consumerId), maxPerTopic);
            received(host,messages);
            return messages.stream().map(m->m.topic()+"|"+m.payload()).toList();
        }

        @Callback
        public List<String> listTopics() {
            return WirelessNetworkSavedData.get(getHostContext().level()).listTopics();
        }

        @Callback
        public int getTopicDepth(@Parameter("topic") @Nullable final String topic) {
            return WirelessNetworkSavedData.get(getHostContext().level()).topicDepth(requireTopic(topic));
        }

        @Override
        public Collection<String> getDeviceTypeNames() {
            return List.of("wireless_card", "wireless", "message_bus");
        }

        private void received(HostContext host,List<WirelessNetworkSavedData.Delivery> messages){
            for(var message:messages){var origin=message.origin();if(origin!=null&&origin.receivedBy(host.hostId())){
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.bettercontent.oc2rwirelesspubsub.api.WirelessMessageReceivedEvent(host.level().getServer(),origin.owner(),origin.operation(),message.topic(),origin.bytes(),origin.energy()));
            }}
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
                var data=maybeBe.get().getPersistentData();
                return new HostContext(serverLevel, pos, serverLevel.dimension().location()+":block:" + pos.asLong(), data.hasUUID(com.bettercontent.oc2rwirelesspubsub.api.WirelessOperators.KEY)?data.getUUID(com.bettercontent.oc2rwirelesspubsub.api.WirelessOperators.KEY):null);
            }

            final Optional<net.minecraft.world.entity.Entity> maybeEntity = query.getContainerEntity();
            if (maybeEntity.isPresent() && maybeEntity.get().level() instanceof ServerLevel serverLevel) {
                final var entity = maybeEntity.get();
                var data=entity.getPersistentData();
                return new HostContext(serverLevel, entity.blockPosition(), "entity:" + entity.getUUID(), data.hasUUID(com.bettercontent.oc2rwirelesspubsub.api.WirelessOperators.KEY)?data.getUUID(com.bettercontent.oc2rwirelesspubsub.api.WirelessOperators.KEY):null);
            }

            throw new IllegalStateException("Wireless card is not in a server-side OC2R host context.");
        }

        private Optional<WirelessRelayBlockEntity> nearestRelay(final HostContext host) {
            final Level level = host.level();
            final BlockPos origin = host.hostPos();
            final String key = level.dimension().location() + "|" + host.hostId() + "|" + origin.asLong();
            final BlockPos cached = relayAssociations.get(key);
            if (cached != null && level.hasChunkAt(cached)) {
                final BlockEntity blockEntity = level.getBlockEntity(cached);
                if (blockEntity instanceof WirelessRelayBlockEntity relay && relay.getBlockPos().distSqr(origin) <= RELAY_RANGE * RELAY_RANGE) {
                    return Optional.of(relay);
                }
                relayAssociations.remove(key, cached);
            }
            WirelessRelayBlockEntity closest = null;
            double closestDist = Double.MAX_VALUE;
            final BlockPos min = origin.offset(-RELAY_RANGE, -RELAY_RANGE, -RELAY_RANGE);
            final BlockPos max = origin.offset(RELAY_RANGE, RELAY_RANGE, RELAY_RANGE);

            for (final BlockPos cursor : BlockPos.betweenClosed(min, max)) {
                if (!level.hasChunkAt(cursor)) continue;
                final BlockEntity blockEntity = level.getBlockEntity(cursor);
                if (blockEntity instanceof WirelessRelayBlockEntity relay) {
                    final double dist = relay.getBlockPos().distSqr(origin);
                    if (dist < closestDist) {
                        closest = relay;
                        closestDist = dist;
                    }
                }
            }
            if (closest != null) relayAssociations.put(key, closest.getBlockPos());
            return Optional.ofNullable(closest);
        }
    }
}
