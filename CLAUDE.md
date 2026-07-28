
This is a minecraft 1.7.10 mod that backports modern minecraft's datapack system. It targets 26.1's format, to be specific. Any types, fields, etc not present in a 26.1 datapack should be ignored. The primary goal is world generation, so resources like recipes are out of scope and thus ignored.

It has several components.

The first is the core loader, located at `src/main/java/databack/common/loader`. This package manages the datapack lifecycle - scanning world for datapacks, loading them, and dispatching resources to handlers as needed.

The second is the handler system, located at `src/main/java/databack/common/handlers`. This package is the core 'registry' for each resource in a datapack. The loader will hand a handler a byte array, and the handler will process it as needed. Typically they will deserialize it from json into a java object, then expose the java object from a `getObject(String id)` method. They are also responsible for syncing data across the network.

The third is the DTO classes, located at `src/main/java/databack/common/dto`. These are 1:1 mappings for the datapack json format. The goal is to be able to load a json tree into a java object via gson without calling specific deserialization methods, and these classes handle all of that. Some types are 'tagged unions,' and there are already plenty of examples of this pattern (biome attributes, density functions, etc).

The fourth is various shim systems, that convert 1.7's world state into something modern packs can use. This includes BlockStates (which are handled by GTNHLib), tags, height maps, etc. These are currently spread over the whole project randomly.

The fifth (not yet implemented) system is the world generator, which will use all of the above to generate worlds.

All systems must 1:1 compatible with modern. While we will likely have to make manual edits to some datapacks, most should work natively and without edits. Unimplemented/out-of-scope features should emit a warning and safely no-op.

The vanilla 26.1 datapack is automatically extracted to build/modernData/data/minecraft, and should be used as a reference.

When examining the structure of json files in a datapack, use the `jq` command as much as possible (when on linux).

Write tests for as much code as possible.
