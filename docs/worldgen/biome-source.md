# Multi-noise biome source: behaviour specification

This document specifies how Databack picks biomes for a `minecraft:multi_noise` biome source: which biome a quart
cell gets, and which quart cell a block reads its biome from. The target is Minecraft 26.1.

Like [`density-functions.md`](density-functions.md), it describes **behaviour**, not an implementation, and it is
written from the datapack format and from black-box observation of the real 26.1 jar: the reference harness
(`misc/vanilla-reference`, `run.sh biome`) and small probes against the jar's public API. Where this document and
the harness disagree, the harness wins. Each rule below names the data that establishes it.

Not covered: the other biome source types (`fixed`, `checkerboard`, `the_end`), spawn point search, and how modern
biomes map onto 1.7 biomes.

## 1. Conventions

**Quantization.** Every climate value is stored as a `long`: `quantize(v) = (long) (v * 10000.0F)`, where `v` is a
`float` and the multiplication is done in `float`. The cast truncates towards zero, so `-0.78` becomes `-7799`, not
`-7800`. (Probe: 2,000,000 floats, no exception; `preset_overworld.tsv` contains `-7799`.)

**Parameters.** A climate point has six parameters, always in this order (indices as in `ClimateEntry`):

| # | Parameter | Noise router field |
|---|---|---|
| 0 | temperature | `temperature` |
| 1 | humidity | `vegetation` |
| 2 | continentalness | `continents` |
| 3 | erosion | `erosion` |
| 4 | depth | `depth` |
| 5 | weirdness | `ridges` |

**Quart cells.** Biomes are stored per quart cell: a 4×4×4 block of blocks. Quart cell `(qx, qy, qz)` holds blocks
`4q .. 4q + 3` on each axis.

## 2. Parameter lists

A multi-noise biome source holds an ordered list of **entries**. Each entry is a biome id, one quantized range
`[min, max]` per parameter, and a quantized `offset`. **List order is behaviour**: it breaks ties (section 4).

**Inline lists** (`"biomes": [...]`): each element is `{"biome": <id>, "parameters": {...}}`. Each of the six
parameters is either a number `v` (the range `[quantize(v), quantize(v)]`) or a pair `[a, b]` (the range
`[quantize(a), quantize(b)]`); `offset` is a number, quantized the same way. Decoding the harness's JSON form of both
presets through vanilla's own codec reproduced every entry exactly, which confirms this quantization for JSON values.

A range with `min > max` is rejected by vanilla. **Proposed:** Databack logs an error naming the pack and entry, and
drops the entry.

**Presets** (`"preset": <id>`, via a `worldgen/multi_noise_biome_source_parameter_list` that names
`minecraft:overworld` or `minecraft:nether`): vanilla defines these lists in code. Databack builds them at runtime from
the vanilla classes the datapack download fetches (`VanillaPresetExtractor`, `VanillaBiomePresets`); nothing of the
table is shipped. Observed shape (`preset_*.tsv`, a gitignored harness dump):

- `minecraft:overworld`: 7,593 entries, every offset 0. 3,795 surface entries, each appearing twice in a row (depth
  `0.0`, then depth `1.0`, otherwise identical), followed by 3 cave entries: `dripstone_caves`, `lush_caves`
  (depth `0.2 .. 0.9`) and `deep_dark` (depth `1.1`).
- `minecraft:nether`: 5 entries, every parameter a single point at depth 0, offsets 0 except `warped_forest` (0.375)
  and `basalt_deltas` (0.175).

An unknown preset id, or a preset that cannot be built (e.g. the download failed), logs a warning; the source then
has no entries and generates no biomes.

## 3. Climate sampling

The **target point** for quart cell `(qx, qy, qz)` is, for each parameter `i`:

```
target[i] = quantize((float) field_i.sample(4 * qx, 4 * qy, 4 * qz))
```

where `field_i` is the noise router field from section 1, evaluated at a single point (every marker transparent, as in
`density-functions.md` section 2). Notes:

- The sample is at the cell's **minimum corner** block, not its centre. (Probe: 3,000 points per dimension, all six
  values match at `4q`; none match at `4q + 2`.)
- The value is **narrowed to `float` before quantizing**. Quantizing the `double` directly gives a different result
  for about 0.4% of points (12 of 3,000 overworld, 1 of 3,000 nether).
- Target values are not clamped and can lie well outside `-1 .. 1` (e.g. depth `-1.95` deep underground).
- There is **no Y clamping**: quart cells far outside the dimension's height range are sampled like any other.
  (Probe: `qy` in `-1000 .. 1000`; `climate_*.tsv` includes `qy` up to 32 cells outside the range.) Databack relies
  on this for Cubic Chunks.

The rule is the same for every dimension and noise settings (`overworld`, `large_biomes`, `amplified`, `nether`);
only the router differs. (`climate_<dim>_<seed>.tsv`.)

## 4. Biome selection

For a target point `t` and an entry `e`, the **distance** is

