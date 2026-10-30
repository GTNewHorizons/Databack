package databack.common.worldgen.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.dto.BlockStateDTO;
import databack.common.dto.worldgen.FluidState;
import databack.common.dto.worldgen.configured_feature.IConfiguredFeature;
import databack.common.dto.worldgen.height_provider.IHeightProvider;
import databack.common.dto.worldgen.int_provider.IIntProvider;
import databack.common.dto.worldgen.placed_feature.IPlacedFeatureRef;
import databack.common.dto.worldgen.placed_feature.PlacedFeature;
import databack.common.worldgen.feature.standin.FtBlock;
import databack.common.worldgen.feature.standin.FtBlockFacts;
import databack.common.worldgen.feature.standin.FtBlockState;
import databack.common.worldgen.feature.standin.FtFluidState;
import databack.common.worldgen.feature.standin.FtFluids;
import databack.common.worldgen.feature.standin.FtHeightProvider;
import databack.common.worldgen.feature.standin.FtHolder;
import databack.common.worldgen.feature.standin.FtHolderSet;
import databack.common.worldgen.feature.standin.FtIntProvider;
import databack.common.worldgen.feature.standin.FtIntRange;
import databack.common.worldgen.feature.standin.FtTag;
import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnDirection;

/// What feature DTOs need to become vanilla's feature objects: the vanilla classes, the datapack's features by id,
/// and conversions of the values DTOs share. Features are built once per id. Not thread-safe; build on one thread.
///
/// Anything that cannot be built is reported once per kind ([#unsupported]) and gives null; whatever contains it is
/// then not built either.
public final class FeatureBuildContext {

    private static final Logger LOGGER = LogManager.getLogger("databack|features");

    @NotNull
    public final VanillaFeatures vanilla;
    @NotNull
    private final Function<String, IConfiguredFeature> configuredFeatures;
    @NotNull
    private final Function<String, PlacedFeature> placedFeatures;

    private final Map<String, FtHolder> configured = new HashMap<>(), placed = new HashMap<>();
    /// Ids being built, to catch features that contain themselves.
    private final Set<String> building = new HashSet<>();
    private final Set<String> warned = new HashSet<>();
    /// See [#nothing].
    @Nullable
    private FtHolder nothing;

    /// `configuredFeatures` and `placedFeatures` give the datapack's definitions by id, or null for unknown ids.
    public FeatureBuildContext(
        @NotNull VanillaFeatures vanilla, @NotNull Function<String, IConfiguredFeature> configuredFeatures,
        @NotNull Function<String, PlacedFeature> placedFeatures
    ) {
        this.vanilla = vanilla;
        this.configuredFeatures = configuredFeatures;
        this.placedFeatures = placedFeatures;
    }

    /// Logs once per `what` that it is not supported, so whatever needs it is skipped. Always null.
    @Nullable
    @Contract("_ -> null")
    public <T> T unsupported(@NotNull String what) {
        if (warned.add(what)) LOGGER.warn("{} is not supported yet; features that use it are skipped", what);
        return null;
    }

    /// A placed feature that places nothing. Selectors use it for options that cannot be built, so the others keep
    /// their chances.
    @NotNull
    public FtHolder nothing() {
        if (nothing == null) {
            Object configured = vanilla.construct("feature/ConfiguredFeature", vanilla.featureType("NO_OP"),
                vanilla.construct("feature/configurations/NoneFeatureConfiguration"));
            nothing = new FtHolder(null, vanilla.construct("placement/PlacedFeature", new FtHolder(null, configured),
                Collections.emptyList()));
        }
        return nothing;
    }

    /// `ref` built, or [#nothing] if it cannot be (already logged).
    @NotNull
    public FtHolder placedOrNothing(@NotNull IPlacedFeatureRef ref) {
        FtHolder feature = ref.instantiate(this);
        return feature != null ? feature : nothing();
    }

    /// The configured feature `id` as a holder of vanilla's configured feature, or null.
    @Nullable
    public FtHolder configuredFeature(@NotNull String id) {
        return resolve("configured feature", id, configured, () -> {
            IConfiguredFeature definition = configuredFeatures.apply(id);
            return definition == null ? null : definition.instantiate(this);
        });
    }

    /// The placed feature `id` as a holder of vanilla's placed feature, or null.
    @Nullable
    public FtHolder placedFeature(@NotNull String id) {
        return resolve("placed feature", id, placed, () -> {
            PlacedFeature definition = placedFeatures.apply(id);
            return definition == null ? null : definition.build(this);
        });
    }

    private interface Builder {

        @Nullable
        Object build();
    }

