package databack;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import databack.common.command.DatapackCommand;
import databack.common.dto.particle.BuiltinParticles;
import databack.common.dto.worldgen.biome.BuiltinBiomeAttributes;
import databack.common.dto.worldgen.biome.BuiltinBiomes;
import databack.common.dto.worldgen.block_predicate.BuiltinBlockPredicates;
import databack.common.dto.worldgen.carver.BuiltinCarvers;
import databack.common.dto.worldgen.configured_feature.BuiltinConfiguredFeatures;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions;
import databack.common.dto.worldgen.height_provider.BuiltinHeightProviders;
import databack.common.dto.worldgen.int_provider.BuiltinIntProviders;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings;
import databack.common.dto.worldgen.placed_feature.BuiltinPlacementModifiers;
import databack.common.handlers.BiomeList;
import databack.common.handlers.ConfiguredCarverList;
import databack.common.handlers.DimensionTypeList;
import databack.common.handlers.ConfiguredFeatureList;
import databack.common.handlers.DatapackHandlerRegistry;
import databack.common.handlers.DatapackNoiseList;
import databack.common.handlers.DensityFunctionList;
import databack.common.handlers.NoiseSettingsList;
import databack.common.handlers.PlacedFeatureList;
import databack.common.interop.BlockTags;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.MiscAdapters;
import lombok.Getter;

public class CommonProxy {

    @Getter
    private static boolean isEFRLoaded;

    public void preInit(FMLPreInitializationEvent event) {
        try {
            ConfigurationManager.registerConfig(DatabackConfig.class);
        } catch (ConfigException e) {
            throw new RuntimeException(e);
        }

        isEFRLoaded = Loader.isModLoaded("etfuturum");

        DatapackSerialization.init();

        MiscAdapters.init();
        BuiltinCarvers.init();
        BuiltinBlockPredicates.init();
        BuiltinConfiguredFeatures.init();
        BuiltinDensityFunctions.init();
        BuiltinHeightProviders.init();
        BuiltinIntProviders.init();
        BuiltinParticles.init();
        BuiltinPlacementModifiers.init();
        BuiltinBiomeAttributes.init();
        BuiltinBiomes.init();
        NoiseGeneratorSettings.init();

        DatapackHandlerRegistry.registerTypeHandler("dimension_type", DimensionTypeList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/configured_carver", ConfiguredCarverList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/configured_feature", ConfiguredFeatureList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/biome", BiomeList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/noise", DatapackNoiseList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/density_function", DensityFunctionList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/noise_settings", NoiseSettingsList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/placed_feature", PlacedFeatureList::new);
    }

    public void init(FMLInitializationEvent event) {
        DatapackSerialization.finish();
    }

    public void postInit(FMLPostInitializationEvent event) {
        BlockTags.init();
    }

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new DatapackCommand());
    }
}
