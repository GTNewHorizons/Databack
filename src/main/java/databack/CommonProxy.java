package databack;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import databack.common.command.DatapackCommand;
import databack.common.dto.worldgen.block_predicate.BuiltinBlockPredicates;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions;
import databack.common.dto.worldgen.height_provider.BuiltinHeightProviders;
import databack.common.dto.worldgen.int_provider.BuiltinIntProviders;
import databack.common.dto.worldgen.placed_feature.BuiltinPlacementModifiers;
import databack.common.handlers.DatapackHandlerRegistry;
import databack.common.handlers.DatapackNoiseList;
import databack.common.handlers.DensityFunctionList;
import databack.common.serde.DatapackSerialization;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        try {
            ConfigurationManager.registerConfig(DatabackConfig.class);
        } catch (ConfigException e) {
            throw new RuntimeException(e);
        }

        DatapackSerialization.init();

        BuiltinBlockPredicates.init();
        BuiltinDensityFunctions.init();
        BuiltinHeightProviders.init();
        BuiltinIntProviders.init();
        BuiltinPlacementModifiers.init();

        DatapackHandlerRegistry.registerTypeHandler("worldgen/noise", DatapackNoiseList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/density_function", DensityFunctionList::new);
    }

    public void init(FMLInitializationEvent event) {
        DatapackSerialization.finish();
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new DatapackCommand());
    }
}
