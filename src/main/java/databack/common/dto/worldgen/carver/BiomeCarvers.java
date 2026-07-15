package databack.common.dto.worldgen.carver;

import java.util.List;
import java.util.Map;

/** Normalized biome carver data, keyed by carve step. */
public class BiomeCarvers {

    public final Map<CarveStep, List<IConfiguredCarver>> phases;

    BiomeCarvers(Map<CarveStep, List<IConfiguredCarver>> phases) {
        this.phases = phases;
    }

}
