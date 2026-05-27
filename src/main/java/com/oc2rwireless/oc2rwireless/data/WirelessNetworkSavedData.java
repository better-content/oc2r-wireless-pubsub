package com.oc2rwireless.oc2rwireless.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class WirelessNetworkSavedData {
    private static final int MAX_TOPIC_BACKLOG = 256;
    private static final WirelessNetworkSavedData INSTANCE = new WirelessNetworkSavedData();

    private final Map<String, TopicState> topics = new ConcurrentHashMap<>();

    public static WirelessNetworkSavedData get() {
        return INSTANCE;
    }

    public void publish(final String topic, final String payload) {
        topicState(topic).publish(payload);
    }

    public List<String> poll(final String topic, final String consumerId, final int maxItems) {
        return topicState(topic).poll(consumerId, maxItems);
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

        public synchronized void publish(final String payload) {
            messages.addLast(new Message(nextId++, payload));
            while (messages.size() > MAX_TOPIC_BACKLOG) {
                messages.removeFirst();
            }

            final long minAvailableId = messages.isEmpty() ? nextId : messages.getFirst().id;
            consumerOffsets.replaceAll((ignored, offset) -> Math.max(offset, minAvailableId));
        }

        public synchronized List<String> poll(final String consumerId, final int maxItems) {
            final int clampedMax = Math.max(1, Math.min(maxItems, 64));
            final ArrayList<String> out = new ArrayList<>(clampedMax);
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
                out.add(message.payload);
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

    private record Message(long id, String payload) {
    }
}
