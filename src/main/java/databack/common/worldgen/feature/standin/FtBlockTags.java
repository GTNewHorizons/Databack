package databack.common.worldgen.feature.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for the block tag constants the feature classes name.
@ApiStatus.Internal
public final class FtBlockTags {

    public static final FtTag AIR = new FtTag("minecraft:air");
    public static final FtTag LEAVES = new FtTag("minecraft:leaves");
    public static final FtTag LOGS = new FtTag("minecraft:logs");
    public static final FtTag REPLACEABLE_BY_TREES = new FtTag("minecraft:replaceable_by_trees");
    public static final FtTag CANNOT_REPLACE_BELOW_TREE_TRUNK = new FtTag("minecraft:cannot_replace_below_tree_trunk");
    public static final FtTag FEATURES_CANNOT_REPLACE = new FtTag("minecraft:features_cannot_replace");
    public static final FtTag LAVA_POOL_STONE_CANNOT_REPLACE = new FtTag("minecraft:lava_pool_stone_cannot_replace");

    private FtBlockTags() {}
}
