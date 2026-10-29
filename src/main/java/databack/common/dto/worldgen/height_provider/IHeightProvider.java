package databack.common.dto.worldgen.height_provider;

import java.util.Random;

public interface IHeightProvider {

    /// A height in a dimension spanning `height` blocks from `minY` (anchors resolve against it).
    int get(Random random, int minY, int height);

}
