package databack.common.handlers;

import org.jetbrains.annotations.Nullable;

import databack.common.dto.dimension.Dimension;

public class DimensionList extends JsonDatapackTypeHandler<Dimension> {

    public static final ResourceType<DimensionList> RT = ResourceType.withPath("dimension");

    public DimensionList() {
        super(RT, Dimension.class);
    }

    @Nullable
    public Dimension getDimension(String id) {
        return super.getObject(id);
    }
}
