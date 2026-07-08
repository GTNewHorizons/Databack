package databack.common.network;

import net.minecraft.entity.player.EntityPlayerMP;

public interface DBPacket {

    byte getPacketID();

    default void sendToPlayer(EntityPlayerMP player) {
        DBNetworkChannel.CHANNEL.sendToPlayer(this, player);
    }
}
