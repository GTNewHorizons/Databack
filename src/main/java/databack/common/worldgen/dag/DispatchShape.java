package databack.common.worldgen.dag;

public enum DispatchShape {
    PER_VOXEL(4096),   // 16×16×16 threads — standard density computation
    PER_COLUMN(256),   // 16×16 threads — flat/column-reduce computations
    PER_CORNER(125);   // 5×5×5 threads — Interpolated argument sampling pass only

    public final int elementCount;

    DispatchShape(int elementCount) {
        this.elementCount = elementCount;
    }
}
