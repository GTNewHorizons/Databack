package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.dimension_type.DimensionType;

public class DimensionTypeList extends JsonDatapackTypeHandler<DimensionType> {

    public static final ResourceType<DimensionTypeList> RT = ResourceType.withPath("dimension_type");

    public DimensionTypeList() {
        super(RT, DimensionType.class);
    }

    @Nullable
    public DimensionType getDimensionType(String id) {
        return super.getObject(id);
    }
}
