package databack.common.dto.worldgen.structure_set;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({ "unused", "NotNullFieldNotInitialized" })
public class StructureSet {

    @NotNull
    public StructureSetElement[] structures;

    @NotNull
    public IStructurePlacement placement;

    public static class StructureSetElement {

        public String structure;
        public int weight;
    }
}
