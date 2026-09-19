package com.bettercontent.oc2rwirelesspubsub.data;

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
    void maxItemsUpperClampLeavesRemainingMessagesForNextPoll() {
        for (int i = 1; i <= 70; i++) {
            broker.publish("bulk", "m" + i);
        }

        final List<String> firstBatch = broker.poll("bulk", "c", 1000);
        final List<String> secondBatch = broker.poll("bulk", "c", 1000);

        assertEquals(64, firstBatch.size());
        assertEquals("m1", firstBatch.get(0));
        assertEquals("m64", firstBatch.get(63));
        assertEquals(List.of("m65", "m66", "m67", "m68", "m69", "m70"), secondBatch);
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
    void pollMatchTreatsRegexMetacharactersAsLiteralTopicCharacters() {
        broker.publish("factory[1]+main", "literal");
        broker.publish("factoryyymain", "regex-looking");

        final List<String> messages = broker.pollMatch("factory[1]+main", "group", 8);

        assertEquals(List.of("factory[1]+main|literal"), messages);
    }

    @Test
    void pollMatchQuestionMarkDoesNotConsumeMissingCharacter() {
        broker.publish("node.a", "match");
        broker.publish("node.", "too-short");
        broker.publish("node.ab", "too-long");

        assertEquals(List.of("node.a|match"), broker.pollMatch("node.?", "group", 8));
    }

    @Test
    void pollMatchOffsetsAreIndependentPerTopic() {
        broker.publish("sensor.a", "a1");
        broker.publish("sensor.b", "b1");

        assertTrue(broker.pollMatch("sensor.*", "shared", 8).containsAll(List.of("sensor.a|a1", "sensor.b|b1")));
        assertTrue(broker.pollMatch("sensor.*", "shared", 8).isEmpty());

        broker.publish("sensor.a", "a2");

        assertEquals(List.of("sensor.a|a2"), broker.pollMatch("sensor.a", "shared", 8));
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
    void existingConsumerOffsetIsAdvancedWhenBacklogTrimsPastIt() {
        for (int i = 1; i <= 10; i++) {
            broker.publish("trimmed", "m" + i);
        }
        assertEquals(List.of("m1"), broker.poll("trimmed", "slow", 1));

        for (int i = 11; i <= 310; i++) {
            broker.publish("trimmed", "m" + i);
        }

        final List<String> firstAvailableBatch = broker.poll("trimmed", "slow", 64);

        assertEquals(256, broker.topicDepth("trimmed"));
        assertEquals("m55", firstAvailableBatch.get(0));
        assertEquals("m118", firstAvailableBatch.get(63));
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
    @Test void attributedMessageAndConsumerOffsetSurviveReload() {
        var owner=java.util.UUID.randomUUID();var origin=new WirelessNetworkSavedData.Origin(owner,"overworld:block:1","message:1",5,10);
        broker.publish("jobs","payload",origin);
        var restored=WirelessNetworkSavedData.load(broker.save(new net.minecraft.nbt.CompoundTag()));
        var received=restored.pollDelivered("jobs","different-host",1);
        assertEquals(1,received.size());assertEquals(origin,received.get(0).origin());assertEquals("payload",received.get(0).payload());
        var again=WirelessNetworkSavedData.load(restored.save(new net.minecraft.nbt.CompoundTag()));
        assertTrue(again.pollDelivered("jobs","different-host",1).isEmpty());
    }
    @Test void distinctWorldBrokersDoNotShareMessages(){var other=new WirelessNetworkSavedData();broker.publish("jobs","one");assertTrue(other.poll("jobs","consumer",1).isEmpty());}
    @Test void anonymousAndUnownedMessagesRoundTripWithoutFabricatingAnOwner(){
        broker.publish("plain","payload");broker.publish("plain","unowned",new WirelessNetworkSavedData.Origin(null,"host","operation",0,0));
        var restored=WirelessNetworkSavedData.load(broker.save(new net.minecraft.nbt.CompoundTag()));
        var delivery=restored.pollMatchDelivered("pl*","receiver",10);assertEquals(2,delivery.size());
        org.junit.jupiter.api.Assertions.assertNull(delivery.get(0).origin());org.junit.jupiter.api.Assertions.assertNull(delivery.get(1).origin().owner());
        assertTrue(restored.pollMatchDelivered("missing","receiver",10).isEmpty());
    }
    @Test void emptySavedBrokerLoadsAndRetainsNoTopics(){assertTrue(WirelessNetworkSavedData.load(new net.minecraft.nbt.CompoundTag()).listTopics().isEmpty());}
    @Test void attributedMessagesRetainBacklogBoundsAfterReload(){
        var origin=new WirelessNetworkSavedData.Origin(java.util.UUID.randomUUID(),"host","operation",4,1);
        for(int i=0;i<300;i++)broker.publish("bounded",Integer.toString(i),origin);
        var restored=WirelessNetworkSavedData.load(broker.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(256,restored.topicDepth("bounded"));assertEquals("44",restored.pollDelivered("bounded","receiver",1).get(0).payload());
    }
    @Test void receiptRequiresAnOwnedMessageAndDifferentPhysicalHost(){
        var origin=new WirelessNetworkSavedData.Origin(java.util.UUID.randomUUID(),"computer-a","message",5,8);
        org.junit.jupiter.api.Assertions.assertFalse(origin.receivedBy("computer-a"));assertTrue(origin.receivedBy("computer-b"));
        org.junit.jupiter.api.Assertions.assertFalse(new WirelessNetworkSavedData.Origin(null,"computer-a","message",5,8).receivedBy("computer-b"));
    }
    @Test void absentPollsDoNotCreateTopicsOrConsumerOffsets(){
        assertTrue(broker.poll("never-published","reader",8).isEmpty());
        assertTrue(broker.pollMatch("nothing.*","reader",8).isEmpty());
        assertTrue(broker.listTopics().isEmpty());
        assertTrue(broker.save(new net.minecraft.nbt.CompoundTag()).getList("topics",net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty());
    }
    @Test void legacyUnscopedMessagesAreQuarantinedInsteadOfBeingAssignedToADimension(){
        var legacy=new net.minecraft.nbt.CompoundTag();var topics=new net.minecraft.nbt.ListTag();var topic=new net.minecraft.nbt.CompoundTag();topic.putString("topic","old");topics.add(topic);legacy.put("topics",topics);
        var restored=WirelessNetworkSavedData.load(legacy,"minecraft:overworld");
        assertEquals(1,restored.quarantinedLegacyCount());assertTrue(restored.listTopics().isEmpty());
        assertEquals(1,WirelessNetworkSavedData.load(restored.save(new net.minecraft.nbt.CompoundTag()),"minecraft:overworld").quarantinedLegacyCount());
    }
    @Test void topicPayloadAndTopicCountAreBoundedBeforePublication(){
        assertTrue(!broker.canPublish("x".repeat(WirelessNetworkSavedData.MAX_TOPIC_LENGTH+1),"ok"));
        assertTrue(!broker.canPublish("ok","x".repeat(WirelessNetworkSavedData.MAX_PAYLOAD_BYTES+1)));
        for(int i=0;i<WirelessNetworkSavedData.MAX_TOPICS;i++)broker.publish("t"+i,"x");
        assertTrue(!broker.canPublish("overflow","x"));assertEquals(WirelessNetworkSavedData.MAX_TOPICS,broker.listTopics().size());
    }
    @Test void consumerOffsetsAreBoundedWithoutChangingPublishedMessages(){
        broker.publish("bounded-consumers","message");
        for(int i=0;i<WirelessNetworkSavedData.MAX_CONSUMERS_PER_TOPIC;i++)assertEquals(List.of("message"),broker.poll("bounded-consumers","c"+i,1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->broker.poll("bounded-consumers","one-too-many",1));
        assertEquals(1,broker.topicDepth("bounded-consumers"));
    }
    @Test void invalidConsumerAndWildcardInputsAreRejectedWithoutCreatingState(){
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->broker.poll("missing","",1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->broker.poll("missing","x".repeat(129),1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->broker.pollMatch("", "reader", 1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->broker.poll(null,"reader",1));
        assertTrue(broker.listTopics().isEmpty());
    }
    @Test void wildcardWorkStopsAtTheConfiguredTopicLimit(){
        for(int i=0;i<WirelessNetworkSavedData.MAX_MATCHED_TOPICS+2;i++)broker.publish(String.format("group-%02d",i),"x");
        assertEquals(WirelessNetworkSavedData.MAX_MATCHED_TOPICS,broker.pollMatch("group-*","reader",1).size());
    }
    @Test void savedDimensionCannotBeReadAsAnotherDimension(){
        broker.publish("local","message");
        var tag=broker.save(new net.minecraft.nbt.CompoundTag());
        var other=WirelessNetworkSavedData.load(tag,"minecraft:the_nether");
        assertTrue(other.listTopics().isEmpty());assertEquals(1,other.quarantinedLegacyCount());
    }
}
