package databack;

import java.util.Map;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;

import com.google.common.eventbus.EventBus;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.WorldAccessContainer;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import databack.common.loader.DatapackLoader;
import databack.common.loader.DatapackWorldInfo;
import lombok.val;

public class DatapackModContainer extends DummyModContainer implements WorldAccessContainer {

    public DatapackModContainer() {
        super(createMetadata());
    }

    private static ModMetadata createMetadata() {
        val meta = new ModMetadata();
        meta.modId = "databackcore";
        meta.name = "Databack Core";
        meta.version = Tags.VERSION;
        meta.dependencies.add(new DefaultArtifactVersion("databack", Tags.VERSION));
        meta.parent = "databack";
        return meta;
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        return true;
    }

    @Override
    public NBTTagCompound getDataForWriting(SaveHandler handler, WorldInfo info) {
        return ((DatapackWorldInfo) info).db$saveDatapackInfo();
    }

    @Override
    public void readData(SaveHandler handler, WorldInfo info, Map<String, NBTBase> propertyMap, NBTTagCompound tag) {
        ((DatapackWorldInfo) info).db$loadDatapackInfo(tag);
        DatapackLoader.load(handler.getWorldDirectory(), ((DatapackWorldInfo) info));
    }
}
