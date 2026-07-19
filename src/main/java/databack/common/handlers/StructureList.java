package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.worldgen.structure.IStructure;

public class StructureList extends JsonDatapackTypeHandler<IStructure> {

    public StructureList() {
        super("worldgen/structure", IStructure.class);
    }

    @Nullable
    public IStructure getStructure(String id) {
        return super.getObject(id);
    }
}
