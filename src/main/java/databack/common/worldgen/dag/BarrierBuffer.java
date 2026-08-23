package databack.common.worldgen.dag;

import com.github.bsideup.jabel.Desugar;
import databack.common.worldgen.Expr;
import databack.common.worldgen.dag.DFDagBuilder2.BarrierDAGNode;
import databack.common.worldgen.dag.codegen.CodeGenContext;
import mcgpu.core.hwaccel.buffer.BufferLayout;

public interface BarrierBuffer {

    BufferLayout getLayout();

    String getName();

    Expr<Float> index(String bindingName, Expr<Integer> x, Expr<Integer> y, Expr<Integer> z);
}
