package databack.common.dto.worldgen.density_function;

public enum DensityFuncTrait {
    /// The density function ignores the Y coordinate completely.
    Flat,
    /// The density function always produces the same value, regardless of the block coordinate.
    Constant,
    //
    ;
}
