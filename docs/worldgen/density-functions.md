# Density functions: behaviour specification

This document specifies how Databack evaluates `worldgen/density_function` trees and how it turns a dimension's
`final_density` into per-block terrain density. The target is Minecraft 26.1: for any datapack, the values must be
bit-for-bit what 26.1 produces (the sign of a zero may differ).

It describes **behaviour**, not an implementation. It is written from the datapack format, observed results, and the
reference harness (`misc/vanilla-reference`), which runs the real 26.1 jar as a black box. The harness output in
`src/test/resources/vanilla_reference` is the authority: where this document and the harness disagree, the harness
wins and this document is wrong. `VanillaParityTest` checks the implementation against it.

Noise generation (`ImprovedNoise`, `PerlinNoise`, `NormalNoise`, `SimplexNoise`), `old_blended_noise` and
`end_islands` are out of this document's scope and are kept as they are.

## 1. Conventions

All values are IEEE-754 `double`s unless stated otherwise; splines are evaluated in `float` (section 3.9).
Exactness depends on the order of operations, so formulas are given in evaluation order and must be computed as
written (no reassociation, no fused multiply-add, no algebraic simplification that changes rounding):

- `lerp(t, a, b) = a + t * (b - a)`
- `clamp(v, lo, hi)` = `lo` if `v < lo`, otherwise `min(v, hi)`, where `min`/`max` return `NaN` if either operand
  is `NaN` (so `clamp(NaN, lo, hi)` is `NaN`).
- `clampedLerp(t, a, b)` = `a` if `t < 0`, `b` if `t > 1`, otherwise `lerp(t, a, b)`.
- `clampedMap(v, fromLo, fromHi, toLo, toHi) = clampedLerp((v - fromLo) / (fromHi - fromLo), toLo, toHi)`
- `floor(v)` rounds towards negative infinity and converts to `int`.
- Block coordinates `x`, `y`, `z` are `int`s converted to `double` where they enter arithmetic.

Every function has a **bound estimate** `[min, max]`. The estimates are part of the observable behaviour: `min` and
`max` (3.5) use them to skip work, which changes results when the estimate is wrong (and some are, see 3.5). Bounds are
computed once, when the tree is built, from the children's estimates.

## 2. Evaluation contexts

A function is evaluated either at a **single point** (block position only) or inside a **chunk**, during terrain
generation. At a single point, every marker (`interpolated`, `flat_cache`, `cache_2d`, `cache_once`,
`cache_all_in_cell`) is transparent: it returns its argument's value at that point. Inside a chunk, the markers take on
the behaviour in section 4.

