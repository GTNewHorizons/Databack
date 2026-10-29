# Deferred implementation tasks

Each section has enough context for an LLM to continue the work.

---

## Missing 26.1 types

Checked against the 26.1 data generator's `reports/registries.json` (see the EFR section for how to run it). Every
registered DTO type exists in 26.1; these 26.1 types have no DTO yet:

- Structure processors: `minecraft:blackstone_replace`, `minecraft:jigsaw_replacement`,
  `minecraft:lava_submerged_block`, `minecraft:nop` (`processor_list/BuiltinProcessors.java`).
- Position rule tests: `minecraft:linear_pos`, `minecraft:axis_aligned_linear_pos`.

Unknown tagged-union types no longer fail their resource: each union gives a placeholder that does nothing
(`TaggedUnionLoader.setUnknown`, set in each `Builtin*.init`), and the type is logged once.

---

## Stubbed fields — complex types not yet implemented

These `JsonElement` fields require new DTO systems to be implemented before they can be typed.

### FloatProvider — ✓ DONE

`IFloatProvider` union implemented in `float_provider` package. Variants: `constant`, `uniform`, `clamped`,
`clamped_normal`, `trapezoid`. Registered in `BuiltinFloatProviders.init()`.

All listed fields updated in `BuiltinCarvers.java` (`yScale`, `horizontal_radius_multiplier`,
`vertical_radius_multiplier`, `floor_level`, `distance_factor`, `thickness`, `horizontal_radius_factor`,
`vertical_rotation`) and `BuiltinConfiguredFeatures.java` (`density`, `wetness`, `height_scale`,
`stalactite_bluntness`, `stalagmite_bluntness`, `wind_speed`).

### BlockStateProvider — ✓ DONE

`IBlockStateProvider` union implemented in `block_state_provider` package. Variants: `simple_state_provider`,
`weighted_state_provider`, `noise_provider`, `noise_threshold_provider`, `dual_noise_provider`,
`rotated_block_provider`, `unstable_block_provider`, `rule_based_state_provider`,
`randomized_int_state_provider`. Registered in `BuiltinBlockStateProviders.init()`.

All listed fields updated in `BuiltinConfiguredFeatures.java` and `BuiltinTreeComponents.java`.
Also updated: `FallenTreeConfig.trunk_provider`, `PlaceOnGroundTreeDecorator.block_state_provider`,
`AttachedToLogsDecorator.block_provider`.

### PlacedFeatureRef / FeatureRef — placed feature references ✓ DONE

`IPlacedFeatureRef` union implemented in `placed_feature` package. Handles string IDs
(`PlacedFeatureIdRef`), tag strings (`PlacedFeatureTagRef`), and inline `PlacedFeature` objects.
Gson adapter registered in `BuiltinPlacementModifiers.init()`.

All listed fields updated; `PlacedFeatureSet`/`PlacedFeatureId`/`PlacedFeatureTag` removed.
Also added: `coral_claw`, `coral_mushroom`, `coral_tree` configured feature types (appear inline
in `simple_random_selector`); `noise_threshold_provider` block state provider type.

### Complex one-off structs

| Field | Required struct | mcdoc location |
|-------|----------------|----------------|
| `GeodeConfig.blocks` | `GeodeBlocks` | `feature/mod.mcdoc` ~L297 |
| `GeodeConfig.layers` | `GeodeLayers` | `feature/mod.mcdoc` ~L299 |
| `GeodeConfig.crack` | `GeodeCrackConfig` | `feature/mod.mcdoc` ~L300 |
| `BlockColumnConfig.layers` | `WeightedList<{height: IIntProvider, provider: IBlockStateProvider}>` | `feature/mod.mcdoc` ~L66 |
| `MangroveRootPlacer.mangrove_root_placement` | `MangroveRootPlacement` | `feature/tree.mcdoc` |
| `MangroveRootPlacer.above_root_placement` | `AboveRootPlacement` (optional) | `feature/tree.mcdoc` |
| `FossilConfig.fossil_processors` / `.overlay_processors` | ✓ DONE — `IProcessorListRef` | `worldgen/processor_list.mcdoc` |

---

## Missing handlers — standalone resource types with no DTO or handler

These types exist as standalone resources in the 26.1 datapack but have no DTO or handler implemented.
Reference files are in `build/modernData/data/minecraft/worldgen/`.

### Structure-related (coherent group — implement together)

| Resource type | Notes |
|---|---|
| `worldgen/structure` | ✓ DONE — `IStructure` tagged union; `JigsawStructure`, `MineshaftStructure`, `NetherFossilStructure`, `OceanRuinStructure`, `RuinedPortalStructure`, `ShipwreckStructure`, `EmptyStructure`; `pool_aliases` / `dimension_padding` stubbed as `JsonElement` |
| `worldgen/structure_set` | ✓ DONE — `StructureSet` + `IStructurePlacement` tagged union; `RandomSpreadPlacement`, `ConcentricRingsPlacement` |
| `worldgen/template_pool` | ✓ DONE — `TemplatePool` + `IPoolElement` tagged union; single/legacy_single, list, feature, empty variants |
| `worldgen/processor_list` | ✓ DONE — `ProcessorList` (array or object form) + `IProcessor` tagged union + `IRuleTest` tagged union; `IProcessorListRef` handles string\|inline; `FossilConfig.fossil_processors` / `.overlay_processors` updated |

