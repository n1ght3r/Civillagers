import com.civillagers.buildlogic.flightRecorder
import com.civillagers.buildlogic.requiredJava

plugins {
    // Picks the Loom variant the active Minecraft version needs. See settings.gradle.kts.
    id("dev.kikugie.loom-back-compat")
    // Node naming and the Java version rule. See settings.gradle.kts.
    id("civillagers.build-logic")
    `maven-publish`
}

/*
 * The Fabric nodes. The NeoForge nodes have build.neoforge.gradle.kts; what every node shares is in
 * gradle/shared.gradle.kts, applied near the end.
 */

// Stonecutter supplies `mod.*` and `deps.*` per node from stonecutter.properties.toml.
// `group` stays unset on purpose: every node shares this script, and Loom keys parts of its cache off
// the project coordinates. The publication sets its own groupId below.
val modId = property("mod.id") as String
val modName = property("mod.name") as String
/** Minecraft range written into fabric.mod.json, e.g. `~26.3`. */
val mcCompat = property("mod.mc_compat") as String
val loaderVersion = property("deps.fabric_loader") as String
val javaVersion = requiredJava { sc.current.parsed >= it }

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = modName

repositories {
    // Loom supplies the Minecraft and Fabric repositories.
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

/**
 * Server-side gametests. Their own source set and their own small mod, so none of it can reach the
 * published jar. See src/gametest/java/AGENTS.md.
 */
val gametest: SourceSet = sourceSets.create("gametest")

/**
 * Development tools: `/civillagers benchmark`. The same arrangement as the gametests, its own source set
 * and its own small mod, loaded by runServer and runBenchmark and never packaged. See src/dev/java/AGENTS.md.
 */
val dev: SourceSet = sourceSets.create("dev")

/**
 * What runClient runs: the dev tools on top of the client, so `/civillagers showcase` works in a dev
 * world. No sources of its own; it only puts both on one classpath.
 */
val devClient: SourceSet = sourceSets.create("devClient")

loom {
    splitEnvironmentSourceSets()

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
        register("${modId}_gametest") {
            sourceSet(gametest)
        }
        register("$modId-dev") {
            sourceSet(dev)
        }
    }

    runs {
        register("gametest") {
            server()
            displayName = "Game Test"
            sourceSet = gametest.name
            // Turns the dedicated server into the GameTest runner: it skips the EULA prompt, runs every
            // @GameTest method headlessly, then exits non-zero if any of them failed.
            systemProperties.put("fabric-api.gametest", "true")
            systemProperties.put(
                "fabric-api.gametest.report-file",
                layout.buildDirectory.file("gametest/report.xml").get().asFile.absolutePath,
            )
        }

        // runServer loads the dev tools, so /civillagers benchmark can be typed into its console; runClient
        // too, for /civillagers showcase. `-Pshowcase` shows the guards by itself once a world is open.
        named("server") {
            sourceSet = dev.name
        }
        named("client") {
            sourceSet = devClient.name
            if (providers.gradleProperty("showcase").isPresent) systemProperties.put("civillagers.showcase", "true")
        }

        // Unattended benchmark: starts the dedicated server, runs `/civillagers benchmark <-Pbenchmark>`
        // once it is up, writes run/<node>/benchmark/latest.json and stops the server. See
        // src/dev/java/com/civillagers/dev/benchmark/AGENTS.md.
        register("benchmark") {
            server()
            displayName = "Benchmark"
            sourceSet = dev.name
            systemProperties.put("civillagers.benchmark", providers.gradleProperty("benchmark").getOrElse("quick"))
            systemProperties.put("civillagers.benchmark.exit", "true")
            // A world of its own beside runServer's, generated from the fixed seed gradle/shared.gradle.kts
            // puts in server.properties, so a run never measures the dev world.
            programArguments.addAll("--world", providers.gradleProperty("civillagers.benchmark.world").get())
            if (providers.gradleProperty("profile").isPresent) {
                jvmArguments.addAll(flightRecorder(rootProject.file("run/${project.name}/benchmark/latest.jfr")))
            }
        }
    }

    runConfigs.all {
        // The agent queue; see src/dev/java/com/civillagers/dev/agent/AGENTS.md. `-Pagent=<file>` answers
        // that file once the game is up and then stops it; `-Pdriven` makes a client leave the mouse alone.
        providers.gradleProperty("agent").orNull?.let { script ->
            systemProperties.put("civillagers.agent.script", rootProject.file(script).absolutePath)
            systemProperties.put("civillagers.agent.script.exit", "true")
        }
        if (providers.gradleProperty("driven").isPresent) systemProperties.put("civillagers.agent.driven", "true")
        // `-Pquickplay=<world>` opens that singleplayer world of run/<node>/saves straight from launch.
        if (name == "client") {
            providers.gradleProperty("quickplay").orNull?.let { world ->
                programArguments.addAll(listOf("--quickPlaySingleplayer", world))
                systemProperties.put("civillagers.agent.quickplay", world)
            }
        }
        // One run directory per node: a world saved by one Minecraft version is not readable by another.
        // The gametest runner and datagen get their own again, so a failed run cannot leave a broken
        // world behind for runServer.
        runDirectory = when (name) {
            "gametest" -> rootProject.file("run/${project.name}/gametest")
            "datagen" -> rootProject.file("run/${project.name}/datagen")
            else -> rootProject.file("run/${project.name}")
        }
    }
}

