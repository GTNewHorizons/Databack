#!/usr/bin/env bash
# Regenerates src/test/resources/vanilla_reference from the real 26.1 jar.
# Usage: run.sh [density] [biome] [surface] [aquifer]  (no arguments: everything)
# Needs a Java 25 JDK (JAVA25_HOME), jq, curl and zip. The jar is the one build.gradle's
# downloadVanillaDatapack task caches; libraries are downloaded into ~/.cache/databack-vanilla.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$here/../.."
java25="${JAVA25_HOME:-/usr/lib/jvm/java-25-openjdk}/bin"
cache="$HOME/.cache/databack-vanilla"
jar="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/minecraft-client-jars/minecraft-26.1-client.jar"

mkdir -p "$cache/libs"
if [ ! -f "$cache/version.json" ]; then
    url=$(curl -s https://piston-meta.mojang.com/mc/game/version_manifest_v2.json | jq -r '.versions[] | select(.id=="26.1") | .url')
    curl -s "$url" > "$cache/version.json"
fi
(cd "$cache/libs" && jq -r '.libraries[] | select(.downloads.artifact != null) | select((.rules // []) | length == 0) | .downloads.artifact.url' ../version.json \
    | grep -v lwjgl | while read -r u; do [ -f "$(basename "$u")" ] || curl -sSfO "$u"; done)

# The harness lives in a vanilla package, which a signed jar forbids.
if [ ! -f "$cache/mc-unsigned.jar" ]; then
    cp "$jar" "$cache/mc-unsigned.jar"
    zip -qd "$cache/mc-unsigned.jar" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/*.EC' || true
fi

cp="$cache/mc-unsigned.jar:$(ls "$cache"/libs/*.jar | tr '\n' ':')"
build="$(mktemp -d)"
"$java25/javac" -d "$build" -cp "$cp" "$here"/net/minecraft/world/level/levelgen/*.java
"$java25/java" -cp "$build:$cp" net.minecraft.world.level.levelgen.VanillaReference "$repo/src/test/resources/vanilla_reference" "$@"
rm -rf "$build"
