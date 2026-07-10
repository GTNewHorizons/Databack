package databack.common.dto.worldgen.block_predicate;

import net.minecraft.world.World;

public interface IBlockPredicate {

    boolean test(World world, int x, int y, int z);

}