### Biome source (relevant to noise world gen)

| Resource type | Notes |
|---|---|
| `worldgen/multi_noise_biome_source_parameter_list` | ✓ DONE — `MultiNoiseBiomeSourceParameterList` DTO + handler; single `preset` string field |

### Probably out of scope (not world gen)

| Resource type | Notes |
|---|---|
| `worldgen/flat_level_generator_preset` | Flat world UI presets |
| `worldgen/world_preset` | World type selection UI |
| `world_clock` | `WorldClockList` handler coded but not registered in `CommonProxy`; likely out of scope |

---

---

## EFR block identities — modern → EFR block map

Surveyed 2026-10-29 with EFR's default config (as tests register it, see
`src/test/java/databack/test/support/EFRBootstrap.java`):

- **EFR → modern:** 319 of EFR's 322 enabled blocks resolve to modern ids (462 ids). The rest have no 26.1 counterpart:
  `netherite_stairs`, and the modded-ore compat blocks `modded_raw_ore_block` and `modded_deepslate_ore`.
- **Modern → 1.7:** 419 of 26.1's 1170 block ids have no identity (`BlockIdentityRegistry.getObject` is null), so
  features skip them (`LegacyStates`) and other code turns them into air (`MiscAdapters.resolveBlockState`). By cause:
  - **Air variants:** `cave_air` and `void_air` should resolve to 1.7's air.
  - **EFR has them, behind experiments that are off by default** (`ConfigExperiments`): crimson and warped wood,
    plants, nylium, vines and shroomlight; mangrove; moss and azalea; dripstone; sculk; lightning rod. Not yet checked
    whether `EFRBlockIdentities` maps them when enabled: rerun the survey with the experiments on (EFR enables them all
    when `etfuturum.testing` is set).
  - **Not in EFR (or no 1.7 block), and placed by worldgen:** ocean (seagrass, tall seagrass, kelp, sea pickle, corals),
    lush caves (big and small dripleaf, spore blossom, hanging roots, rooted dirt, flowering azalea), deep dark (sculk
    vein, sensor, shrieker), pale garden (pale oak, pale moss, hanging moss, eyeblossoms, resin), powder snow, pitcher
    plants, torchflower, bush, wildflowers, leaf litter, firefly bush, dry grass, suspicious sand and gravel.
  - **Not in EFR, not worldgen:** candles, campfires, bells, lecterns, grindstones, copper bars, chests, lanterns and golem
    statues, shelves, hanging signs, and the tile-entity forms 1.7 stores differently (mob heads, potted plants, item
    frames, banners by colour).

Survey: a test listing `assets/minecraft/blockstates/*.json` from the 26.1 jar (`VanillaJar.JAR`) and checking each id
with `BlockIdentityRegistry.INSTANCE.getObject`, and the reverse with `getBlockIdentity` over `ModBlocks.VALUES` and
metas 0..15. The full 26.1 block list with state properties also comes from vanilla's data generator, run on the harness
jar: `java -cp <harness classpath> net.minecraft.data.Main --reports --output <dir>` → `reports/blocks.json`
(`jq -r 'keys[]' blocks.json` for ids).

---

## EFR block properties — GTNHLib properties for EFR's own blocks

Modern block state properties reach 1.7 meta through GTNHLib's `BlockPropertyRegistry` (`MiscAdapters.resolveBlockState`
writes them, `LegacyStates.toModern` reads them back). EFR registers no GTNHLib properties itself. GTNHLib's own
registrations reach EFR blocks only where they are by type: EFR stairs (`facing`, half), slabs (top), logs and pillars
(`axis`) and trapdoors (`facing`, half, `open`) already work.

These EFR blocks report no properties, so a datapack's `facing`, `axis` and the like are dropped (logged as having no
1.7 equivalent) and the block gets its default meta:

- observer, barrel, smoker (GTNHLib's furnace `facing` is registered on vanilla's furnace only), bee nest / beehive
  (`facing`, `honey_level`), chain (`axis`), lantern (`hanging`);
- EFR doors (vanilla's wooden door reports none either).

Survey the rest of `ModBlocks` the same way (dump `BlockPropertyRegistry.getBlockState(block, meta).toMap()` per meta in
a test; tests register EFR's blocks, see `src/test/java/databack/test/support/EFRBootstrap.java`). Register the missing
properties with `BlockPropertyRegistry.registerProperty`, next to `EFRBlockIdentities`, using modern property names and
values (or translating in `PropertyTranslator`). Meta layouts come from each block's 1.7 class. Needed once features or
structures place oriented EFR blocks.
