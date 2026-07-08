package databack.common.loader;

import java.io.File;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.eventhandler.Event;

public class DatapackEvent extends Event {

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

        public DatapackFinishedLoadingEvent() {

        }
    }

    public static class DatapackSyncEvent extends DatapackEvent {

        public DatapackSyncEvent(EntityPlayerMP player) {

        }
    }
}
