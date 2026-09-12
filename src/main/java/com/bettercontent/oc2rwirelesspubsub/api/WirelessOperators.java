package com.bettercontent.oc2rwirelesspubsub.api;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Explicit host placement retains attribution; reading a computer never transfers it. */
@Mod.EventBusSubscriber(modid="oc2r_wireless_pubsub")
public final class WirelessOperators {
 public static final String KEY="Oc2rWirelessOperator";
 private WirelessOperators(){}
 @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent event){if(!event.isCanceled()&&event.getEntity() instanceof ServerPlayer player){var host=event.getLevel().getBlockEntity(event.getPos());if(host!=null){host.getPersistentData().putUUID(KEY,player.getUUID());host.setChanged();}}}
}
