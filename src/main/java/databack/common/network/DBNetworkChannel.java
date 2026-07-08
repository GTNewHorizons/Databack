package databack.common.network;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.FMLOutboundHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import databack.Databack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.MessageToMessageCodec;

@SuppressWarnings("unused")
@ChannelHandler.Sharable
public class DBNetworkChannel extends MessageToMessageCodec<FMLProxyPacket, DBPacket> {

    private final EnumMap<Side, FMLEmbeddedChannel> channel;
    private final DBPacketEncoder<DBPacket>[] encoders;

    public static final DBNetworkChannel CHANNEL = new DBNetworkChannel();

    public DBNetworkChannel() {
        this.channel = NetworkRegistry.INSTANCE.newChannel(Databack.MODID, this, new HandlerShared());

        DBPacketEncoder<?>[] packetTypes = Arrays.stream(DBPacketEntry.values())
            .map(e -> e.encoder)
            .toArray(DBPacketEncoder[]::new);

        final int maxPacketID = Arrays.stream(packetTypes)
            .mapToInt(DBPacketEncoder::getPacketID)
            .max()
            .getAsInt();

        // noinspection unchecked
        this.encoders = new DBPacketEncoder[maxPacketID + 1];

        for (DBPacketEncoder<?> packetType : packetTypes) {
            int packetID = packetType.getPacketID();
            if (this.encoders[packetID] == null) {
                // noinspection unchecked
                this.encoders[packetID] = (DBPacketEncoder<DBPacket>) packetType;
            } else {
                throw new IllegalArgumentException("Duplicate Packet ID! " + packetID);
            }
        }
    }

    public static void init() {
        // forces this class to be loaded
    }

    @Override
    protected void encode(ChannelHandlerContext context, DBPacket packet, List<Object> output) {
        ByteBuf buffer = Unpooled.buffer()
            .writeByte(packet.getPacketID());
        DBPacketEncoder<DBPacket> encoder = this.encoders[packet.getPacketID()];
        encoder.writePacket(new DBPacketBuffer(buffer), packet);

        output.add(
            new FMLProxyPacket(
                buffer,
                context.channel()
                    .attr(NetworkRegistry.FML_CHANNEL)
                    .get()));
    }

    @Override
    protected void decode(ChannelHandlerContext context, FMLProxyPacket proxyPacket, List<Object> output) {
        ByteBuf buffer;

        buffer = proxyPacket.payload();

        DBPacketEncoder<DBPacket> encoder = this.encoders[buffer.readByte()];
        DBPacket packet = encoder.readPacket(new DBPacketBuffer(buffer));
        encoder.setINetHandler(proxyPacket.handler(), packet);
        output.add(packet);
    }

    public void sendToPlayer(DBPacket packet, EntityPlayerMP player) {
        if (packet == null) {
            Databack.LOGGER.info("packet null");
            return;
        }
        if (player == null) {
            Databack.LOGGER.info("player null");
            return;
        }
        this.channel.get(Side.SERVER)
            .attr(FMLOutboundHandler.FML_MESSAGETARGET)
            .set(FMLOutboundHandler.OutboundTarget.PLAYER);
        this.channel.get(Side.SERVER)
            .attr(FMLOutboundHandler.FML_MESSAGETARGETARGS)
            .set(player);
        this.channel.get(Side.SERVER)
            .writeAndFlush(packet);
    }

    public void sendToAllAround(DBPacket packet, NetworkRegistry.TargetPoint position) {
        this.channel.get(Side.SERVER)
            .attr(FMLOutboundHandler.FML_MESSAGETARGET)
            .set(FMLOutboundHandler.OutboundTarget.ALLAROUNDPOINT);
        this.channel.get(Side.SERVER)
            .attr(FMLOutboundHandler.FML_MESSAGETARGETARGS)
            .set(position);
        this.channel.get(Side.SERVER)
            .writeAndFlush(packet);
    }

    public void sendToAll(DBPacket packet) {
        this.channel.get(Side.SERVER)
            .attr(FMLOutboundHandler.FML_MESSAGETARGET)
            .set(FMLOutboundHandler.OutboundTarget.ALL);
        this.channel.get(Side.SERVER)
            .writeAndFlush(packet);
    }

    public void sendToServer(DBPacket packet) {
        this.channel.get(Side.CLIENT)
            .attr(FMLOutboundHandler.FML_MESSAGETARGET)
            .set(FMLOutboundHandler.OutboundTarget.TOSERVER);
        this.channel.get(Side.CLIENT)
            .writeAndFlush(packet);
    }

    public void sendPacketToAllPlayersInRange(World world, DBPacket packet, int x, int z) {
        if (!world.isRemote) {
            for (Object tObject : world.playerEntities) {
                if (!(tObject instanceof EntityPlayerMP tPlayer)) {
                    break;
                }
                Chunk tChunk = world.getChunkFromBlockCoords(x, z);
                if (tPlayer.getServerForPlayer()
                    .getPlayerManager()
                    .isPlayerWatchingChunk(tPlayer, tChunk.xPosition, tChunk.zPosition)) {
                    sendToPlayer(packet, tPlayer);
                }
            }
        }
    }

    @Sharable
    private class HandlerShared extends SimpleChannelInboundHandler<DBPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DBPacket packet) {
            World world = FMLCommonHandler.instance()
                .getEffectiveSide()
                .isClient() ? getClientWorld() : null;

            encoders[packet.getPacketID()].process(world, packet);
        }

        @SideOnly(Side.CLIENT)
        private World getClientWorld() {
            return Minecraft.getMinecraft().theWorld;
        }
    }
}
