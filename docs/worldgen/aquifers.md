# Aquifers

This document describes how Databack decides what fills open terrain (density ≤ 0): air, the default fluid, lava, or
a stone barrier. The target is Minecraft 26.1.

## 1. Without aquifers

When a noise setting has `aquifers_enabled: false` (or vanilla's aquifer is unavailable), the global fluid rule
applies: open blocks below `y = -54` are lava, other open blocks below `sea_level` are the default fluid, everything
else open is air. (`surface/chunk_overworld_no_aquifers_*_noise`: exact.)

## 2. With aquifers

Vanilla's aquifer is a seeded 3D grid of cells with per-cell fluid levels, barriers that depend on terrain density,
and inputs from six router fields (barrier, floodedness, spread, lava, erosion and depth) and the preliminary surface
level sampled at several nearby chunks. A black-box model of it (an earlier attempt) was far from vanilla in real
terrain, so Databack runs vanilla's own aquifer class instead, like the biome presets and zoom:

- `VanillaDatapackDownloader` fetches `Aquifer$NoiseBasedAquifer.class` from Mojang at runtime.
- `VanillaAquifer` rewrites it for Java 8 against stand-ins (`worldgen/aquifer/standin`, mapped in `AquiferMappings`)
  and constructs one per chunk; `AquiferSystem` asks it about every open block, as vanilla's noise step does.
- Only names, signatures and call targets of the class were used. The stand-ins' behaviour was established black-box
  on the 26.1 jar: the math helpers (`clamp`, `map`, `clampedMap`, `quantize(v, step) = floor(v / step) * step`,
  `square`, `sectionToBlockCoord`), the fluid status (fluid strictly below its level, air at and above), the
  "no fluid" height (`-32512`), the deep dark test (erosion < -0.225 and depth > 0.9), and the noise state's
  `maxPreliminarySurfaceLevel(x1, z1, x2, z2)` (the highest preliminary surface level sampled at
  `(x1 + 4i, z1 + 4j)` within the rectangle; 400/400 rectangles).
- **Approximation:** vanilla's aquifer randomness is not reproduced; Databack's positional random forked by the name
  `minecraft:aquifer` stands in for it.

## 3. Reference data and tests

`src/test/resources/vanilla_reference/aquifer/` (`misc/vanilla-reference/run.sh aquifer`): real overworld chunks with
aquifers on and ore veins off (`chunk_<seed>_<cx>_<cz>_{blocks,density}.bin.gz`), and synthetic cases
(`case_<name>_<cx>_<cz>.bin.gz` from `cases/*.json`: fully open terrain with constant aquifer fields).

`AquiferParityTest` runs our aquifer on vanilla's own density: every synthetic case matches block for block; the real
chunks match 99.88% and 99.98% of open blocks, with the same shares of air, water, lava and barrier.
