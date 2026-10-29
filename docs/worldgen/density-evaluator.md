# Density evaluator: how it works

Companion to [`density-functions.md`](density-functions.md). That document is the **behaviour spec**: what values a
`worldgen/density_function` tree must produce. This one explains **how the code in
`src/main/java/databack/common/worldgen/density/` produces them**, why it is shaped the way it is, and what to watch out
for when changing it.

The evaluator is a clean-room implementation. Its sources of truth are the spec, the reference data in
`src/test/resources/vanilla_reference/`, and the black-box harness in `misc/vanilla-reference/`. Never Minecraft's
code (see `CLAUDE.md`).

## Goals

1. **Exact.** Every value is bit-for-bit what 26.1 produces; only the sign of a zero may differ. `VanillaParityTest`
   enforces this.
2. **Fast.** Terrain density for an overworld chunk (y 0..255, neighbouring columns shared) costs about 7M CPU cycles.
   Most of that is noise sampling, which lives in `worldgen/noise` and is out of this package's hands.

## Pipeline at a glance

```
datapack JSON
  │  gson (TaggedUnionLoader)
  ▼
DTOs: dto/worldgen/density_function/*        IDensityFunctionFactory.instantiate(ctx)
  │
  ▼
DensityBuildContext                          seeds noises, builds each DTO once (shared subtrees → same object)
  │
  ▼
DensityNode tree (immutable)                 sample(x, y, z) = single-point value
  │  DensityOptimizer.optimize (optional, pure trees only)
  ▼
DensityRouter                                one tree per noise_router field
  │
  │  final_density only:
  ▼
ChunkDensitySampler ──► ChunkPlan            forChunk → intern → ChunkAnalysis → marker order
                         │
           pure? ────────┼──────────── stateful?
                         ▼                      ▼
              PureChunkEvaluator       StatefulChunkEvaluator
              (per thread, fast)       (per thread, exact replay of spec §5)
                         │
                         ▼
              double[height * 256] at ((y - minY) * 16 + z) * 16 + x
```

`ModernWorldGenerator` gets chunk densities through `DensityPrefetcher`, which runs `ChunkDensitySampler` on worker
threads ahead of time.

## File map

| Area | Files |
|---|---|
| Node base and arithmetic | `DensityNode`, `DensityMath` |
| Constructors used by the DTOs | `Density` |
| Leaves | `ConstantNode`, `GradientNode` (`y_clamped_gradient`), `PlaceholderNode` (`blend_alpha`, `blend_offset`, beardifier), `NoiseNode` (`noise`, `shift_a`, `shift_b`, `shift`), `OldBlendedNoise`, `EndIslands` |
| One input | `TransformNode` (`abs` … `squeeze`), `ClampNode`, `BlendDensityNode`, `AffineNode` (constant `add`/`mul`) |
| Several inputs | `PairNode` (general `add`/`mul`/`min`/`max`), `RangeChoiceNode`, `ShiftedNoiseNode`, `WeirdScaledNode`, `FindTopSurfaceNode`, `SplineNode` + `Spline` |
| Markers | `MarkerNode` (`interpolated`, `flat_cache`, `cache_2d`, `cache_once`, `cache_all_in_cell`) |
| Build | `DensityBuildContext`, `SeededNoise`, `DensityRouter`, `DensityOptimizer` |
| Chunk evaluation | `ChunkDensitySampler`, `ChunkPlan`, `ChunkAnalysis`, `ChunkEvaluator`, `PureChunkEvaluator`, `CellOps`, `SharedSubexpressions`, `ColumnCache`, `StatefulChunkEvaluator`, `DensityPrefetcher` |

`OldBlendedNoise`, `EndIslands` and everything in `worldgen/noise` and `worldgen/rng` hold kept noise code: their
algorithms are not to be changed, only how they plug into `DensityNode`.

## The node model

`DensityNode` is an immutable, seeded function of the block position:

- `sample(x, y, z)` returns the **single-point** value: every marker is transparent (spec §2).
- `lo`, `hi` hold the **bound estimate** (spec §1). Estimates are behaviour, not hints. `min` skips its second argument
  when the first value is below `b.lo`, and `max` when it is above `b.hi`, even when the estimate is wrong, and some
  estimates are wrong on purpose: the `mul` rule is unsound for mixed signs and must be reproduced exactly.
