package databack.common.dto.worldgen.placed_feature;

import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import databack.common.collection.Pos3DArrayList;
import databack.common.dto.worldgen.block_predicate.IBlockPredicate;
import databack.common.dto.worldgen.height_provider.IHeightProvider;
import databack.common.dto.worldgen.int_provider.IIntProvider;
import databack.common.interop.heightmap.HeightmapType;
import databack.common.interop.heightmap.WorldHeightmapExt;
import databack.common.serde.DatapackSerialization;
import databack.common.serde.TaggedUnionLoader;

public class BuiltinPlacementModifiers {

    public static void init() {
        TaggedUnionLoader<IPlacementModifier> placementModifiers = DatapackSerialization
            .getTaggedUnionLoader("builtin/placement_modifiers");

        placementModifiers.addVariant("minecraft:block_predicate_filter", BlockPredicateFilterMod.class);
        placementModifiers.addVariant("minecraft:carving_mask", CarvingMaskMod.class);
        placementModifiers.addVariant("minecraft:rarity_filter", RarityFilterMod.class);
        placementModifiers.addVariant("minecraft:count", CountMod.class);
        placementModifiers.addVariant("minecraft:count_on_every_layer", CountOnEveryLayerMod.class);
        placementModifiers.addVariant("minecraft:noise_threshold_count", NoiseThresholdCountMod.class);
        placementModifiers.addVariant("minecraft:noise_based_count", NoiseBasedCountMod.class);
        placementModifiers.addVariant("minecraft:environment_scan", EnvironmentScanMod.class);
        placementModifiers.addVariant("minecraft:fixed_placement", FixedPlacementMod.class);
        placementModifiers.addVariant("minecraft:heightmap", HeightmapMod.class);
        placementModifiers.addVariant("minecraft:height_range", HeightRangeMod.class);
        placementModifiers.addVariant("minecraft:random_offset", RandomOffsetMod.class);
        placementModifiers.addVariant("minecraft:surface_relative_threshold_filter", SurfaceRelativeThresholdFilterMod.class);
        placementModifiers.addVariant("minecraft:surface_water_depth_filter", SurfaceWaterDepthFilterMod.class);
    }

    private enum SearchDirection {
        up,
        down;
    }

    private static class BlockPredicateFilterMod implements IPlacementModifier {

        public IBlockPredicate predicate;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            positions.removeIf((x, y, z) -> !predicate.test(world, x, y, z));
            return positions;
        }
    }

    private static class CarvingMaskMod implements IPlacementModifier {

        public String step;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            return positions; // TODO
        }
    }

    private static class RarityFilterMod implements IPlacementModifier {

        public int chance;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            if (world.rand.nextInt(chance) != 0) positions.clear();
            return positions;
        }
    }

    private static class CountMod implements IPlacementModifier {

        public IIntProvider count;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            Pos3DArrayList result = new Pos3DArrayList();
            positions.forEach((x, y, z) -> {
                int n = count.get(world.rand);
                for (int i = 0; i < n; i++) result.add(x, y, z);
            });
            return result;
        }
    }

    private static class CountOnEveryLayerMod implements IPlacementModifier {

        public IIntProvider count;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            return positions; // TODO
        }
    }

    private static class NoiseThresholdCountMod implements IPlacementModifier {

        public double noise_level;
        public double below_noise;
        public double above_noise;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            return positions; // TODO
        }
    }

    private static class NoiseBasedCountMod implements IPlacementModifier {

        public double noise_to_count_ratio;
        public double noise_factor;
        @Nullable public Double noise_offset;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            return positions; // TODO
        }
    }

    private static class EnvironmentScanMod implements IPlacementModifier {

        public SearchDirection direction_of_search;
        public int max_steps;
        public IBlockPredicate target_condition;
        @Nullable public IBlockPredicate allowed_search_condition;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            Pos3DArrayList result = new Pos3DArrayList();
            int dy = direction_of_search == SearchDirection.up ? 1 : -1;

            positions.forEach((x, y, z) -> {
                int ty = y;

                for (int step = 0; step < max_steps; step++) {
                    if (target_condition.test(world, x, ty, z)) {
                        result.add(x, ty, z);
                        break;
                    }
                    if (allowed_search_condition != null && !allowed_search_condition.test(world, x, ty, z)) break;
                    ty += dy;
                }
            });

            return result;
        }
    }

    private static class FixedPlacementMod implements IPlacementModifier {

        public int[][] positions;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            Pos3DArrayList result = new Pos3DArrayList();
            for (int[] pos : this.positions) result.add(pos[0], pos[1], pos[2]);
            return result;
        }
    }

    private static class HeightmapMod implements IPlacementModifier {

        public HeightmapType heightmap;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            var heightmap = WorldHeightmapExt.getHeightmap(world, this.heightmap);

            positions.replaceAll((Vector3i v) -> v.set(v.x(), heightmap.getTop(v.x(), v.y(), v.z()), v.z()));
            return positions;
        }
    }

    private static class HeightRangeMod implements IPlacementModifier {

        public IHeightProvider height;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            positions.replaceAll((Vector3i v) -> v.set(v.x(), height.get(world.rand), v.z()));
            return positions;
        }
    }

    private static class RandomOffsetMod implements IPlacementModifier {

        public IIntProvider xz_spread;
        public IIntProvider y_spread;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            positions.replaceAll((Vector3i v) -> v.set(
                v.x() + xz_spread.get(world.rand),
                v.y() + y_spread.get(world.rand),
                v.z() + xz_spread.get(world.rand)));
            return positions;
        }
    }

    private static class SurfaceRelativeThresholdFilterMod implements IPlacementModifier {

        public HeightmapType heightmap;
        @Nullable public Integer min_inclusive;
        @Nullable public Integer max_inclusive;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            var heightmap = WorldHeightmapExt.getHeightmap(world, this.heightmap);

            positions.removeIf((x, y, z) -> {
                int surface = heightmap.getTop(x, y, z);
                int dist = y - surface;
                return (min_inclusive != null && dist < min_inclusive)
                    || (max_inclusive != null && dist > max_inclusive);
            });
            return positions;
        }
    }

    private static class SurfaceWaterDepthFilterMod implements IPlacementModifier {

        public int max_water_depth;

        @Override
        public Pos3DArrayList apply(World world, Pos3DArrayList positions) {
            return positions; // TODO
        }
    }
}
