package databack.common.worldgen;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.world.ICubicWorldType;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;

import cpw.mods.fml.common.Optional;
import databack.common.worldgen.cubic.CubicModernGenerator;
import databack.mixins.DBMods;

/// With Cubic Chunks, the world is cubic and generated over its dimension's whole range ([CubicModernGenerator]).
@Optional.Interface(iface = "com.cardinalstar.cubicchunks.api.world.ICubicWorldType", modid = "cubicchunks")
public class ModernWorldType extends WorldType implements ICubicWorldType {

    public static final ModernWorldType INSTANCE = new ModernWorldType();

    public ModernWorldType() {
        super("modern");
    }

    public static void init() {
        // loads class
    }

    @Override
    public IChunkProvider getChunkGenerator(World world, String generatorOptions) {
        return new ModernWorldGenerator(world, generatorOptions, DBMods.CUBIC_CHUNKS.isModLoaded());
    }

    /// `chunkGenerator` is the one [#getChunkGenerator] made for the world.
    @NotNull
    @Override
    @Optional.Method(modid = "cubicchunks")
    public IWorldGenerator createCubeGenerator(WorldServer world, IChunkProvider chunkGenerator) {
        ModernWorldGenerator generator = chunkGenerator instanceof ModernWorldGenerator modern ? modern
            : new ModernWorldGenerator(world, "", true);
        return new CubicModernGenerator(world, generator);
    }

    @Override
    @Optional.Method(modid = "cubicchunks")
    public IntRange getGenerationRange(World world) {
        int[] range = ModernWorldGenerator.dimensionRange(world);
        return new IntRange(range[0], range[0] + range[1]);
    }

    /// Only for the dimensions 1.7 would have this type generate ([TypeGeneratedDimensions]): Cubic Chunks asks the save's
    /// type about every dimension.
    @Override
    @Optional.Method(modid = "cubicchunks")
    public boolean hasCubicGeneratorForWorld(World world) {
        return TypeGeneratedDimensions.includes(world.provider.getClass());
    }
}
