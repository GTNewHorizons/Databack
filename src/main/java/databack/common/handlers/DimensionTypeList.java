package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.dimension_type.DimensionType;

public class DimensionTypeList extends JsonDatapackTypeHandler<DimensionType> {

    public static final DimensionTypeList INSTANCE = new DimensionTypeList();

    public DimensionTypeList() {
        super("dimension_type", DimensionType.class);
    }

    @Nullable
    public DimensionType getDimensionType(String id) {
        return super.getObject(id);
    }
}
