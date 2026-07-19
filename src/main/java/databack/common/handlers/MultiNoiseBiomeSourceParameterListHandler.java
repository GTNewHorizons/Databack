package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.multi_noise_biome_source.MultiNoiseBiomeSourceParameterList;

public class MultiNoiseBiomeSourceParameterListHandler
        extends JsonDatapackTypeHandler<MultiNoiseBiomeSourceParameterList> {

    public MultiNoiseBiomeSourceParameterListHandler() {
        super("worldgen/multi_noise_biome_source_parameter_list", MultiNoiseBiomeSourceParameterList.class);
    }

    @Nullable
    public MultiNoiseBiomeSourceParameterList getParameterList(String id) {
        return super.getObject(id);
    }
}