- `inputs()` / `withInputs(...)` / `rewrite(f)` give a generic bottom-up rewrite. `withInputs` **keeps the node's
  current estimate**; it does not recompute it.
- `withBounds(lo, hi)` returns a copy with another estimate.
- `equals` / `hashCode` are structural: same class, same parameters, same estimate, equal inputs. Hashes are computed
  once, in the constructor. Noise nodes compare their `SeededNoise` by identity, which works because
  `DensityBuildContext` creates one per id.

### Invariant: rewrites never change an estimate

Estimates propagate upwards (an `add`'s estimate is built from its inputs'), and any of them can end up as the `b` of a
`min`/`max` somewhere higher. So anything that replaces node `N` with `R` must hand `R` the estimate of `N`:

- `DensityOptimizer.apply` calls `withBounds` when a simplification changed the estimate. A folded `square(3)` is a
  `ConstantNode` 9 with estimate `[3, 9]`.
- `PureChunkEvaluator.Binder.bind` does the same for its replacements (transparent markers, memos), using `Rebound`
  for its own node types.
- Live nodes in `StatefulChunkEvaluator` read estimates from the original nodes.

### Constant forms and the leading-zero exception

`Density.add` / `Density.mul` choose the **constant form** (`AffineNode`, `input + c` / `input * c`) when either argument
is a `ConstantNode`: a bare number or `minecraft:constant`. `PlaceholderNode`s are *not* constants
(case `blend_offset_constant`).

One exception, found with the harness: `mul` whose **first** argument is the constant 0 (`AffineNode.zeroFirst`):

- evaluated standalone at a single point: `0.0`, input not evaluated;
- evaluated as part of a chunk, anywhere (cell level, corners, `flat_cache` arguments, `find_top_surface` densities):
  `input * 0`, which is NaN for an infinite input.

`ChunkPlan.forChunk` rewrites every `zeroFirst` node to the plain form before anything chunk-related sees the tree.
The optimizer only folds such a node when its input is finite (where both forms agree).

## Building: `DensityBuildContext`

- Holds the seeded random factory (legacy or xoroshiro, per `legacy_random_source`) and creates each `worldgen/noise`
  once per id (`noise(id)` → `SeededNoise`). `nether/temperature` and `nether/vegetation` always use the legacy
  seeding.
- `build(factory)` memoizes per DTO, so identical DTO subtrees become one runtime object. DTOs must build their
  children through `ctx.build`, not `instantiate`.
- `terrainRandom()` returns a fresh random per `old_blended_noise`.
- `buildRouter(router, optimize)` builds every field; with `optimize`, each one goes through `DensityOptimizer`.
- Unknown references log a warning and become `Density.ZERO`. `interval_select` (26.2 only) logs a warning and becomes
  `Density.ZERO`.

## The optimizer: `DensityOptimizer`

Build-time simplification that never changes a value (up to the sign of a zero): constant folding, identities
(`x + 0`, `x * 1`, finite `x * 0`), clamps that can't trigger, `min`/`max` where one side always wins, `range_choice`
with a decided input, markers of finite constants, and deduplication of equal subtrees.

- Decisions use `bounds()`, a **sound** interval analysis with padding for rounding, not the (possibly unsound)
  estimates. Where 26.1 itself uses an estimate (the `min`/`max` skip), the rules use the estimate.
- Simplifications drop evaluations, and dropped evaluations are only invisible when caching state is unobservable. So
  `optimize` returns stateful trees (`!ChunkAnalysis.isPure`) unchanged.

## Chunk evaluation

### `ChunkPlan`: shared, immutable setup

Built once per sampler and Y range:

1. `forChunk`: switches to the chunk form of leading-zero `mul` (above).
2. `intern`: structurally equal subtrees become one object. Equal markers must share state (spec §5.1), and from here
   on identity means "same marker".
3. Cell geometry: `cellWidth = 4 * size_horizontal`, `cellHeight = 4 * size_vertical`, the Y range rounded outwards
   to cells.
4. `ChunkAnalysis.impurity(root)`: picks the evaluator.
5. `interpolators` and `cellCaches`, in children-before-parent order of first appearance (spec §5.1). Neither
   `flat_cache` arguments nor `find_top_surface` densities are entered.

