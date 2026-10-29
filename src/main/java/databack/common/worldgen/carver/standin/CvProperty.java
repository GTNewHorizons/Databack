package databack.common.worldgen.carver.standin;

import org.jetbrains.annotations.ApiStatus;

/// Stand-in for a block state property and the property constants the carvers name.
@ApiStatus.Internal
public final class CvProperty {

    public static final CvProperty WATERLOGGED = new CvProperty();

    private CvProperty() {}
}
