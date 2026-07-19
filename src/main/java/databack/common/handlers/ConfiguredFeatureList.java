package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.configured_feature.IConfiguredFeature;

public class ConfiguredFeatureList extends JsonDatapackTypeHandler<IConfiguredFeature> {

    public static final ResourceType<ConfiguredFeatureList> RT = ResourceType.withPath("worldgen/configured_feature");

    public ConfiguredFeatureList() {
        super(RT, IConfiguredFeature.class);
    }

    @Nullable
    public IConfiguredFeature getConfiguredFeature(String id) {
        return super.getObject(id);
    }
}
