package databack.common.worldgen.dag;

import databack.common.dto.worldgen.density_function.IDensityFunction;

/// A marker interface that indicates an object is something that can generate code within a [IDensityFunction] compute
/// context. This is typically used to generate GPGPU kernels, but it could also be used to generate native ASTs/machine
/// code or java bytecode.
/// All functions must provide a float value for a given voxel, but the semantics of how this is done differs between
/// inline and barrier functions.
/// A given [IDensityFunction] is always either a barrier node or an inline node - it cannot be dynamically determined.
/// Pretend this interface is sealed, you're always meant to implement [InlineDFCodeGenerator] or [BarrierDFCodeGenerator].
public interface DFCodeGenerator<State> {

}
