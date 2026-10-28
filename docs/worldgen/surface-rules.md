# Surface rules: behaviour specification

This document specifies how Databack applies a noise setting's `surface_rule`: after terrain density and fluids, the
rule replaces default blocks (stone) with surface materials. The target is Minecraft 26.1.

It describes behaviour, and is written from the datapack format and black-box observation of the real 26.1 jar
through the reference harness (`misc/vanilla-reference`, `run.sh surface`). Where this document and the harness
disagree, the harness wins.

The goal is terrain **close to** vanilla's, not block-exact: where vanilla uses seeded randomness that black-box data
cannot reveal, Databack uses its own (marked **Approximation**). Everything else is exact against the dumps.

## 1. Inputs and terms

- **Open** blocks are air and fluid; **solid** blocks are everything else. Only default blocks are given to the rule;
  if the rule produces nothing, the default block stays.
- A column is evaluated from the top of the generated range down.
- Anchors: `absolute v` is `v`; `above_bottom v` is `minY + v`; `below_top v` is `minY + height - 1 - v`, where
  `minY` and `height` are the noise setting's (nether bedrock roof dumps; `vertical_gradient_bottom`).
- Noises named by conditions are the datapack noises, seeded like density function noises, and sampled in 2D at
  `(x, 0, z)` (`noise_threshold_surface`: all 341,667 blocks).

### 1.1 Floor and ceiling stone depth

- **Floor depth** `d` of a solid block: 1 for the topmost solid block under an open block, increasing downward. Air
  resets it, and so does fluid that has an open block directly above it (an ocean). Fluid directly under a solid block
  (a flooded cave), and fluid below such fluid, neither resets nor counts. (`stone_depth_floor`: all 341,667 blocks.)
- **Ceiling depth**: the same counted upward from the open block below; every open block resets it.
  (`stone_depth_ceiling`: exact.)

### 1.2 Per-column values

- **Surface depth** `s = floor(2.75 * surface + 3 + r * 0.25)`, `surface` being the `minecraft:surface` noise and `r` in
  `[0, 1)`. Fitted from 2,560 columns (`stone_depth_floor_surface`). **Approximation:** vanilla's `r` is a seeded value
  black-box data cannot reveal; Databack uses its own per-column random.
- **Secondary depth** for a range `R`: `floor((secondary + 1) / 2 * R)`, `secondary` being the
  `minecraft:surface_secondary` noise (`stone_depth_floor_secondary`: exact, boundaries at thirds for `R = 6`).
- **Fluid level** `W`: one above the top of the first fluid block found going down the column (any fluid, open or
  flooded cave); none if the column has no fluid above the block. (`water_*`: all blocks of three ladders. Equivalent
  on the dumps: taking the first fluid since the floor depth last reset.)
- **Preliminary surface** `Q`: `floor` of the router's `preliminary_surface_level`, sampled at the corner of the column's
  4x4-column cell (`x & ~3`, `z & ~3`); vanilla's value is constant per cell (`*.columns.tsv`: 320/320 cells;
  `PreliminarySurfaceTest`: every column of both seeds).

## 2. Rules

| Rule | Result |
|---|---|
| `block` | `result_state`. |
| `sequence` | The first child that produces a block. |
| `condition` | `then_run` if `if_true` holds, otherwise nothing. |
| `bandlands` | A terracotta stripe by height (2.1). |

### 2.1 Bandlands

Vanilla uses one stripe pattern indexed by height, shifted per column by a small amount (0 or -1 between the dumped
chunks), presumably from the `minecraft:clay_bands_offset` noise. The pattern is plain terracotta with mostly
1-block coloured stripes (orange, brown, yellow, light grey, white, red; occasional runs of 2-4), with no repeat within
157 blocks. **Approximation:** Databack generates its own seeded pattern with that character and shifts it by the
offset noise.

## 3. Conditions

`d` is the block's floor depth, `s` the column's surface depth.

| Condition | Holds when | Evidence |
|---|---|---|
| `biome` | The block's biome (zoomed, at the block itself) is listed. | `biome_lookup`: 5,575/5,575 near-surface blocks |
| `noise_threshold` | `min_threshold <= noise(x, 0, z) <= max_threshold`. | exact |
| `vertical_gradient` | True below `true_at_and_below`, false at and above `false_at_and_above`; between them, true with probability `(falseY - y) / (falseY - trueY)`. **Approximation:** Databack's own per-block random. | linear in both dumps |
| `y_above` | `y + (add_stone_depth ? d : 0) >= anchor + surface_depth_multiplier * s`. | exact, three ladders |
| `water` | No fluid above, or `y + (add_stone_depth ? d : 0) >= W + offset + surface_depth_multiplier * s`. | exact, three ladders |
| `stone_depth` | `depth <= 1 + offset + (add_surface_depth ? s : 0) + secondary(secondary_depth_range)`, with the floor or ceiling depth per `surface_type`. | exact |
| `temperature` | The block's biome temperature is below 0.15. **Approximation:** height cooling and the frozen temperature modifier are not modelled. | consistent with all dumped biomes |
| `steep` | `H(x, z + 1) - H(x, z - 1) >= 4` or `H(x - 1, z) - H(x + 1, z) >= 4`, `H` being the top non-air block of a column; neighbours outside the chunk are replaced by the column itself. | `steep`: 2,560/2,560 columns |
| `hole` | `s <= 0`. | none in the dumps (rare) |
| `above_preliminary_surface` | `y >= Q + s - 8`. **Approximation:** exact in 57% of columns, most errors within a few blocks; the true threshold is not established. | `above_preliminary_surface` |
| `not` | The inner condition does not hold. | — |

## 4. Fluids without aquifers

Until aquifers exist, open blocks below `sea_level` are the default fluid; in the overworld, open blocks at `y <= -55`
are lava instead (`chunk_overworld_no_aquifers_*_noise`; whether that height is absolute or relative to `minY` is not
established). Databack only generates y 0 to 255 so far.

## 5. Reference data

`src/test/resources/vanilla_reference/surface/`, from `misc/vanilla-reference/run.sh surface`:

- `chunk_<config>_<seed>_<cx>_<cz>_{noise,surface}.bin.gz`: real chunks after terrain/fluids and after surface rules.
- `case_<name>_<cx>_<cz>.bin.gz`: synthetic rules from `cases/<name>.json`, each exposing one input through a ladder of
  conditions placing a different wool.
- `*.columns.tsv`: the preliminary surface level vanilla reports per column.

Tests: exact checks for the exact parts; tolerance checks for surface depth (within one block, mostly exact);
statistical checks (agreement rate, same block set) for whole chunks.
