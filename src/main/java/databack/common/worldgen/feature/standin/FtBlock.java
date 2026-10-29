package databack.common.worldgen.feature.standin;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/// Stand-in for a block: its modern id. Unique per id ([#of]), so vanilla may compare blocks with `==`. Blocks that
/// vanilla's features place specially are instances of the matching subclass.
@ApiStatus.Internal
public class FtBlock {

    private static final ConcurrentHashMap<String, FtBlock> BLOCKS = new ConcurrentHashMap<>();

    /// The two-block-tall plants: the blocks with a `half` of `lower` and `upper`.
    private static final Set<String> DOUBLE_PLANTS = new HashSet<>(Arrays.asList("minecraft:tall_grass",
        "minecraft:large_fern", "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony",
        "minecraft:tall_seagrass", "minecraft:pitcher_plant", "minecraft:pitcher_crop", "minecraft:small_dripleaf"));
    private static final Set<String> MOSSY_CARPETS = Collections.singleton("minecraft:pale_moss_carpet");

    /// Namespaced: `minecraft:stone`.
    @NotNull
    public final String id;

    FtBlock(@NotNull String id) {
        this.id = id;
    }

    /// The block with the namespaced `id`.
    @NotNull
    public static FtBlock of(@NotNull String id) {
        return BLOCKS.computeIfAbsent(id, FtBlock::create);
    }

    private static FtBlock create(String id) {
        if (DOUBLE_PLANTS.contains(id)) return new FtDoublePlantBlock(id);
        if (MOSSY_CARPETS.contains(id)) return new FtMossyCarpetBlock(id);
        return new FtBlock(id);
    }

    /// The state without properties; whatever the block's are, they take their defaults when the state is placed.
    @NotNull
    @Contract(pure = true)
    public FtBlockState defaultBlockState() {
        return FtBlockState.of(this, Collections.emptyMap());
    }

    /// The block as registered under its id.
    @NotNull
    @Contract("-> new")
    public FtHolder builtInRegistryHolder() {
        return new FtHolder(id, this);
    }

    @Override
    public String toString() {
        return id;
    }
}
