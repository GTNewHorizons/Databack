package databack.common.worldgen.feature.standin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import databack.common.worldgen.vanilla.standin.VnBlockPos;
import databack.common.worldgen.vanilla.standin.VnDirection;

/// Stand-in for a block state: a modern block and the properties set on it. Unique per block and properties
/// ([#of]), so vanilla may compare states with `==`.
///
/// A state only knows the properties it was given, not every property its block has: [#hasProperty] and
/// [#getProperties] answer for the given ones.
@ApiStatus.Internal
public final class FtBlockState {

    private static final ConcurrentHashMap<FtBlockState, FtBlockState> STATES = new ConcurrentHashMap<>();
    private static final Set<String> AIR = new HashSet<>(
        Arrays.asList("minecraft:air", "minecraft:cave_air", "minecraft:void_air"));

    @NotNull
    private final FtBlock block;
    @NotNull
    private final SortedMap<String, String> properties;

    private FtBlockState(@NotNull FtBlock block, @NotNull SortedMap<String, String> properties) {
        this.block = block;
        this.properties = properties;
    }

    /// The state of `block` with `properties` (name to serialized value).
    @NotNull
    public static FtBlockState of(@NotNull FtBlock block, @NotNull Map<String, String> properties) {
        FtBlockState state = new FtBlockState(block, Collections.unmodifiableSortedMap(new TreeMap<>(properties)));
        FtBlockState interned = STATES.putIfAbsent(state, state);
        return interned != null ? interned : state;
    }

    @NotNull
    public FtBlock getBlock() {
        return block;
    }

    /// Name to serialized value, sorted by name.
    @NotNull
    public SortedMap<String, String> properties() {
        return properties;
    }

    /// Whether this is a state of `block` (a [FtBlock]).
    public boolean is(Object block) {
        return this.block == block;
    }

    public boolean is(@NotNull FtHolderSet blocks) {
        return blocks.contains(block.id);
    }

    public boolean is(@NotNull FtTag tag) {
        return FtBlockFacts.get().isInTag(block.id, tag.id);
    }

    public boolean isAir() {
        return AIR.contains(block.id);
    }

    public boolean canBeReplaced() {
        return FtBlockFacts.get().isReplaceable(this);
    }

    public boolean isSolid() {
        return FtBlockFacts.get().isSolid(this);
    }

    /// Whether the state is a full, opaque block.
    public boolean isSolidRender() {
        return FtBlockFacts.get().isSolidRender(this);
    }

    public boolean canSurvive(@NotNull FtLevel level, @NotNull VnBlockPos pos) {
        return level.canSurvive(this, pos);
    }

    public boolean isFaceSturdy(@NotNull FtLevel level, @NotNull VnBlockPos pos, @NotNull VnDirection face) {
        return level.isFaceSturdy(this, pos, face);
    }

    /// Water for water and waterlogged states, lava for lava, and empty otherwise.
    @NotNull
    public FtFluidState getFluidState() {
        if (block.id.equals("minecraft:water") || "true".equals(properties.get("waterlogged"))) {
            return FtFluidState.WATER;
        }
        return block.id.equals("minecraft:lava") ? FtFluidState.LAVA : FtFluidState.EMPTY;
    }

    @NotNull
    public Collection<FtProperty> getProperties() {
        List<FtProperty> list = new ArrayList<>();
        for (String name : properties.keySet()) list.add(FtProperty.of(name));
        return list;
    }

    public boolean hasProperty(@NotNull FtProperty property) {
        return properties.containsKey(property.getName());
    }

    /// The value of `property` as vanilla's value type, or `fallback` if the state does not have it (or its value is
    /// not one of the property's).
    @Nullable
    public Comparable<?> getValueOrElse(@NotNull FtProperty property, @Nullable Comparable<?> fallback) {
        String value = properties.get(property.getName());
        Comparable<?> parsed = value == null ? null : property.parse(value);
        return parsed != null ? parsed : fallback;
    }

    /// The state with `property` set to `value`. Unlike vanilla, never refuses a property the state lacks: states
    /// only know the properties they were given.
    @NotNull
    @Contract(pure = true)
    public Object setValue(@NotNull FtProperty property, @NotNull Comparable<?> value) {
        Map<String, String> changed = new TreeMap<>(properties);
        changed.put(property.getName(), FtProperty.serialize(value));
        return of(block, changed);
    }

    /// [#setValue] when the state has `property`, this state otherwise.
    @NotNull
    @Contract(pure = true)
    public Object trySetValue(@NotNull FtProperty property, @NotNull Comparable<?> value) {
        return hasProperty(property) ? setValue(property, value) : this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FtBlockState)) return false;
        FtBlockState other = (FtBlockState) o;
        return block == other.block && properties.equals(other.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(block, properties);
    }

    @Override
    public String toString() {
        return properties.isEmpty() ? block.id : block.id + properties;
    }
}