    private FtHolder resolve(String kind, String id, Map<String, FtHolder> built, Builder builder) {
        if (built.containsKey(id)) return built.get(id);
        // Placed and configured features often share ids.
        String key = kind + " " + id;
        if (!building.add(key)) {
            LOGGER.warn("The {} {} contains itself; skipping it", kind, id);
            return null;
        }

        try {
            Object value = builder.build();
            if (value == null && warned.add(key)) LOGGER.warn("Skipping the {} {}", kind, id);

            FtHolder holder = value == null ? null : new FtHolder(id, value);
            built.put(id, holder);
            return holder;
        } finally {
            building.remove(key);
        }
    }

    @NotNull
    public FtBlockState state(@NotNull BlockStateDTO state) {
        Map<String, String> properties = state.Properties == null ? Collections.emptyMap() : state.Properties;
        return FtBlockState.of(FtBlock.of(state.Name.toString()), properties);
    }

    @NotNull
    public List<FtBlockState> states(@NotNull List<BlockStateDTO> states) {
        List<FtBlockState> list = new ArrayList<>(states.size());
        for (BlockStateDTO state : states) list.add(state(state));
        return list;
    }

    /// A block id, namespaced if it was not.
    @NotNull
    public FtBlock block(@NotNull String id) {
        return FtBlock.of(id.indexOf(':') < 0 ? "minecraft:" + id : id);
    }

    /// The blocks of a block list: ids and `#tags`.
    @NotNull
    public FtHolderSet blocks(@NotNull List<String> entries) {
        List<FtHolder> listed = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        for (String entry : entries) {
            if (entry.startsWith("#")) {
                tags.add(entry.substring(1));
            } else {
                FtBlock block = block(entry);
                listed.add(new FtHolder(block.id, block));
            }
        }
        if (tags.isEmpty()) return FtHolderSet.of(listed);

        FtHolderSet direct = FtHolderSet.of(listed);
        return FtHolderSet.matching(entries.toString(), id -> {
            if (direct.contains(id)) return true;
            for (String tag : tags) {
                if (FtBlockFacts.get().isInTag(id, tag)) return true;
            }
            return false;
        });
    }

    /// A block tag by its namespaced id, without `#`.
    @NotNull
    public FtTag tag(@NotNull String id) {
        return new FtTag(id.indexOf(':') < 0 ? "minecraft:" + id : id);
    }

    /// A datapack's fluid state, or null when it isn't water's or lava's source: no vanilla configuration names a
    /// flowing one, and their levels aren't kept ([FtFluidState#createLegacyBlock]).
    @Nullable
    public FtFluidState fluidState(@NotNull FluidState state) {
        String id = state.Name.indexOf(':') < 0 ? "minecraft:" + state.Name : state.Name;
        if (id.equals(FtFluids.WATER.id)) return FtFluidState.WATER;
        if (id.equals(FtFluids.LAVA.id)) return FtFluidState.LAVA;
        return unsupported("Fluid state " + id);
    }

    /// The fluids of a fluid list, or null when it names a tag other than water's or lava's. Water's and lava's tags
    /// hold both forms of their fluid ([FtFluidState]).
    @Nullable
    public FtHolderSet fluids(@NotNull List<String> entries) {
        Set<String> fluids = new HashSet<>();
        for (String entry : entries) {
            boolean tag = entry.startsWith("#");
            String id = tag ? entry.substring(1) : entry;
            if (id.indexOf(':') < 0) id = "minecraft:" + id;

            if (!tag) {
                fluids.add(id);
            } else if (id.equals("minecraft:water") || id.equals("minecraft:lava")) {
                fluids.add(id);
                fluids.add(id.replace(":", ":flowing_"));
            } else {
                return unsupported("Fluid tag " + entry);
            }
        }
        return FtHolderSet.matching(entries.toString(), fluids::contains);
    }

    /// A block offset; none is zero.
    @NotNull
    public VnBlockPos offset(int @Nullable [] offset) {
        return offset == null ? VnBlockPos.ZERO : new VnBlockPos(offset[0], offset[1], offset[2]);
    }

    /// A direction by its serialized name (`up`), or null for an unknown name.
    @Nullable
    public VnDirection direction(@NotNull String name) {
        for (VnDirection direction : VnDirection.values()) {
            if (direction.getSerializedName().equals(name)) return direction;
        }
        return null;
    }

    /// An inclusive `[min, max]` range.
    @NotNull
    public FtIntRange intRange(int @NotNull [] range) {
        return new FtIntRange(range[0], range[1]);
    }

    @NotNull
    public FtIntProvider intProvider(@NotNull IIntProvider provider) {
        return FtIntProvider.of(provider);
    }

    @NotNull
    public FtHeightProvider heightProvider(@NotNull IHeightProvider provider) {
        return new FtHeightProvider(provider);
    }
}
