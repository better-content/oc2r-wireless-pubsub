package com.bettercontent.oc2rwirelesspubsub.api;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;
import java.util.UUID;
/** Committed receipt on a distinct physical host; never carries message payload. */
public final class WirelessMessageReceivedEvent extends Event {
 public final MinecraftServer server;public final UUID owner;public final String operation,topic;public final int bytes,energy;
 public WirelessMessageReceivedEvent(MinecraftServer server,UUID owner,String operation,String topic,int bytes,int energy){this.server=server;this.owner=owner;this.operation=operation;this.topic=topic;this.bytes=bytes;this.energy=energy;}
}
