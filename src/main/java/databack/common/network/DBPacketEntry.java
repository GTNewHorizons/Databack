package databack.common.network;

public enum DBPacketEntry {

    SyncJsonHandler(new PacketEncoderSyncJsonHandler()),
    SyncTagHandler(new PacketEncoderSyncTagHandler()),
    //
    ;

    public final byte id = (byte) ordinal();
    public final DBPacketEncoder<?> encoder;

    DBPacketEntry(DBPacketEncoder<?> encoder) {
        this.encoder = encoder;
    }
}
