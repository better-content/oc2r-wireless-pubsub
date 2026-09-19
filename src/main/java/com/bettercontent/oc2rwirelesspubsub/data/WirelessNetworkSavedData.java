package com.bettercontent.oc2rwirelesspubsub.data;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Per-dimension, bounded broker. The previous unscoped saved data is retained but never delivered. */
public final class WirelessNetworkSavedData extends net.minecraft.world.level.saveddata.SavedData {
    private static final String DATA_ID = "oc2r_wireless_messages";
    private static final int FORMAT = 2;
    public static final int MAX_TOPIC_LENGTH = 96, MAX_PAYLOAD_BYTES = 4_096, MAX_TOPICS = 128, MAX_CONSUMERS_PER_TOPIC = 128, MAX_MATCHED_TOPICS = 32;
    private static final int MAX_TOPIC_BACKLOG = 256;
    private static final WirelessNetworkSavedData INSTANCE = new WirelessNetworkSavedData("test");

    public static WirelessNetworkSavedData get(final net.minecraft.server.level.ServerLevel level) {
        final String dimension = level.dimension().location().toString();
        return level.getDataStorage().computeIfAbsent(tag -> load(tag, dimension), () -> new WirelessNetworkSavedData(dimension), DATA_ID);
    }
    /** Test-only compatibility accessor; runtime callers must supply their ServerLevel. */
    public static WirelessNetworkSavedData get() { return INSTANCE; }
    public record Origin(UUID owner, String host, String operation, int energy, int bytes) { public boolean receivedBy(final String receiverHost) { return owner != null && !host.equals(receiverHost); } }
    public record Delivery(String topic, String payload, Origin origin) {}

    private final String dimension;
    private final Map<String, TopicState> topics = new ConcurrentHashMap<>();
    private final List<net.minecraft.nbt.CompoundTag> quarantinedLegacy = new ArrayList<>();
    public WirelessNetworkSavedData() { this("test"); }
    private WirelessNetworkSavedData(final String dimension) { this.dimension = dimension; }

    public void publish(final String topic, final String payload) { publish(topic, payload, null); }
    public void publish(final String topic, final String payload, final Origin origin) { validatePublish(topic, payload); topicStateForPublish(topic).publish(payload, origin); setDirty(); }
    public boolean canPublish(final String topic, final String payload) { try { validatePublish(topic, payload); return topics.containsKey(topic) || topics.size() < MAX_TOPICS; } catch (IllegalArgumentException ignored) { return false; } }
    public List<String> poll(final String topic, final String consumerId, final int maxItems) { return pollDelivered(topic, consumerId, maxItems).stream().map(Delivery::payload).toList(); }
    public List<Delivery> pollDelivered(final String topic, final String consumerId, final int maxItems) {
        validateTopic(topic); validateConsumer(consumerId); final TopicState state = topics.get(topic);
        if (state == null) return List.of(); // Empty polls never create durable topic/consumer state.
        final List<Message> messages = state.poll(consumerId, maxItems); if (!messages.isEmpty()) setDirty();
        return messages.stream().map(m -> new Delivery(topic, m.payload, m.origin)).toList();
    }
    public List<Delivery> pollMatchDelivered(final String pattern, final String consumerId, final int maxItems) {
        validatePattern(pattern); validateConsumer(consumerId); final List<Delivery> out = new ArrayList<>(); int matched = 0;
        for (final String topic : sortedTopics()) if (matches(pattern, topic) && ++matched <= MAX_MATCHED_TOPICS) out.addAll(pollDelivered(topic, consumerId + "|" + topic, maxItems));
        return out;
    }
    public List<String> pollMatch(final String pattern, final String consumerId, final int maxItems) { return pollMatchDelivered(pattern, consumerId, maxItems).stream().map(m -> m.topic + "|" + m.payload).toList(); }
    public List<String> listTopics() { return sortedTopics(); }
    public int topicDepth(final String topic) { final TopicState state = topics.get(topic); return state == null ? 0 : state.depth(); }
    public int quarantinedLegacyCount() { return quarantinedLegacy.size(); }
    public void clear() { topics.clear(); quarantinedLegacy.clear(); setDirty(); }