Everything evaluated as part of a chunk, including the single-point evaluations inside it (`flat_cache` arguments,
3.8's `find_top_surface` scan), uses the chunk's rules. The one function whose value differs between the two contexts
apart from the markers is `mul` with a leading constant 0 (3.4).

Every marker's bound estimate is its argument's (cases `marker_bounds_*`: with an unsound argument estimate, the
`max` short circuit follows the argument's estimate for all five markers).

`router` fields other than `final_density` (aquifers, ore veins, climate) are not part of this document yet.

## 3. Function types

Unless noted, a function evaluates its arguments at the same point and has no state.

### 3.1 Constants and gradients

- **constant** (a bare number in JSON): its value. Bounds `[v, v]`.
- **y_clamped_gradient** `from_y`, `to_y`, `from_value`, `to_value`:
  `clampedMap(y, from_y, to_y, from_value, to_value)`. Bounds `[min(from_value, to_value), max(...)]`.

### 3.2 Blending placeholders

Databack has no chunk blending or structure terrain adaptation, so these behave as 26.1 does with no blending data
and no structures:

- **blend_alpha**: `1.0`. **blend_offset**: `0.0`. Bounds equal to the value. These are not constants for 3.4:
  `mul(blend_offset, x)` is the general form, so it is `0.0` without evaluating `x` (case `blend_offset_constant`).
- **blend_density** `argument`: the argument's value. Bounds `[-inf, +inf]`.
- **beardifier** (not a datapack type; see 5.1): `0.0`.

### 3.3 Single-input transforms

For input value `v`:

| type | value |
|---|---|
| `abs` | `abs(v)` |
| `square` | `v * v` |
| `cube` | `v * v * v` |
| `half_negative` | `v` if `v > 0`, else `v * 0.5` |
| `quarter_negative` | `v` if `v > 0`, else `v * 0.25` |
| `invert` | `1.0 / v` |
| `squeeze` | with `c = clamp(v, -1, 1)`: `c / 2.0 - c * c * c / 24.0` |

Bounds, from the input bounds `[lo, hi]` and `f` the transform above:
- `invert`: `[-inf, +inf]` if `lo < 0 < hi`, otherwise `[f(hi), f(lo)]`.
- `abs`, `square`: `[max(0, lo), max(f(lo), f(hi))]`. The lower bound is `max(0, lo)` itself, not `f` of anything:
  `square` over `[2, 3]` has bounds `[2, 9]`.
- all others: `[f(lo), f(hi)]`.

**clamp** `input`, `min`, `max`: `clamp(v, min, max)`. Bounds `[min, max]` (from the fields, not the input).

### 3.4 add and mul

For `add`/`mul` with arguments `a`, `b` and values `va`, `vb`:

- If either argument is a constant `c` (a bare number or `minecraft:constant`, which behave alike: cases
  `constant_type`, `constant_bare_zero`), the other argument `in` is the input: `add` is `vin + c` and `mul` is
  `vin * c`. There is no short circuit, with one exception: a `mul` whose **first** argument is the constant `0`,
  evaluated at a single point (section 2, outside any chunk), is `0.0` without evaluating the input, like the general
  form. Inside a chunk it is `vin * 0`, which is NaN for an infinite input (cases `constant_bare_zero`,
  `constant_bare_zero_point_read`, `constant_bare_zero_interpolated`, `constant_bare_zero_flat_cache`,
  `constant_bare_zero_find_top_surface`). A trailing constant 0 is `vin * 0` everywhere
  (case `constant_bare_zero_second`). Bounds: `add` → `[lo + c, hi + c]`; `mul` → `[lo * c, hi * c]` if `c >= 0`,
  otherwise `[hi * c, lo * c]`.
- Otherwise `add` is `va + vb`, and `mul` is `0.0` if `va == 0.0` (then `b` is **not** evaluated, and the result is
  `+0.0` even when `va` is `-0.0`), else `va * vb`.

Bounds for the non-constant forms, from `[lo1, hi1]` and `[lo2, hi2]`:
- `add`: `[lo1 + lo2, hi1 + hi2]`.
- `mul`: if `lo1 > 0 && lo2 > 0`: `[lo1 * lo2, hi1 * hi2]`; else if `hi1 < 0 && hi2 < 0`: `[hi1 * hi2, lo1 * lo2]`;
  else `[min(lo1 * hi2, hi1 * lo2), max(lo1 * lo2, hi1 * hi2)]`. (This is not a sound bound when one interval is
  positive and the other negative; it must be reproduced anyway.)

### 3.5 min and max

- **min**: evaluate `va`. If `va < b.min` (b's bound estimate), the result is `va` and `b` is not evaluated. Otherwise
  `min(va, vb)`.
- **max**: evaluate `va`. If `va > b.max`, the result is `va` and `b` is not evaluated. Otherwise `max(va, vb)`.

Bounds: `min` → `[min(lo1, lo2), min(hi1, hi2)]`; `max` → `[max(lo1, lo2), max(hi1, hi2)]`.

When `b`'s estimate is unsound, the skip returns `va` even though `vb` would have been smaller (or larger); this is
the 26.1 behaviour and must be kept.

### 3.6 range_choice

`input`, `min_inclusive`, `max_exclusive`, `when_in_range`, `when_out_of_range`: evaluate the input `v`; if
`min_inclusive <= v < max_exclusive`, the result is `when_in_range`, else `when_out_of_range`. Only the chosen
branch is evaluated. (`NaN` is out of range.) Bounds: the union of both branches' bounds.

### 3.7 Noise sampling

`noise` refers to a `worldgen/noise` entry, instantiated per world seed (instantiation is part of the noise code).
`|noise|`'s bound `N` comes from the noise implementation.

- **noise** `noise`, `xz_scale`, `y_scale`: the noise at `(x * xz_scale, y * y_scale, z * xz_scale)`. Bounds `[-N, N]`.
- **shift_a** `argument` (a noise): `4.0 * noise(x * 0.25, 0.0, z * 0.25)`.
- **shift_b** `argument`: `4.0 * noise(z * 0.25, x * 0.25, 0.0)` (note the swapped axes).
- **shift** `argument`: `4.0 * noise(x * 0.25, y * 0.25, z * 0.25)`.
  All three have bounds `[-4N, 4N]`. (The multiplications are `coordinate * 0.25` before sampling and
  `sample * 4.0` after.)
- **shifted_noise** `noise`, `xz_scale`, `y_scale`, `shift_x`, `shift_y`, `shift_z`: the noise at
  `(x * xz_scale + sx, y * y_scale + sy, z * xz_scale + sz)`, where `sx`, `sy`, `sz` are the three shift functions'
  values, evaluated in that order. Bounds `[-N, N]`.
- **weird_scaled_sampler** `input`, `noise`, `rarity_value_mapper`: evaluate the input `v` and map it to a rarity
  `r`, then the result is `r * abs(noise(x / r, y / r, z / r))`.
  - `type_1`: `r = 0.75` if `v < -0.5`, `1.0` if `v < 0`, `1.5` if `v < 0.5`, else `2.0`. Maximum rarity `2.0`.
  - `type_2`: `r = 0.5` if `v < -0.75`, `0.75` if `v < -0.5`, `1.0` if `v < 0.5`, `2.0` if `v < 0.75`, else `3.0`.
    Maximum rarity `3.0`.
  - Bounds `[0, maxRarity * N]`.
- **old_blended_noise**, **end_islands**: kept as implemented (see the introduction).

### 3.8 find_top_surface

`density`, `upper_bound`, `lower_bound` (int), `cell_height` (int):

1. `top = floor(upper_bound / cell_height) * cell_height`, with `upper_bound` evaluated at the point.
2. If `top <= lower_bound`, the result is `lower_bound`.
3. Otherwise, for `y' = top, top - cell_height, ...` while `y' >= lower_bound`: evaluate `density` **at the single
   point** `(x, y', z)` (markers transparent, regardless of context). The first `y'` with density `> 0` is the
   result.
4. If none is found, `lower_bound`.

Bounds `[lower_bound, max(lower_bound, upper_bound.max)]`.

### 3.9 spline

A spline is either a constant (a number) or a multipoint spline: a `coordinate` function and a list of points, each
with a `location`, a `value` (a nested spline) and a `derivative`. Locations, values and derivatives are `float`s,
and all spline arithmetic is `float`; the density function's result is the spline's `float` value widened to `double`.

Evaluating a multipoint spline at a point:
1. `t0 = (float) coordinate`.
2. Find `i`, the index of the last point with `location <= t0`, by a search equivalent to: the number of points whose
   location is `<= t0`, minus one. (For `NaN` this is the last index.)
3. If `i < 0`: the result is `extend(0)`; if `i` is the last index: `extend(last)`, where
   `extend(k) = value_k` if `derivative_k == 0`, else `value_k + derivative_k * (t0 - location_k)`.
   `value_k` is the nested spline evaluated at the same point.
4. Otherwise, with `x1, x2` the locations of points `i` and `i + 1`, `y1, y2` their values, `d1, d2` their
   derivatives:
   - `t = (t0 - x1) / (x2 - x1)`
   - `a = d1 * (x2 - x1) - (y2 - y1)`, `b = -d2 * (x2 - x1) + (y2 - y1)`
   - result `= lerp(t, y1, y2) + t * (1 - t) * lerp(t, a, b)` (float `lerp`, `1 - t` as `1.0f - t`).

Only the values that are needed are evaluated: `extend` evaluates one point's value, step 4 evaluates two, each once,
`y1` before `y2`.

Bound estimate (all `float`), with `[cLo, cHi]` the coordinate's bounds cast to `float` and `[vlo_k, vhi_k]` each
point's value bounds:
1. Start from `min = +inf`, `max = -inf`.
2. If `cLo < location_0`: include `extend'(0, cLo, vlo_0)` and `extend'(0, cLo, vhi_0)`, where
   `extend'(k, c, v) = v` if `derivative_k == 0`, else `v + derivative_k * (c - location_k)`.
3. If `cHi > location_last`: likewise with `cHi` and the last point.
4. Include every point's `[vlo_k, vhi_k]`.
5. For each pair of neighbouring points `k, k + 1` where at least one derivative is non-zero, with
   `p1 = d_k * (x_{k+1} - x_k)` and `p2 = d_{k+1} * (x_{k+1} - x_k)`:
   - `minA = p1 - vhi_{k+1} + vlo_k`, `maxA = p1 - vlo_{k+1} + vhi_k`
   - `minB = -p2 + vlo_{k+1} - vhi_k`, `maxB = -p2 + vhi_{k+1} - vlo_k`
   - include `min(vlo_k, vlo_{k+1}) + 0.25 * min(minA, minB)` and `max(vhi_k, vhi_{k+1}) + 0.25 * max(maxA, maxB)`.

A constant spline's bounds are its value.

### 3.10 interval_select

A 26.2 type; not part of 26.1. Datapacks that use it are outside the target and should get a warning.

## 4. Markers inside a chunk

Inside a chunk the five markers have state. For trees where no `interpolated` or `cache_all_in_cell` contains another
one, and with the exceptions called out in 4.3 and 4.4, the state is unobservable and the markers behave as described
in **4.1**. Section **5** gives the exact evaluation order, which determines what the state-dependent cases observe.

### 4.1 Values

- **interpolated**: the argument is evaluated only at cell corners; every block gets the trilinear interpolation of
  its cell's eight corner values (5.3).
- **flat_cache**: per chunk, the argument is evaluated once per point of a grid with 4-block spacing, at the single
  points `(4i, 0, 4j)` in world coordinates (y = 0, markers transparent). The grid starts at
  `(floor(chunkMinX / 4), floor(chunkMinZ / 4))` and has `16/4 + 1 = 5` points per axis, covering the chunk plus
  one extra row and column on the positive side. Every read at a position whose `(floor(x/4), floor(z/4))` lies in that grid
  returns the grid value, whatever its `y` and its offset within the 4×4 column. A read outside the grid evaluates the
  argument directly at the actual position. (For a 3D argument this differs from evaluating it per block; that's
  intended.)
- **cache_2d**: a one-entry cache keyed on `(x, z)`. A read at the same `(x, z)` as the previous read returns the
  previous value, even if `y` differs. Batch reads (5.4) pass straight through and neither use nor update the
  entry. The entry starts empty in every chunk.
- **cache_once**, **cache_all_in_cell**: no change in value for ordinary trees (but see 4.3 and 4.4).

### 4.2 The final density

Terrain density at a block is `final_density + beardifier`, read through an outermost `cache_all_in_cell` (5.1).

### 4.3 cache_once

`cache_once` remembers values for an **instant**. There are two kinds of instant, each identified by when it began:

- A **point instant** begins at every corner during corner filling, and at every block in the block phase. No point
  instant begins during a cell fill: all of its points share the instant that was current when it started (the
  previous block's, or for the first cell after a corner fill, the last corner's).
- A **batch instant** begins once per corner column during corner filling (shared by every interpolator's fill of
  that column) and once per cell fill.

Behaviour:
- A batch read: if this `cache_once` stored a batch during the current batch instant, it returns a copy of it.
  Otherwise it evaluates its argument as a batch, stores it, and returns it.
- A point read: if this `cache_once` stored a batch during the current batch instant, it returns the stored batch
  value for the point's index within the batch. Otherwise, if it stored a point value during the current point
  instant, it returns that value. Otherwise it evaluates its argument at the point and stores the value.
- At a single point (outside a chunk), it is transparent.

Consequence (verified by case `cache_once_per_point`): when a `cache_once` is reached only point by point during a
cell fill (for instance as the second argument of `min`), every point of the cell that reaches it gets the same value:
the one computed for the first such point, unless it already holds a value for the instant the fill inherited.

### 4.4 Nested interpolators and cell caches ("vanilla mode")

When an `interpolated` or `cache_all_in_cell` contains another `interpolated` or `cache_all_in_cell`, the inner one is
read at times when its state belongs to a different position. These rules apply to all trees, but only nested trees
can observe them:

- **interpolated**, read during a cell fill (point or batch): the trilinear interpolation of the current cell's corners
  at the point's in-cell offset (5.3, x-y-z order).
- **interpolated**, read at any other time inside the chunk (corner filling, or the block phase):
  - a point read returns the interpolator's **per-block value** (5.3, y-x-z order) for the most recent block of the
    block phase: `0.0` before the chunk's first block;
  - a batch read evaluates the argument directly, as a batch, over the requested points (at a corner column, that's
    the corners themselves).
- **cache_all_in_cell**, point read inside the chunk: if the point's in-cell offset lies within a cell, the value
  stored for that offset during the **most recent** cell fill (all `0.0` before the chunk's first cell fill);
  otherwise its argument evaluated at the point. During corner filling the in-cell offset is `(0, 0, 0)`, so a nested
  cell cache read while filling corners returns the most recently filled cell's value at its lowest corner block.
- **cache_all_in_cell**, batch read: one point read per point (so the rule above applies to each).

Batch reads of `flat_cache` are likewise one point read per point.

Every chunk starts from the initial state (per-block values `0.0`, cell caches all `0.0`, `cache_2d` empty), regardless
of what was generated before. Cases `nested_interpolated` and `nested_cell_caches` pin this down, including a chunk
generated after its neighbour.

## 5. Chunk evaluation

### 5.1 Setup

- Cell size: `cellWidth = 4 * size_horizontal`, `cellHeight = 4 * size_vertical` (from `noise_settings.noise`).
- Cells are aligned to the world grid: cell `(cx, cy, cz)` covers blocks `cx * cellWidth ..`, etc. The Y range evaluated
  is a whole number of cells starting at the cell containing `min_y`. When marker state is unobservable (no nesting,
  and none of the state-dependent reads of 4.1 and 4.3), evaluating a smaller or larger Y range than the dimension's
  (as the 1.7 world does with `0..255`, or Cubic Chunks with any range) gives the same values for the blocks it
  covers, as long as the cells stay aligned. When state is observable it does not: a block's value can depend on
  cells elsewhere in the column (by 4.4, a nested `cache_all_in_cell` read while filling corners returns the most
  recently filled cell's value, and cells are filled top to bottom). For case `nested_cell_caches`, dropping one cell
  at each end of the range changes 5376 of the 12288 remaining blocks. Such trees have to be evaluated over the
  dimension's own range; outside it, 26.1 defines no values.
- The tree is `cache_all_in_cell(add(final_density, beardifier))`. Identical subtrees (by structure) share one marker
  instance, so state is shared between them.
- **Interpolators** are ordered by first appearance in a children-before-parent traversal (`argument1` before
  `argument2`; a marker after its argument). **Cell caches** likewise, so the outermost cell cache is last.

### 5.2 Order

For a chunk with `n = 16 / cellWidth` cells per horizontal axis and `m` cells vertically:

1. Fill the corner columns of the first x slice.
2. For each cell column `i = 0 .. n-1` (increasing x):
   1. Fill the corner columns of slice `i + 1`.
   2. For each `cz = 0 .. n-1`, and for each `cy` from `m-1` down to `0`:
      1. **Select** the cell: each interpolator takes its eight corners from slices `i` (low x) and `i + 1` (high x).
      2. **Cell fill**: batch-fill each cell cache, in order, over the cell's points (5.4).
      3. **Block phase**: for each in-cell `y` from `cellHeight - 1` down to `0`, then each in-cell `x` from `0`,
         then each in-cell `z` from `0`: update every interpolator's per-block value (5.3) and read the block's
         density from the outermost cell cache.

**Filling the corner columns of a slice** (a slice is one corner x position): for each corner `z` index
`0 .. n` (inclusive), for each interpolator in order, batch-evaluate its argument over the column's `m + 1` corners,
bottom to top (one batch instant for the column; one point instant per corner).

### 5.3 Interpolation

For a block at in-cell offset `(dx, dy, dz)`: `fx = dx / cellWidth`, `fy = dy / cellHeight`, `fz = dz / cellWidth`
(each a `double` division). `cXYZ` is the corner value with `X`, `Y`, `Z` in `{0, 1}` (`1` = the high corner on that
axis).

- **Cell-fill order (x, then y, then z)**, used for reads during a cell fill:
  `lerp(fz, lerp(fy, lerp(fx, c000, c100), lerp(fx, c010, c110)),
             lerp(fy, lerp(fx, c001, c101), lerp(fx, c011, c111)))`
- **Per-block order (y, then x, then z)**, the per-block value:
  `lerp(fz, lerp(fx, lerp(fy, c000, c010), lerp(fy, c100, c110)),
             lerp(fx, lerp(fy, c001, c011), lerp(fy, c101, c111)))`

The two orders round differently. Terrain itself always uses the cell-fill order (the final density goes through a
cell cache); the per-block order is only observable through 4.4.

### 5.4 Batch and point reads

A **batch** read asks for a function's values at a sequence of points: a corner column (bottom to top), or a cell
(in-cell `y` from top to bottom, then `x`, then `z`). Each function forwards batch reads to its arguments as follows;
"per point" means the argument gets one point read per point, in order, only where needed.

| function | arguments |
|---|---|
| `add` (no constant) | batch `argument1`, then batch `argument2` |
| `mul`, `min`, `max` (no constant) | batch `argument1`; `argument2` per point (3.4, 3.5 short circuits) |
| `add`/`mul` with a constant, the 3.3 transforms, `clamp`, `blend_density` | batch the input |
| `range_choice` | batch the input; the chosen branch per point |
| `weird_scaled_sampler` | batch the input; noise per point |
| markers | see 4 |
| everything else | per point (its own arguments get point reads) |

This only matters where state is observable (4.3, 4.4). Implementations may batch anything else however they like,
and may evaluate pure functions speculatively, as long as values are identical.

## 6. Verification

`misc/vanilla-reference/run.sh` regenerates the reference data from the 26.1 jar:

- `points_<dimension>_<seed>.tsv`: every router field at 400 points, single-point evaluation, for the overworld,
  amplified, nether and end with two seeds.
- `chunk_<dimension>_<seed>_<x>_<z>.bin.gz`: terrain density for whole chunks, including far-out positions (block
  coordinates up to about 30 million) where noise coordinates wrap.
- `cases/*.json`: synthetic trees, one per behaviour (each file has a `description`). The harness uses each as the
  `final_density` of an otherwise empty router and writes `case_<name>_<x>_<z>.bin.gz` per listed chunk plus
  `case_<name>_points.tsv`. `cases_index.tsv` lists them.

To pin down a new behaviour, add a case file, rerun the harness, and add or update the matching section here.