/**
 * The mod loader this node builds for. Loader code lives beside the source set it belongs to, in
 * `src/main/<loader>` and `src/client/<loader>`, and only this loader's directories are compiled.
 * Everything else in `src/main/java` and `src/client/java` is loader independent; `checkLoaderSeam`
 * enforces it. See src/main/java/com/civillagers/platform/AGENTS.md.
 *
 * The directories sit inside `src/main` and `src/client` rather than in a `src/<loader>` of their own
 * because Stonecutter only rewrites versioned comments under `src/<source set>`: anywhere else, every
 * node but the active one would compile an empty directory.
 */
val loader = "fabric"

sourceSets.main {
    java.srcDir("src/main/$loader/java")
    resources.srcDir("src/main/$loader/resources")
}
sourceSets.named("client") {
    java.srcDir("src/client/$loader/java")
    resources.srcDir("src/client/$loader/resources")
}
gametest.java.srcDir("src/gametest/$loader/java")
dev.java.srcDir("src/dev/$loader/java")
dev.resources.srcDir("src/dev/$loader/resources")

/**
 * Every datapack and asset JSON the mod ships that is not written by hand, from `src/datagen`. Keyed by
 * Minecraft version rather than by node, because the Fabric and NeoForge nodes of one Minecraft version
 * share it: the NeoForge nodes have no generators of their own. A resource root of `main`, so the jar
 * picks it up with no further wiring.
 *
 * Regenerate with `:<node>:runDatagen`; `:<node>:checkDatagen` fails when the committed files and the
 * generators have drifted apart. See src/datagen/java/AGENTS.md.
 */
val generatedResources: File = rootProject.file("src/main/generated/${sc.current.version}")

fabricApi.configureDataGeneration {
    outputDirectory.set(generatedResources)
    // Its own source set and its own small mod, like the gametests, so no generator code can reach the
    // published jar.
    createSourceSet = true
    modId = "civillagers-datagen"
    // The item model providers live in net.minecraft.client.data, so datagen runs as a client.
    client = true
}

// Loom adds the datagen output to `main`'s resources by reading the source directories back and setting
// them again, which loses the task dependency Stonecutter had attached. Without this, building any node
// other than the active one fails.
tasks.named("processResources") { dependsOn("stonecutterGenerate") }

/*
 * Datagen keeps a hash cache and skips a file whose hash still matches, so a generated file edited by
 * hand would survive a regeneration. Emptying the directory first makes the task mean what its name
 * says, and lets `checkDatagen` trust the difference.
 */
tasks.named("runDatagen") {
    doFirst { generatedResources.deleteRecursively() }
}