    @Override public net.minecraft.nbt.CompoundTag save(final net.minecraft.nbt.CompoundTag tag) {
        tag.putInt("format", FORMAT); tag.putString("dimension", dimension); final var list = new net.minecraft.nbt.ListTag();
        topics.forEach((topic, state) -> { synchronized (state) { list.add(saveTopic(topic, state)); } }); tag.put("topics", list);
        if (!quarantinedLegacy.isEmpty()) { final var quarantine = new net.minecraft.nbt.ListTag(); quarantinedLegacy.forEach(quarantine::add); tag.put("quarantined_legacy", quarantine); } return tag;
    }
    public static WirelessNetworkSavedData load(final net.minecraft.nbt.CompoundTag tag) { return load(tag, "test"); }
    public static WirelessNetworkSavedData load(final net.minecraft.nbt.CompoundTag tag, final String expectedDimension) {
        final WirelessNetworkSavedData out = new WirelessNetworkSavedData(expectedDimension);
        if (tag.getInt("format") != FORMAT || !expectedDimension.equals(tag.getString("dimension"))) { quarantine(tag, out); return out; }
        for (final var raw : tag.getList("topics", net.minecraft.nbt.Tag.TAG_COMPOUND)) out.loadTopic((net.minecraft.nbt.CompoundTag) raw);
        for (final var raw : tag.getList("quarantined_legacy", net.minecraft.nbt.Tag.TAG_COMPOUND)) out.quarantinedLegacy.add(((net.minecraft.nbt.CompoundTag) raw).copy()); return out;
    }
    private static void quarantine(final net.minecraft.nbt.CompoundTag tag, final WirelessNetworkSavedData out) { for (final var raw : tag.getList("topics", net.minecraft.nbt.Tag.TAG_COMPOUND)) out.quarantinedLegacy.add(((net.minecraft.nbt.CompoundTag) raw).copy()); for (final var raw : tag.getList("quarantined_legacy", net.minecraft.nbt.Tag.TAG_COMPOUND)) out.quarantinedLegacy.add(((net.minecraft.nbt.CompoundTag) raw).copy()); }
    private net.minecraft.nbt.CompoundTag saveTopic(final String topic, final TopicState state) {
        final var entry = new net.minecraft.nbt.CompoundTag(); entry.putString("topic", topic); entry.putLong("nextId", state.nextId); final var messages = new net.minecraft.nbt.ListTag();
        for (final Message m : state.messages) { final var row = new net.minecraft.nbt.CompoundTag(); row.putLong("id", m.id); row.putString("payload", m.payload); saveOrigin(row, m.origin); messages.add(row); }
        entry.put("messages", messages); final var offsets = new net.minecraft.nbt.CompoundTag(); state.consumerOffsets.forEach(offsets::putLong); entry.put("offsets", offsets); return entry;
    }
    private void loadTopic(final net.minecraft.nbt.CompoundTag entry) {
        final String topic = entry.getString("topic"); if (!isValidTopic(topic) || topics.size() >= MAX_TOPICS) return; final TopicState state = new TopicState(); state.nextId = Math.max(1, entry.getLong("nextId"));
        for (final var raw : entry.getList("messages", net.minecraft.nbt.Tag.TAG_COMPOUND)) { final var row = (net.minecraft.nbt.CompoundTag) raw; final String payload = row.getString("payload"); if (payloadBytes(payload) <= MAX_PAYLOAD_BYTES) state.messages.addLast(new Message(row.getLong("id"), payload, loadOrigin(row))); while (state.messages.size() > MAX_TOPIC_BACKLOG) state.messages.removeFirst(); }
        final var offsets = entry.getCompound("offsets"); for (final String key : offsets.getAllKeys()) if (state.consumerOffsets.size() < MAX_CONSUMERS_PER_TOPIC && isValidConsumer(key)) state.consumerOffsets.put(key, offsets.getLong(key)); topics.put(topic, state);
    }
    private static void saveOrigin(final net.minecraft.nbt.CompoundTag row, final Origin origin) { if (origin != null) { if (origin.owner != null) row.putUUID("owner", origin.owner); row.putString("host", origin.host); row.putString("operation", origin.operation); row.putInt("energy", origin.energy); row.putInt("bytes", origin.bytes); } }
    private static Origin loadOrigin(final net.minecraft.nbt.CompoundTag row) { return row.contains("host") ? new Origin(row.hasUUID("owner") ? row.getUUID("owner") : null, row.getString("host"), row.getString("operation"), row.getInt("energy"), row.getInt("bytes")) : null; }
    private TopicState topicStateForPublish(final String topic) { final TopicState existing = topics.get(topic); if (existing != null) return existing; if (topics.size() >= MAX_TOPICS) throw new IllegalStateException("wireless topic limit reached"); return topics.computeIfAbsent(topic, ignored -> new TopicState()); }
    private List<String> sortedTopics() { return topics.keySet().stream().sorted().toList(); }
    private static void validatePublish(final String topic, final String payload) { validateTopic(topic); if (payload == null || payloadBytes(payload) > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("payload exceeds " + MAX_PAYLOAD_BYTES + " UTF-8 bytes"); }
    private static void validateTopic(final String topic) { if (!isValidTopic(topic)) throw new IllegalArgumentException("topic must be 1-" + MAX_TOPIC_LENGTH + " UTF-8 bytes"); }
    private static boolean isValidTopic(final String topic) { return topic != null && !topic.isBlank() && payloadBytes(topic) <= MAX_TOPIC_LENGTH; }
    private static void validateConsumer(final String consumer) { if (!isValidConsumer(consumer)) throw new IllegalArgumentException("consumer id must be 1-128 UTF-8 bytes"); }
    private static boolean isValidConsumer(final String consumer) { return consumer != null && !consumer.isBlank() && payloadBytes(consumer) <= 128; }
    private static void validatePattern(final String pattern) { if (!isValidTopic(pattern)) throw new IllegalArgumentException("pattern must be 1-" + MAX_TOPIC_LENGTH + " UTF-8 bytes"); }
    private static int payloadBytes(final String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
    private static boolean matches(final String pattern, final String value) { final var regex = new StringBuilder(pattern.length()); for (int i = 0; i < pattern.length(); i++) { final char ch = pattern.charAt(i); if (ch == '*') regex.append(".*"); else if (ch == '?') regex.append('.'); else regex.append(Pattern.quote(String.valueOf(ch))); } return value.matches(regex.toString()); }
    private static final class TopicState {
        private long nextId = 1; private final Deque<Message> messages = new ArrayDeque<>(); private final Map<String, Long> consumerOffsets = new HashMap<>();
        synchronized void publish(final String payload, final Origin origin) { messages.addLast(new Message(nextId++, payload, origin)); while (messages.size() > MAX_TOPIC_BACKLOG) messages.removeFirst(); final long min = messages.isEmpty() ? nextId : messages.getFirst().id; consumerOffsets.replaceAll((ignored, offset) -> Math.max(offset, min)); }
        synchronized List<Message> poll(final String consumer, final int max) { final int limit = Math.max(1, Math.min(max, 64)); if (messages.isEmpty()) return List.of(); if (!consumerOffsets.containsKey(consumer) && consumerOffsets.size() >= MAX_CONSUMERS_PER_TOPIC) throw new IllegalStateException("wireless consumer limit reached"); final long start = Math.max(consumerOffsets.getOrDefault(consumer, messages.getFirst().id), messages.getFirst().id); final var out = new ArrayList<Message>(limit); long next = start; for (final Message m : messages) if (m.id >= next) { out.add(m); next = m.id + 1; if (out.size() == limit) break; } consumerOffsets.put(consumer, next); return out; }
        synchronized int depth() { return messages.size(); }
    }
    private record Message(long id, String payload, Origin origin) {}
}
