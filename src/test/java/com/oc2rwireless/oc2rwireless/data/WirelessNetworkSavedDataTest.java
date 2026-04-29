package com.oc2rwireless.oc2rwireless.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WirelessNetworkSavedDataTest {
    private WirelessNetworkSavedData broker;

    @BeforeEach
    void setUp() {
        broker = WirelessNetworkSavedData.get();
        broker.clear();
    }

    @Test
    void publishAndPollUsesConsumerOffsets() {
        broker.publish("alerts", "a1");
        broker.publish("alerts", "a2");

        final List<String> first = broker.poll("alerts", "consumerA", 16);
        final List<String> second = broker.poll("alerts", "consumerA", 16);

        assertEquals(List.of("a1", "a2"), first);
        assertTrue(second.isEmpty());
    }

    @Test
    void consumersAreIndependent() {
        broker.publish("metrics", "m1");
        broker.publish("metrics", "m2");

        assertEquals(List.of("m1", "m2"), broker.poll("metrics", "c1", 16));
        assertEquals(List.of("m1", "m2"), broker.poll("metrics", "c2", 16));
    }

    @Test
    void maxItemsIsClampedAndApplied() {
        broker.publish("q", "v1");
        broker.publish("q", "v2");
        broker.publish("q", "v3");

        assertEquals(List.of("v1"), broker.poll("q", "c", 0));
        assertEquals(List.of("v2", "v3"), broker.poll("q", "c", 1000));
    }

    @Test
    void listTopicsShowsPublishedTopics() {
        broker.publish("t.a", "1");
        broker.publish("t.b", "2");

        final List<String> topics = broker.listTopics();

        assertTrue(topics.contains("t.a"));
        assertTrue(topics.contains("t.b"));
    }

    @Test
    void topicDepthTracksBacklog() {
        assertEquals(0, broker.topicDepth("depth"));

        broker.publish("depth", "x");
        broker.publish("depth", "y");

        assertEquals(2, broker.topicDepth("depth"));

        broker.poll("depth", "c", 1);

        assertEquals(2, broker.topicDepth("depth"));
    }

    @Test
    void wildcardPollMatchSupportsStarAndQuestionMark() {
        broker.publish("factory.alpha", "a1");
        broker.publish("factory.beta", "b1");
        broker.publish("farm.alpha", "x1");

        final List<String> star = broker.pollMatch("factory.*", "group", 8);
        final List<String> question = broker.pollMatch("f?rm.alpha", "group2", 8);

        assertTrue(star.contains("factory.alpha|a1"));
        assertTrue(star.contains("factory.beta|b1"));
        assertEquals(List.of("farm.alpha|x1"), question);
    }

    @Test
    void backlogTrimsToLimitForSlowConsumers() {
        for (int i = 1; i <= 300; i++) {
            broker.publish("trim", "m" + i);
        }

        final List<String> firstBatch = broker.poll("trim", "slow", 64);

        assertEquals(256, broker.topicDepth("trim"));
        assertEquals("m45", firstBatch.get(0));
    }

    @Test
    void clearResetsAllState() {
        broker.publish("x", "1");
        broker.publish("y", "2");
        broker.poll("x", "c", 1);

        broker.clear();

        assertTrue(broker.listTopics().isEmpty());
        assertEquals(0, broker.topicDepth("x"));
        assertTrue(broker.poll("y", "c", 10).isEmpty());
    }
}
