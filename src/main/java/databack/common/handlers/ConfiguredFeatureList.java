package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.configured_feature.IConfiguredFeature;

public class ConfiguredFeatureList extends JsonDatapackTypeHandler<IConfiguredFeature> {

    public static final ConfiguredFeatureList INSTANCE = new ConfiguredFeatureList();

    public ConfiguredFeatureList() {
        super("worldgen/configured_feature", IConfiguredFeature.class);
    }

    @Nullable
    public IConfiguredFeature getConfiguredFeature(String id) {
        return super.getObject(id);
    }
}
