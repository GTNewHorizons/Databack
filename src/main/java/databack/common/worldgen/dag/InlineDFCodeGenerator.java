package databack.common.worldgen.dag;

import databack.common.worldgen.Expr;
import databack.common.worldgen.dag.DFDagBuilder2.InlinedDAGNode;
import databack.common.worldgen.dag.codegen.CodeGenContext;

/// A trivial [IDensityFunctionFactory] that can be inlined into other kernels. An inline function cannot modify the
/// execution shape of the density function tree, and only operates on individual values.
/// By convention, inline functions emit a function into the codegen/execution context, then invoke that function at a
/// later period. This is for debugging simplicity.
public interface InlineDFCodeGenerator<State> extends DFCodeGenerator<State> {

    /// Inserts the function into the code generation context. Returns a state object that references the inserted
    /// function's name/identity. Called once per node per kernel; appends a GLSL function to the kernel preamble.
    State emitFunction(CodeGenContext context, InlinedDAGNode inline);

    /// Invokes the previously inserted function at the given coordinates. Returns a GLSL Expr.
    Expr<Float> invokeFunction(CodeGenContext context, State state, Expr<Integer> x, Expr<Integer> y, Expr<Integer> z);
}
