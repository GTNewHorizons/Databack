# Ore veins

This document specifies how Databack places ore veins: large ribbons of copper or iron ore in filler stone, decided
per block during the noise step. The target is Minecraft 26.1.

It describes behaviour, and is written from the datapack format and black-box observation of the real 26.1 jar
through the reference harness (`misc/vanilla-reference`, `run.sh ore_vein`). Where this document and the harness
disagree, the harness wins.

## 1. Inputs

Veins are placed only when the noise settings have `ore_veins_enabled: true`. A datapack controls them only through
three noise router fields, sampled at each block with the same cell interpolation as `final_density`:

- `vein_toggle` (`t`): where veins are, and which kind.
- `vein_ridged` (`r`): shapes veins into ribbons.
- `vein_gap` (`g`): breaks up the ore inside a vein.

Everything else (kinds, height bands, thresholds, blocks) is fixed by the game.

## 2. Vein kinds

| Kind | When | Band (inclusive) | Filler | Ore | Raw block |
|---|---|---|---|---|---|
| Copper | `t > 0` | y 0 to 50 | `granite` | `copper_ore` | `raw_copper_block` |
| Iron | `t <= 0` | y -60 to -8 | `tuff` | `deepslate_iron_ore` | `raw_iron_block` |

The bands are independent of the datapack: vanilla's `vein_toggle` is 0 outside y -60 to 50, but a constant
`vein_toggle` still only produces veins inside the bands (`toggle_*` cases).

## 3. Placement

Every block the terrain and aquifer leave as the default block is considered: solid terrain (density > 0) and aquifer
barriers alike (`aquifer_barriers`: barriers in open terrain become vein blocks at the same rates). A block is **in a
vein** when it lies in its kind's band and

- `|t| + fade > 0.4`, where `fade = -0.2 + 0.2 * min(d, 20) / 20` and `d` is the distance to the nearer end of the band
  (`min(y - bottom, top - y)`): veins thin out over the 20 blocks at each end of a band (`toggle_041` to `toggle_06`;
  every tie point fails, so the comparison is strict), and
- `r < 0` (`ridged_*`: 0 places nothing, -0.01 places veins).

Both are exact against every block of the dumps (`solid_real`, `real_ridged`, `real_toggle`), given vanilla's field
values.

A block in a vein becomes:

1. stone (unchanged) with probability 0.3;
2. otherwise ore if a second roll is below `clampedMap(|t|, 0.4, 0.6, 0.1, 0.3)` (0.1 at `|t| = 0.4` rising linearly to
   0.3 at 0.6 and above; `fade` is not included) and `g >= -0.3` (`gap_*`: -0.3 places ore, -0.31 does not);
   of ore, 2% is the raw block instead;
3. otherwise filler.

The three rolls are independent: the same positions stay stone for every `t`, and ore positions at a low `t` remain ore
at a higher one. **Approximation:** vanilla's rolls are seeded values black-box data cannot reveal; Databack uses its
own positional random, forked by the name `minecraft:ore`.

## 4. Order with other steps

Veins replace only the default block, so they never touch air or fluids, and surface rules do not replace vein blocks
(they only see default blocks). Vein blocks are solid for the surface rules' depth counting.

If a vein block has no 1.7 counterpart (no block identity), a warning is logged once and the default block stays.

## 5. Reference data and tests

`src/test/resources/vanilla_reference/ore_vein/` (`misc/vanilla-reference/run.sh ore_vein`):

- `chunk_<seed>_<cx>_<cz>_blocks.bin.gz`: real overworld chunks after the noise step, ore veins on, aquifers off.
- `case_<name>_<cx>_<cz>_blocks.bin.gz`: synthetic cases from `cases/<name>.json` on all-solid terrain
  (`final_density: 1`), with constant vein fields or the real ones; `aquifer_barriers` is open terrain with aquifers
  on and barriers everywhere.
- `*_fields.bin.gz`: vanilla's interpolated `vein_toggle`, `vein_ridged`, `vein_gap` for y -64 to 63.

Tests: exact checks for the vein mask (given vanilla's fields, and per layer for the constant cases); statistical
checks for the stone, ore and raw shares; a parity check of our field values against vanilla's.
