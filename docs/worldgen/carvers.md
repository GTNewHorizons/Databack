# Carvers

This document describes Databack's carver step: the tunnel caves and ravines (`configured_carver`, listed per biome
in `carvers`) carved after surface rules. The target is Minecraft 26.1.

## 1. Vanilla's carvers, run as is

A carver's shape is a seeded random walk that black-box data cannot reveal, so Databack runs vanilla's own carver
classes, like the aquifer and the biome presets:

- `VanillaDatapackDownloader` fetches `WorldCarver`, `CaveWorldCarver`, `CanyonWorldCarver`, `NetherWorldCarver` and
  `WorldCarver$CarveSkipChecker` from Mojang at runtime.
- `VanillaCarvers` rewrites them for Java 8 against stand-ins (`worldgen/carver/standin`, mapped in `CarverMappings`)
  and takes the three carver constants. Only names, signatures and call targets of the classes were used. Their codec
  setup is inert: it builds lambdas over configured carvers, which become null.
- The stand-ins' behaviour was established black-box on the 26.1 jar (`run.sh carver`, `carver/probes/*.tsv`,
  `CarverProbeTest`): `sin`/`cos` (a 65536-step table, wrapping), `floor`, the legacy and xoroshiro randoms, a
  carving's own random (the legacy one), float and height providers, `randomBetween`, chunk positions (the middle block
  is 8 in), and the overworld context's range.
- Block states are interned per block, so vanilla's `==` comparisons hold; `replaceable` is a membership test,
  answered by the block tags of each block's identity.

## 2. The step (`CarverSystem`)

For each chunk, every chunk within 8 chunks may start carvings: for the `i`th carver of its biome, a random over the
legacy source is seeded with `largeFeatureSeed(seed + i, startX, startZ)` (the world seed seeds a random whose two first
longs scale the chunk coordinates, mixed with the seed; `large_feature_seed.tsv`), and the carver carves its part of
the chunk if the start chunk rolls a start. One carving mask is shared by all carvers of the chunk. Carved blocks are
filled by the chunk's aquifer (the same one the noise step uses), lava at and below `lava_level`.

- **Approximation:** vanilla's aquifer randomness is ours (docs/worldgen/aquifers.md), so carved blocks the aquifer
  walls off differ a little.
- **Unverified:** which biome of a start chunk gives its carvers. Databack takes the biome at the chunk's corner at
  y 0; every vanilla overworld biome has the same carvers, so this only matters for datapacks.
- **Approximation:** when a carver exposes dirt under grass, it re-runs the surface rule there; Databack evaluates the
  rule as for the top block of a column, with steepness false.
- Fluid updates next to carved blocks are not scheduled; 1.7 updates such fluids when they are next touched. Debug
  settings are ignored (with a warning).

## 3. Reference data and tests

`src/test/resources/vanilla_reference/carver/` (`misc/vanilla-reference/run.sh carver`): real overworld chunks before
and after vanilla's carver step (aquifers on, ore veins off), an all-solid case (`final_density: 1`, aquifers off), and
the probes. The harness binds vanilla's block tags from the extracted datapack; without them carvers replace nothing.

`CarverParityTest`: the all-solid case matches block for block (8 chunks); the real chunks agree on 99.5% and 97.5% of
carved blocks.
