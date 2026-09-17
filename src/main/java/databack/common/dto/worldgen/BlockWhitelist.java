package databack.common.dto.worldgen;

import net.minecraft.block.Block;

public interface BlockWhitelist {

    boolean contains(Block block, int meta);

}
