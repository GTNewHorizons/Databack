package databack;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import databack.common.command.DatapackCommand;
import databack.common.dto.dimension.BuiltinDimensionGenerators;
import databack.common.dto.particle.BuiltinParticles;
import databack.common.dto.worldgen.biome.BuiltinBiomeAttributes;
import databack.common.dto.worldgen.biome.BuiltinBiomes;
import databack.common.dto.worldgen.block_predicate.BuiltinBlockPredicates;
import databack.common.dto.worldgen.block_state_provider.BuiltinBlockStateProviders;
import databack.common.dto.worldgen.carver.BuiltinCarvers;
import databack.common.dto.worldgen.configured_feature.BuiltinConfiguredFeatures;
import databack.common.dto.worldgen.density_function.BuiltinDensityFunctions;
import databack.common.dto.worldgen.float_provider.BuiltinFloatProviders;
import databack.common.dto.worldgen.height_provider.BuiltinHeightProviders;
import databack.common.dto.worldgen.int_provider.BuiltinIntProviders;
import databack.common.dto.worldgen.noise_settings.NoiseGeneratorSettings;
import databack.common.dto.worldgen.placed_feature.BuiltinPlacementModifiers;
import databack.common.dto.worldgen.processor_list.BuiltinProcessors;
import databack.common.dto.worldgen.structure.BuiltinStructures;
import databack.common.dto.worldgen.structure_set.BuiltinStructurePlacements;
import databack.common.dto.worldgen.template_pool.BuiltinPoolElements;
import databack.common.handlers.BiomeList;
import databack.common.handlers.ConfiguredCarverList;
import databack.common.handlers.DimensionList;
import databack.common.handlers.DimensionTypeList;
import databack.common.handlers.ConfiguredFeatureList;
import databack.common.handlers.DatapackHandlerRegistry;
import databack.common.handlers.DatapackNoiseList;
import databack.common.handlers.DensityFunctionList;
import databack.common.handlers.NoiseSettingsList;
import databack.common.handlers.MultiNoiseBiomeSourceParameterListHandler;
import databack.common.handlers.PlacedFeatureList;
import databack.common.handlers.ProcessorListHandler;
import databack.common.handlers.StructureList;
import databack.common.handlers.StructureSetList;
import databack.common.handlers.TemplatePoolList;
import databack.common.handlers.WorldPresetList;
import databack.common.interop.BlockTags;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.MiscAdapters;
import databack.common.worldgen.ModernWorldType;
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
        BuiltinBlockStateProviders.init();
        BuiltinConfiguredFeatures.init();
        BuiltinDensityFunctions.init();
        BuiltinFloatProviders.init();
        BuiltinHeightProviders.init();
        BuiltinIntProviders.init();
        BuiltinParticles.init();
        BuiltinPlacementModifiers.init();
        BuiltinBiomeAttributes.init();
        BuiltinBiomes.init();
        BuiltinProcessors.init();
        BuiltinPoolElements.init();
        BuiltinStructures.init();
        BuiltinStructurePlacements.init();
        NoiseGeneratorSettings.init();
        BuiltinDimensionGenerators.init();

        DatapackHandlerRegistry.registerTypeHandler("dimension", DimensionList::new);
        DatapackHandlerRegistry.registerTypeHandler("dimension_type", DimensionTypeList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/configured_carver", ConfiguredCarverList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/configured_feature", ConfiguredFeatureList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/biome", BiomeList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/noise", DatapackNoiseList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/density_function", DensityFunctionList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/noise_settings", NoiseSettingsList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/placed_feature", PlacedFeatureList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/multi_noise_biome_source_parameter_list", MultiNoiseBiomeSourceParameterListHandler::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/processor_list", ProcessorListHandler::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/template_pool", TemplatePoolList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/structure", StructureList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/structure_set", StructureSetList::new);
        DatapackHandlerRegistry.registerTypeHandler("worldgen/world_preset", WorldPresetList::new);

        ModernWorldType.init();
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

    public void serverAboutToStart(FMLServerAboutToStartEvent event) {

    }
}
