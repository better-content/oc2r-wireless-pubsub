package com.bettercontent.oc2rwirelesspubsub.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class WirelessNetworkSavedData extends net.minecraft.world.level.saveddata.SavedData {
    public static WirelessNetworkSavedData get(net.minecraft.server.MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WirelessNetworkSavedData::load, WirelessNetworkSavedData::new, "oc2r_wireless_messages");
    }
    public record Origin(java.util.UUID owner,String host,String operation,int energy,int bytes) { public boolean receivedBy(String receiverHost){return owner!=null&&!host.equals(receiverHost);} }
    public record Delivery(String topic,String payload,Origin origin) {}

    private static final int MAX_TOPIC_BACKLOG = 256;
    private static final WirelessNetworkSavedData INSTANCE = new WirelessNetworkSavedData();

    private final Map<String, TopicState> topics = new ConcurrentHashMap<>();

    public static WirelessNetworkSavedData get() {
        return INSTANCE;
    }

    public void publish(final String topic, final String payload) {
        topicState(topic).publish(payload, null);
        setDirty();
    }

    public List<String> poll(final String topic, final String consumerId, final int maxItems) {
        return pollDelivered(topic,consumerId,maxItems).stream().map(Delivery::payload).toList();
    }

    public void publish(String topic,String payload,Origin origin) {
        topicState(topic).publish(payload,origin);setDirty();
    }
    public List<Delivery> pollDelivered(String topic,String consumerId,int maxItems) {
        var result=topicState(topic).poll(consumerId,maxItems).stream().map(m->new Delivery(topic,m.payload,m.origin)).toList();setDirty();return result;
    }
    public List<Delivery> pollMatchDelivered(String pattern,String consumerId,int maxItems) {
        var out=new ArrayList<Delivery>();
        for(String topic:topics.keySet())if(matches(pattern,topic))out.addAll(pollDelivered(topic,consumerId+"|"+topic,maxItems));
        return out;
    }
    @Override public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag) {
        var list=new net.minecraft.nbt.ListTag();
        topics.forEach((topic,state)->{synchronized(state){var entry=new net.minecraft.nbt.CompoundTag();entry.putString("topic",topic);entry.putLong("nextId",state.nextId);var messages=new net.minecraft.nbt.ListTag();for(var m:state.messages){var row=new net.minecraft.nbt.CompoundTag();row.putLong("id",m.id);row.putString("payload",m.payload);if(m.origin!=null){var o=m.origin;if(o.owner()!=null)row.putUUID("owner",o.owner());row.putString("host",o.host());row.putString("operation",o.operation());row.putInt("energy",o.energy());row.putInt("bytes",o.bytes());}messages.add(row);}entry.put("messages",messages);var offsets=new net.minecraft.nbt.CompoundTag();state.consumerOffsets.forEach(offsets::putLong);entry.put("offsets",offsets);list.add(entry);}});
        tag.put("topics",list);return tag;
    }
    public static WirelessNetworkSavedData load(net.minecraft.nbt.CompoundTag tag) {
        var out=new WirelessNetworkSavedData();
        for(var raw:tag.getList("topics",net.minecraft.nbt.Tag.TAG_COMPOUND)){var entry=(net.minecraft.nbt.CompoundTag)raw;var state=out.topicState(entry.getString("topic"));state.nextId=Math.max(1,entry.getLong("nextId"));for(var mr:entry.getList("messages",net.minecraft.nbt.Tag.TAG_COMPOUND)){var row=(net.minecraft.nbt.CompoundTag)mr;Origin origin=row.contains("host")?new Origin(row.hasUUID("owner")?row.getUUID("owner"):null,row.getString("host"),row.getString("operation"),row.getInt("energy"),row.getInt("bytes")):null;state.messages.addLast(new Message(row.getLong("id"),row.getString("payload"),origin));while(state.messages.size()>MAX_TOPIC_BACKLOG)state.messages.removeFirst();}var offsets=entry.getCompound("offsets");for(String key:offsets.getAllKeys())state.consumerOffsets.put(key,offsets.getLong(key));}
        return out;
    }

    public List<String> pollMatch(final String pattern, final String consumerId, final int maxItemsPerTopic) {
        final ArrayList<String> out = new ArrayList<>();
        for (final String topic : topics.keySet()) {
            if (matches(pattern, topic)) {
                final List<String> messages = poll(topic, consumerId + "|" + topic, maxItemsPerTopic);
                for (final String message : messages) {
                    out.add(topic + "|" + message);
                }
            }
        }
        return out;
    }

    public List<String> listTopics() {
        return new ArrayList<>(topics.keySet());
    }

    public int topicDepth(final String topic) {
        final TopicState state = topics.get(topic);
        return state == null ? 0 : state.depth();
    }

    public void clear() {
        topics.clear();
    }

    private TopicState topicState(final String topic) {
        return topics.computeIfAbsent(topic, ignored -> new TopicState());
    }

    private static boolean matches(final String pattern, final String value) {
        final StringBuilder regex = new StringBuilder(pattern.length());
        for (int i = 0; i < pattern.length(); i++) {
            final char ch = pattern.charAt(i);
            if (ch == '*') {
                regex.append(".*");
            } else if (ch == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return value.matches(regex.toString());
    }

    private static final class TopicState {
        private long nextId = 1;
        private final Deque<Message> messages = new ArrayDeque<>();
        private final Map<String, Long> consumerOffsets = new HashMap<>();

        public synchronized void publish(final String payload, Origin origin) {
            messages.addLast(new Message(nextId++, payload, origin));
            while (messages.size() > MAX_TOPIC_BACKLOG) {
                messages.removeFirst();
            }

            final long minAvailableId = messages.isEmpty() ? nextId : messages.getFirst().id;
            consumerOffsets.replaceAll((ignored, offset) -> Math.max(offset, minAvailableId));
        }

        public synchronized List<Message> poll(final String consumerId, final int maxItems) {
            final int clampedMax = Math.max(1, Math.min(maxItems, 64));
            final ArrayList<Message> out = new ArrayList<>(clampedMax);
            if (messages.isEmpty()) {
                return out;
            }

            final long minAvailableId = messages.getFirst().id;
            final long startId = Math.max(consumerOffsets.getOrDefault(consumerId, minAvailableId), minAvailableId);

            long nextConsumerId = startId;
            for (final Message message : messages) {
                if (message.id < nextConsumerId) {
                    continue;
                }
                out.add(message);
                nextConsumerId = message.id + 1;
                if (out.size() >= clampedMax) {
                    break;
                }
            }

            consumerOffsets.put(consumerId, nextConsumerId);
            return out;
        }

        public synchronized int depth() {
            return messages.size();
        }
    }

    private record Message(long id, String payload, Origin origin) {
    }
}