### `ChunkAnalysis`: is marker state observable?

The walk tracks, for every node, **where** it is evaluated (cell level, or corner level inside an `interpolated`
argument) and **how** it is read (batch or point, following spec §5.4's forwarding table). `flat_cache` arguments and
`find_top_surface` densities are single-point contexts and are not walked.

State is observable, so the tree needs the stateful evaluator, when:

| Construct | Why |
|---|---|
| `interpolated` or `cache_all_in_cell` at corner level | nesting (spec §4.4) |
| `cache_once` point-read at cell level | a cell fill has a single point instant, so every point gets the first point's value (§4.3) |
| `cache_2d` point-read with an argument that depends on y | it returns the previous read's value at the same `(x, z)`, whatever its y (§4.1) |

Everything else is pure: corner-level `cache_once` (each corner is its own instant), batch-read `cache_once` (recomputed
each cell), batch-read `cache_2d` (passes straight through), y-independent `cache_2d`, and a user `cache_all_in_cell`
at cell level (filled children first, so reads see the current cell).

Helpers used elsewhere:
- `isYIndependent`: conservative. Noise with `y_scale == 0` counts as independent, because `±0.0` give the same noise.
- `isCheap`: no noise or per-point work. `interpolated` and `flat_cache` reads count as cheap.

Every vanilla dimension is pure.

### `PureChunkEvaluator`: the fast path

When state is unobservable, every function inside a chunk is a pure function of the block position, given two
chunk-specific meanings:

- `interpolated` = trilinear interpolation of its argument's corner values, in cell-fill order (x, y, z; spec §5.3);
- `flat_cache` = its argument at `(4 * floor(x / 4), 0, 4 * floor(z / 4))`.

Every other marker is transparent. Purity means order doesn't matter, so the evaluator is free to skip, reorder and
reuse.

**Binding.** At construction, `Binder` maps the immutable tree onto per-thread objects, separately per context:

| Context | `interpolated` | `flat_cache` | `cache_2d` | Memo of shared subtrees |
|---|---|---|---|---|
| `CORNER` (interpolator arguments) | impossible (would be stateful) | `FlatGrid` | `ColumnMemo` if y-independent | `PointMemo` around `SharedSubexpressions.memoTargets` |
| `CELL` (per-point fallback at cell level) | `CellInterp` | `FlatGrid` | `ColumnMemo` if y-independent | none |
| `FLAT` (`flat_cache` arguments, only at y = 0 grid points) | transparent | `FlatGrid` (shared) | transparent | none |

`cache_once` and `cache_all_in_cell` are transparent in every context. `find_top_surface` densities are left
unbound (single points).

**Per chunk** (`fill`):

1. `prepareColumns`: for each of the `(n + 1)²` corner columns, take complete per-interpolator arrays from
   `ColumnCache` where available; otherwise point at this evaluator's own buffers.
2. For every cell (any order): run the compiled `CellOps` root over the cell's lanes, then `copyCell` into the output.
3. `publishColumns`: put every column array this chunk *completed* into `ColumnCache`, merged with the entry already
   there.

**Lazy corners.** An interpolator's corner is computed the first time a cell reads that interpolator
(`ensureCorners` → `ensureCorner`, stamped with the chunk generation). On the overworld, the big terrain
interpolator is needed in every cell, but the noodle-cave ones only in a minority, so most of their corners are never
computed. Only complete columns are shared, so a cache entry is never partial.

**`CellOps`.** The tree above the interpolators is compiled into ops that fill an array with one **lane** per block of
the cell. Lanes are ordered y, z, x, so x is contiguous like the output.

- `run(out, lanes, count)` computes the requested lanes (`lanes == null` means all).
- Ops with an entirely **cheap** subtree ignore the subset and fill every lane in tight loops. Expensive ops
  (`PerPoint`: noise, splines and so on via the `CELL` binding) compute only the requested lanes.
- `Gated` (general `mul`/`min`/`max`) collects the lanes where `b` is needed, skips `b` entirely when none are, and
  otherwise asks for only those lanes (or all lanes, if `b` is cheap).
- `Range` (`range_choice`) partitions lanes into in and out of range and runs each branch on its own lanes.
- `Interpolated` calls `interpolate`, which shares the partial lerps between lanes (with `b - a` computed once per
  pair), so every lane goes through exactly the operations of `DensityMath.trilinearXYZ`. A `cw == 4` path keeps a row
  in registers.

**Cell bounds.** Ops may report sound bounds for the current cell without evaluating it (`cellBounds`):

- `Interpolated`: the range of its 8 corners, widened by `1e-12 * magnitude + MIN_NORMAL`, since each lerp with a
  weight in `[0, 1)` stays between its endpoints up to a few units of rounding. No bounds for non-finite or huge
  (`>= 1e300`) corners.
- `Add`, `Affine` and the monotonic transforms (`abs`, `half_negative`, `quarter_negative`) map their input's bounds;
  rounding is monotonic, so this stays sound.
- `Range`: the chosen branch's bounds when its input decides it, otherwise the union of both.
- `Gated` `min`/`max`: derived from both inputs.

They are used in two places:
- `Range` decides the whole cell from its input's bounds and skips the input's lanes (and the unused branch);
- `Gated` `min`/`max` skip `b` when its bounds show it can't win: for example the overworld noodle, which is 64
  underground, against a density of at most about 0.46.

Any new op that reports bounds must be **sound for every lane**; when unsure, return `false`.

**Memos.**
- `PointMemo`: one entry keyed on `(x, y, z)`, for subtrees reached more than once per corner. On the overworld,
  `sloped_cheese` is referenced three times.
- `ColumnMemo`: one entry keyed on `(x, z)`.
- `FlatGrid`: 5×5 per chunk, stamped with the chunk generation.

All are correct only because the tree is pure.

### `StatefulChunkEvaluator`: the exact path

A direct replay of spec §5.2: fill the first corner slice; then for each cell column, fill the next slice, and for
each `cz` and each `cy` from the top down, **select** corners, **fill** the cell caches (children first, the implicit
outermost `cache_all_in_cell(add(final_density, beardifier))` last), and run the **block phase** (per-block
interpolator values in y-x-z order, then read the outermost cache).

Each function is bound to a `Live` node with:

- `point()`: a read at the current point (`px, py, pz`, in-cell offset `ox, oy, oz`, batch `index`);
- `batch(out)`: a read over the current batch (a corner column or a cell), forwarding to inputs per spec §5.4.
  The default is one point read per point, via `enterPoint(i)`.

Marker state follows spec §4:

| Live node | State |
|---|---|
| `Interpolator` | corner arrays for the chunk; selected corners; per-block value (reset to 0 each chunk) |
| `CellCache` | values of the most recent fill (reset to 0); at corners it reads offset `(0, 0, 0)` |
| `CacheOnce` | batch values plus batch instant; point value plus point instant |
| `Cache2D` | one `(x, z)` entry (reset each chunk) |
| `FlatCache` | 5×5 grid (reset each chunk) |

**Instants.** A batch instant begins per corner column and per cell fill. A point instant begins per corner (an
instant is `cornerBase + index`) and per block. A cell fill inherits the current point instant: the last block's, or
the last corner's right after a corner fill. Instants come from a counter that never repeats, so no stamp from an
earlier chunk can match, and every chunk behaves as if generated from scratch.

Subtrees with no chunk markers become `Stateless` (`node.sample`), since they have no state to replay.

## Y ranges and Cubic Chunks

`ChunkDensitySampler` accepts any requested Y window. With Cubic Chunks it may lie above, below or across the
dimension's noise range.

- **Pure trees:** only the requested window is evaluated. Clipping or extending doesn't change any value.
- **Stateful trees:** a block's value can depend on other cells in the column, and 26.1 only defines values over the
  dimension's own range (`dimensionMinY .. dimensionMaxY`). Blocks inside that range come from evaluating the whole
  dimension range; blocks outside it come from evaluating the requested window on its own.

Pass the dimension range through the 9-argument constructor, as `ModernWorldGenerator` does. The 6-argument
constructor treats the requested range as the dimension's.

`ColumnCache` keys columns by cell `(x, z)` only, so one cache belongs to one sampler (one Y range).

## Threads

`DensityNode` trees, `ChunkPlan` and `ColumnCache` are shared and thread-safe: the trees are immutable, and the
cache's entries are immutable once published through an `AtomicReferenceArray`. Each thread gets its own evaluator
from a `ThreadLocal`. `DensityPrefetcher` keeps a small pool of worker threads (`-Ddataback.densityThreads`).

## Testing

| Test | Checks |
|---|---|
| `VanillaParityTest` | every router field at 400 points per dimension and seed; whole chunks of real terrain (including far out, where coordinates wrap); every case in `cases/` as chunks and points, with and without the optimizer, after another chunk, in a clipped range and in a wider range; the exact evaluator on vanilla terrain |
| `ChunkEvaluatorsTest` | fast against exact evaluator on pure trees (lazy corners, bound skips, shared columns, other cell sizes, non-finite corners); spec §4.3 directly |
| `DensityFunctionsTest` | estimates, constant forms, chunk vs `sample` on marker-free trees, splines, optimizer rules, analysis, plan ordering, memos, build context |
| `DensityConcurrencyTest` | threads, prefetching and column sharing never change results |

Run them with `./gradlew test --tests 'databack.common.worldgen.*'`.

### Settling a behavioural question

Don't guess, and don't consult Minecraft code:

1. Add `src/test/resources/vanilla_reference/cases/<name>.json` (`description`, `seed`, `noise`, `chunks`,
   `density`). Make the two possible behaviours give clearly different values (NaN versus a number works well), and
   make the result depend on the seed (`caseDependsOnSeed` checks that).
2. Run `misc/vanilla-reference/run.sh` unchanged. It regenerates everything; existing files should come out
   byte-identical.
3. Implement, run the parity tests, and record the finding in `density-functions.md`.

To check that a case actually discriminates, temporarily implement the other behaviour and watch the case fail.

## Benchmarking

```
./gradlew jmh --rerun -Pjmh.includes=ChunkDensity -Pjmh.params='mode=shared' \
  -Pjmh.jvm=/usr/lib/jvm/java-25-openjdk/bin/java -Pjmh.profilers=perfnorm
```

- Read `cycles:u`. On a busy desktop it drifts ±5% between runs; `instructions:u` stays stable.
- `-Pjmh.profilers=gc` reports allocation (about 5.6 KB per chunk); for a profile, use async-profiler with
  `output=collapsed`.
- Modes, each adding to the previous one: `vanilla` (the exact evaluator, full height), `fastpath`, `optimized`,
  `clipped`, `shared`.
- Without `--rerun`, Gradle considers the task up to date and doesn't run it.

## Adding or changing a function type

1. **DTO** in `BuiltinDensityFunctions`: register the variant and build children through `ctx.build`.
2. **Node**: a `DensityNode` subclass with `sample`, the estimate from the spec, `inputs`/`withInputs` (keep the
   estimate), `withBounds` and `sameParams`; plus a constructor in `Density`.
3. **`ChunkAnalysis`**: how its inputs are read (batch or point), whether it depends on y, whether it is cheap.
4. **`StatefulChunkEvaluator.Binder`**: a `Live` node with spec §5.4 forwarding (otherwise binding throws for trees
   with markers below it).
5. **`PureChunkEvaluator`**: nothing, if per-point evaluation is acceptable (it falls back to `PerPoint`). Add a
   `CellOps` op only if the type is hot at cell level.
6. **`DensityOptimizer.computeBounds`**: a sound interval, or leave it unbounded.
7. **Tests**: a unit test, a harness case if any behaviour is unclear, and a `ChunkEvaluatorsTest` tree if it reaches
   the fast path.

## Pitfalls

- **Evaluation order is part of the result.** Write formulas exactly as the spec does: no reassociation, no FMA, no
  "equivalent" algebra (`x / 24.0` is not `x * (1 / 24.0)`).
- **Estimates must survive every rewrite** (see the invariant above).
- **Only pure trees may be reordered, memoized or cached.** If a change can drop or repeat an evaluation, guard it
  with `ChunkAnalysis.isPure`.
- **Standalone and chunk evaluation differ** for leading-zero `mul`: use `ChunkPlan.root`, not the router's tree,
  inside chunk code.
- **Bounds-based skips** must be sound for every lane, including NaN, infinities and subnormal values.
- **No allocation per cell or block:** ops own their scratch arrays, and only finished columns are copied for the
  cache.
- **Jabel:** main sources target Java 8. No records, no sealed types, no pattern-matching `switch`, no Java 9+ APIs.
  `instanceof` patterns are fine.
