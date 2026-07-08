package databack;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import databack.common.handlers.DatapackNoiseList;
import databack.common.handlers.DatapackHandlerRegistry;
import databack.common.serde.DatapackSerialization;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        try {
            ConfigurationManager.registerConfig(DatabackConfig.class);
        } catch (ConfigException e) {
            throw new RuntimeException(e);
        }

        DatapackSerialization.init();
    }

    public void init(FMLInitializationEvent event) {
        DatapackSerialization.finish();
        DatapackHandlerRegistry.registerTypeHandler("worldgen/noise", DatapackNoiseList::new);
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
