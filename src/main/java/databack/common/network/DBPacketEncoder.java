package databack.common.network;

import net.minecraft.network.INetHandler;
import net.minecraft.world.World;

public abstract class DBPacketEncoder<Packet extends DBPacket> {

    protected DBPacketEncoder() {}

    /**
     * Unique ID of this packet.
     */
    public abstract byte getPacketID();

    /**
     * Encode the data into given byte buffer.
     */
    public void writePacket(DBPacketBuffer buffer, Packet packet) {
        throw new UnsupportedOperationException("Wrong side");
    }

    /**
     * Decode byte buffer into packet object.
     */
    public Packet readPacket(DBPacketBuffer buffer) {
        throw new UnsupportedOperationException("Wrong side");
    }

    /**
     * Process the received packet.
     *
     * @param world null if message is received on server side, the client world if message is received on client side
     */
    public void process(World world, Packet packet) {
        throw new UnsupportedOperationException("Wrong side");
    }

    /**
     * This will be called just before {@link #process(World, DBPacket)}} to inform the handler about the source and
     * type of
     * connection.
     */
    public void setINetHandler(INetHandler handler, Packet packet) {}
}
