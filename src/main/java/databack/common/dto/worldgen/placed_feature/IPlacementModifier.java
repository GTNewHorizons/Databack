package databack.common.dto.worldgen.placed_feature;

import net.minecraft.world.World;

import databack.common.collection.Pos3DArrayList;

public interface IPlacementModifier {

    Pos3DArrayList apply(World world, Pos3DArrayList positions, String featureId);

}