// The gametests compile and run against the mod and everything the mod uses.
gametest.compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
gametest.runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
// So do the dev tools.
dev.compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
dev.runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
// The agent's client half names client classes. Loom's split keeps them out of the mod, where common code
// reaching one is a mistake; a tool whose job is to drive a client is on both sides by definition. Only
// the client entrypoint loads those classes, so a dedicated server never does.
dev.compileClasspath += sourceSets["client"].compileClasspath + sourceSets["client"].output
devClient.runtimeClasspath = dev.output + dev.runtimeClasspath + sourceSets["client"].runtimeClasspath

/**
 * Adds a client-only mod dependency. Loom prefixes these configurations with `mod` where it remaps
 * dependencies and leaves them bare where it does not (26.1+), so resolve the name that actually exists.
 */
fun clientMod(configuration: String, notation: Any) {
    val prefixed = "mod${configuration.replaceFirstChar(Char::uppercase)}"
    val target = if (configurations.findByName(prefixed) != null) prefixed else configuration
    dependencies.add(target, notation)
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // No-op from 26.1 on, which ships unobfuscated.
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    // Optional integrations. The mod runs without any of them. A new one is a `deps.<name>` key in
    // stonecutter.properties.toml, a compileOnly here, a runtime line for runClient, and a gate on
    // Loader.isModLoaded; see "Optional integrations" in the root AGENTS.md.
    clientMod("clientCompileOnly", "maven.modrinth:modmenu:${property("deps.modmenu")}")
    clientMod("clientRuntimeOnly", "maven.modrinth:modmenu:${property("deps.modmenu")}")
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "minecraft" to mcCompat,
        "loader" to loaderVersion,
        "java" to javaVersion,
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
    filesMatching("*.mixins.json") { expand("java" to "JAVA_$javaVersion") }
}

// So do the dev tools'.
tasks.named<ProcessResources>("processDevResources") {
    inputs.property("java", javaVersion)
    filesMatching("*.mixins.json") { expand("java" to "JAVA_$javaVersion") }
}

// The client mixins have their own config, in the client source set.
tasks.named<ProcessResources>("processClientResources") {
    inputs.property("java", javaVersion)
    filesMatching("*.mixins.json") { expand("java" to "JAVA_$javaVersion") }
}

// Keep the client source set's runtime classpath entry present so Fabric Loader does not report
// build/resources/client as a missing path during runClient.
tasks.named("runClient") {
    doFirst { layout.buildDirectory.dir("resources/client").get().asFile.mkdirs() }
}

// Registered lazily: withSourcesJar(), in gradle/shared.gradle.kts, adds the task after this block.
tasks.withType<Jar>().matching { it.name.endsWith("sourcesJar") }.configureEach {
    dependsOn("stonecutterGenerate")
    mustRunAfter("runDatagen")
}

/**
 * Regenerates the datapack and asset JSON and fails when the result differs from what is committed.
 * It asks git rather than diffing trees itself, because git already knows which files are committed and
 * which are new, and datagen writes in place.
 */
tasks.register("checkDatagen") {
    group = "verification"
    description = "Fails when the committed generated resources do not match what the generators produce"
    dependsOn("runDatagen")

    val root = rootProject.projectDir
    val generated = generatedResources

    doLast {
        val relative = root.toPath().relativize(generated.toPath()).toString().replace('\\', '/')
        val process = ProcessBuilder("git", "status", "--porcelain", "--", relative)
            .directory(root)
            .redirectErrorStream(true)
            .start()
        val changes = process.inputStream.bufferedReader().readText().trim()
        check(process.waitFor() == 0) { "git status failed:\n$changes" }
        check(changes.isEmpty()) {
            "Generated resources are out of date. Run \":${project.name}:runDatagen\" and commit:\n$changes"
        }
    }
}

// What gradle/shared.gradle.kts cannot read itself: a script applied with `apply(from)` sees neither
// build-logic nor Stonecutter, so it is handed plain values.
extra["civ.requiredJava"] = javaVersion
extra["civ.loaderChecks"] = listOf("checkDatagen")
apply(from = rootProject.file("gradle/shared.gradle.kts"))

// What this node hands `buildAndCollect`: Loom's remapped jar, not the plain one.
tasks.named<Copy>("buildAndCollect") {
    from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = property("mod.group") as String
            from(components["java"])
        }
    }
    repositories { }
}
