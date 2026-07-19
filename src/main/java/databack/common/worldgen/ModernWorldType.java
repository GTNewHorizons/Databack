package databack.common.worldgen;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;

public class ModernWorldType extends WorldType {

    public static final ModernWorldType INSTANCE = new ModernWorldType();

    public ModernWorldType() {
        super("modern");
    }

    public static void init() {
        // loads class
    }

    @Override
    public IChunkProvider getChunkGenerator(World world, String generatorOptions) {
        return new ModernWorldGenerator(world, generatorOptions);
    }
}
