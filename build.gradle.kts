
plugins {
    id("com.gtnewhorizons.gtnhconvention")
    id("me.champeau.jmh") version "0.7.3"
}

tasks.test.configure {
    useJUnitPlatform()
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
