
plugins {
    id("com.gtnewhorizons.gtnhconvention")
    id("me.champeau.jmh") version "0.7.3"
}

tasks.test.configure {
    useJUnitPlatform()
    // The 26.1 client jar addon.gradle's downloadVanillaDatapack caches; biome preset tests read classes from it.
    systemProperty("databack.vanillaJar",
        gradle.gradleUserHomeDir.resolve("caches/minecraft-client-jars/minecraft-26.1-client.jar").absolutePath)
    // Stands in for VanillaDatapackDownloader's download, so world loads in tests don't reach Mojang.
    systemProperty("databack.vanillaDatapack", file("misc/test-packs/minecraft").absolutePath)
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// JMH benchmarks live in src/jmh. They need Minecraft (which RetroFuturaGradle only puts on main's classpaths, not
// on the configurations the jmh plugin extends) and the test fixtures, so wire those in explicitly.
sourceSets.named("jmh") {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.test.get().output +
        sourceSets.test.get().runtimeClasspath
}

jmh {
    // e.g. ./gradlew jmh -Pjmh.includes=ChunkDensity
    providers.gradleProperty("jmh.includes").orNull?.let { includes.add(it) }
    providers.gradleProperty("jmh.profilers").orNull?.let { profilers.add(it) }
    // e.g. -Pjmh.jvm=/usr/lib/jvm/java-25-openjdk/bin/java to benchmark on the JVM players actually use
    providers.gradleProperty("jmh.jvm").orNull?.let { jvm.set(it) }
    // e.g. -Pjmh.params=mode=shared,clipped;dimension=overworld to override @Param values
    providers.gradleProperty("jmh.params").orNull?.split(';')?.forEach { param ->
        val (name, values) = param.split('=', limit = 2)
        benchmarkParameters.put(name, objects.listProperty(String::class.java).value(values.split(',')))
    }
    jvmArgsAppend.add("-Ddataback.projectDir=" + project.projectDir.absolutePath)
    resultFormat.set("JSON")
}

// The benchmark fat jar includes Minecraft and Forge.
tasks.named<Jar>("jmhJar") {
    isZip64 = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Runs the benchmarks on the source sets' classpaths instead of the fat jar. The jar misses Minecraft (RetroFuturaGradle
// adds it as loose files), and in it JMH's ASM 9 shadows the ASM 5 the vanilla class rewriter is built against. JMH's
// own libraries come first (Minecraft ships an older jopt-simple), less its ASM.
// e.g. ./gradlew jmhClasspath -Pjmh.args='CarverBenchmark -prof gc'
tasks.register<JavaExec>("jmhClasspath") {
    dependsOn("jmhCompileGeneratedClasses")
    mainClass.set("org.openjdk.jmh.Main")
    classpath = configurations.getByName("jmh").filter { !it.name.startsWith("asm-") } +
        files(layout.buildDirectory.dir("jmh-generated-classes"), layout.buildDirectory.dir("jmh-generated-resources")) +
        sourceSets.main.get().runtimeClasspath + sourceSets.test.get().runtimeClasspath +
        sourceSets.named("jmh").get().runtimeClasspath
    providers.gradleProperty("jmh.jvm").orNull?.let { setExecutable(it) }
    systemProperty("databack.projectDir", project.projectDir.absolutePath)
    args((providers.gradleProperty("jmh.args").orNull ?: "").split(' ').filter { it.isNotEmpty() })
}
