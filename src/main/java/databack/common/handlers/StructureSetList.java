package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.structure_set.StructureSet;

public class StructureSetList extends JsonDatapackTypeHandler<StructureSet> {

    public StructureSetList() {
        super("worldgen/structure_set", StructureSet.class);
    }

    @Nullable
    public StructureSet getStructureSet(String id) {
        return super.getObject(id);
    }
}
