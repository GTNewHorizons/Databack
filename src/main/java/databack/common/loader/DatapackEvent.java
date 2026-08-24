package databack.common.loader;

import java.io.File;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.eventhandler.Event;

public class DatapackEvent extends Event {

    public static class DatapackStartLoadingEvent extends DatapackEvent {

    }

    public static class DatapackGatherEvent extends DatapackEvent {

        public final List<File> candidates;

        public DatapackGatherEvent(List<File> candidates) {
            this.candidates = candidates;
        }
    }

    public static class DatapackLoadEvent extends DatapackEvent {

        public final List<Datapack> packs;

        public DatapackLoadEvent(List<Datapack> packs) {
            this.packs = packs;
        }
    }

    public static class DatapackFinishedLoadingEvent extends DatapackEvent {

    }

    public static class DatapackSyncEvent extends DatapackEvent {

        public final EntityPlayerMP player;

        public DatapackSyncEvent(EntityPlayerMP player) {
            this.player = player;
        }
    }

    /**
     * Fired just before a world starts, after {@link DatapackHandlerRegistry#clearAll()}.
     * Subscribers should call {@link DatapackHandlerRegistry#registerTypeHandler} to register
     * fresh handler instances appropriate for the current game mode (SP vs MP).
     */
    public static class DatapackRegisterHandlersEvent extends DatapackEvent {}
}
