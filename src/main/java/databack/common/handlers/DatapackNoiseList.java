package databack.common.handlers;

import databack.common.dto.worldgen.noise.DatapackNoise;

public class DatapackNoiseList extends JsonDatapackTypeHandler<DatapackNoise> {

    public DatapackNoiseList() {
        super("worldgen/noise", DatapackNoise.class);
    }

}
