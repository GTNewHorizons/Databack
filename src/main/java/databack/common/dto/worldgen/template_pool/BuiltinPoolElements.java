package databack.common.dto.worldgen.template_pool;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.placed_feature.IPlacedFeatureRef;
import databack.common.dto.worldgen.processor_list.IProcessorListRef;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

@SuppressWarnings("unused")
public class BuiltinPoolElements {

    public static void init() {
        TaggedUnionLoader<IPoolElement> loader = DatapackSerialization
            .createTaggedUnionLoader("worldgen/template_pool_element", IPoolElement.class, "element_type");

        loader.addVariant("minecraft:single_pool_element", SinglePoolElement.class);
        loader.addVariant("minecraft:legacy_single_pool_element", SinglePoolElement.class);
        loader.addVariant("minecraft:list_pool_element", ListPoolElement.class);
        loader.addVariant("minecraft:feature_pool_element", FeaturePoolElement.class);
        loader.addVariant("minecraft:empty_pool_element", EmptyPoolElement.class);
    }

    private static class SinglePoolElement implements IPoolElement {

        public String location;
        public IProcessorListRef processors;
        public String projection;
        @Nullable
        public String override_liquid_settings;
    }

    private static class ListPoolElement implements IPoolElement {

        public IPoolElement[] elements;
        public String projection;
    }

    private static class FeaturePoolElement implements IPoolElement {

        public IPlacedFeatureRef feature;
        public String projection;
    }

    private static class EmptyPoolElement implements IPoolElement {}
}