```
dist(e, t) = sum over i of d(t[i], e.min[i], e.max[i])^2  +  e.offset^2

d(v, lo, hi) = lo - v   if v < lo
               v - hi   if v > hi
               0        otherwise
```

computed in `long`. With realistic climate values every term is far below `2^62`; Databack does not specify the result
when a pathological router or list makes the sum overflow. The offset acts as a seventh
parameter whose target is always 0.

The quart cell's biome is the biome of the entry with the **smallest distance; on a tie, the earliest entry in list
order**.

Evidence:

- Real data: this rule picks vanilla's biome at every untied cell of `climate_*.tsv` (all dimensions and seeds); at
  tied cells see below.
- Synthetic ties (`ties_*.tsv`: coarse random lists where ties are common): this rule equals vanilla's brute-force
  lookup on all 6,000 queries, including all 400 with a tied minimum.

**Deliberate deviation.** Exact ties do happen in real worlds: a target value can land exactly on a quantized range
boundary, e.g. continentalness `-4550` between the deep ocean and ocean ranges (about 1 cell in 1,500 to 3,000 in
the dumps). Vanilla's normal lookup goes through an index whose tie-breaking depends on its earlier queries, so at
such cells **vanilla itself has no single answer**:

- Synthetic lists: its answer differs from the rule above on 199 of 6,000 queries, and the same query gives different
  answers on a fresh list and on one that has answered other queries (`ties_queries.tsv`, `sequential` vs `fresh`).
- Real cells: at three tied cells from the dumps, repeating the query after random earlier queries gave the other
  biome in 5% to 26% of 200 trials (probe). Vanilla's own brute-force lookup gives the rule above every time.

Databack does not reproduce that: the result must be a pure function of the list and the target, so it uses the rule
above. `MultiNoiseParityTest` accepts any tied entry at such cells, and requires them to stay rare.

Any faster search Databack uses must give exactly the same answers as this rule.

## 5. Biome zoom: block to quart cell

A block's biome is not simply its own quart cell's. Each block reads **exactly one** quart cell, chosen pseudo-randomly
from nearby ones, and gets that cell's biome (section 4). (`zoom_<seed>.tsv`: 11,192 lookups per seed, each read one
cell.)

### 5.1 Zoom seed

The choice depends on a 64-bit **zoom seed** derived from the world seed. The derivation is a fixed function of the
seed alone; `zoom_seeds.tsv` lists it for 57 seeds, including `0`, `±1` and the extremes. Databack does not specify it:
see 5.3.

### 5.2 Candidates

Shift the block position by -2 on each axis and take the quart cell containing it:

```
base_x = floor((x - 2) / 4),  base_y = floor((y - 2) / 4),  base_z = floor((z - 2) / 4)
```

The chosen cell is always one of the 8 cells `base + (0 or 1)` per axis: the corners of the quart-scale cube around
the block. The choice is not uniform: in both dumps some corners are chosen far more often than others (e.g. for seed
12345, `+0` on all axes 2,561 times, `+1` on all axes 642 times). In the unshifted frame the chosen cell is up to one
cell away from `floor(b / 4)` on each axis.

Both the zoom seed and the block position matter: the two seeds' dumps differ, and blocks inside one quart cell read
different cells.

### 5.3 Choosing among the candidates

The zoom seed derivation and the choice among the candidates are seeded hash functions, which black-box observation
can confirm but not reveal, and writing them from prior knowledge of vanilla's code would break the clean-room rule.
So Databack does not reimplement them: like the presets, they come from running vanilla's own classes, downloaded at
runtime and rewritten against stand-ins (`BiomeZoom`, `VanillaBiomeZoom`). Only the contract above is relied on: a
world seed gives a zoom seed; a zoom seed and a block position give exactly one quart cell, read once.

If the classes are unavailable (e.g. the download failed), the zoom is unavailable and a warning is logged.

## 6. Reference data and tests

All files are in `src/test/resources/vanilla_reference/biome/` and are regenerated by `misc/vanilla-reference/run.sh
biome`.

| File | Contents | Checks |
|---|---|---|
| `preset_{overworld,nether}.tsv` | Every preset entry, quantized, in list order. **Gitignored: never commit or ship.** | `VanillaPresetExtractorTest` |
| `climate_<dim>_<seed>.tsv` | Quart position, the six target values, the chosen biome | Sections 3 and 4 |
| `ties_lists.tsv`, `ties_queries.tsv` | Synthetic lists; vanilla's indexed (`sequential`, `fresh`) and brute-force results | Section 4 |
| `zoom_seeds.tsv` | World seed → zoom seed | Section 5.1, `BiomeZoomTest` |
| `zoom_<seed>.tsv` | Block position → the quart cell read | Section 5, `BiomeZoomTest` |

Dimensions in `climate_*`: `overworld`, `large_biomes`, `amplified`, `nether`; seeds `12345` and `-987654321`.

## 7. Open questions

**Invalid ranges (section 2).** Confirm the proposed handling of `min > max`.
