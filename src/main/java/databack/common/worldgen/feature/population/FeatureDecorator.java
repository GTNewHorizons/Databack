package databack.common.worldgen.feature.population;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/// Places a chunk's features, once [DeferredPopulation] finds its 3×3 generated.
///
/// Not implemented yet: features are not built from the datapack, so this warns once and places nothing.
public final class FeatureDecorator implements DeferredPopulation.Decorator {

    private static final Logger LOGGER = LogManager.getLogger("databack|features");

    private boolean warned;

    @Override
    public void decorate(int chunkX, int chunkZ) {
        if (warned) return;
        warned = true;
        LOGGER.warn("Feature population is not implemented yet; chunks are left undecorated");
    }
}
