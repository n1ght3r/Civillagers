import com.civillagers.buildlogic.requiredJava

plugins {
    // NeoForge's build plugin. Its version is on the plugin classpath from settings.gradle.kts, which
    // resolves it without applying it.
    id("net.neoforged.moddev")
    // Node naming and the Java version rule. See settings.gradle.kts.
    id("civillagers.build-logic")
    `maven-publish`
}

/*
 * The NeoForge nodes, `<version>-neoforge`. Each is one Minecraft version and one loader, with the same
 * sources as the Fabric node of that version. What every node needs is in gradle/shared.gradle.kts,
 * applied near the end. See src/main/java/com/civillagers/platform/AGENTS.md.
 */

val modId = property("mod.id") as String
val modName = property("mod.name") as String
/** Minecraft range written into neoforge.mods.toml, in Maven range syntax, e.g. `[1.21.1,1.21.2)`. */
val mcRange = property("mod.mc_compat") as String
val neoForgeVersion = property("deps.neoforge") as String
val javaVersion = requiredJava { sc.current.parsed >= it }
/** NeoForge mod ids cannot contain a hyphen, so both loaders use this one for the gametests. */
val gametestModId = "${modId}_gametest"

// The loader goes in the version so the jar never collides with the Fabric jar of the same Minecraft
// version in build/libs/.
version = "${property("mod.version")}+${sc.current.version}-neoforge"
base.archivesName = modName

repositories {
    // ModDevGradle supplies the NeoForged, Mojang and Minecraft libraries repositories.
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

/** The mod loader this node builds for; see build.gradle.kts for how loader directories work. */
val loader = "neoforge"

neoForge {
    version = neoForgeVersion
}

/*
 * One source set. ModDevGradle has no split between `main` and `client`, so the client sources compile
 * into `main` here. The Fabric nodes keep `loom.splitEnvironmentSourceSets()`, so the compiler still
 * catches client code reached from common code there.
 *
 * `src/client` cannot simply be listed as a directory of `main`. Stonecutter preprocesses `src/<name>`
 * only for a source set called `<name>`, so without a `client` source set nothing writes this node's
 * copy of the client sources. The `client` source set below exists only to be preprocessed; it is never
 * compiled or packaged. Its output goes into `main` by path: the sources themselves when this is the
 * active node, which Stonecutter leaves in place, and Stonecutter's generated copy otherwise.
 *
 * `src/datagen` is absent on purpose: the generators are Fabric only, and these nodes read the JSON the
 * Fabric node of the same Minecraft version writes.
 */
sourceSets.create("client")
val clientSources: File =
    if (sc.current.isActive) rootProject.file("src/client")
    else layout.buildDirectory.dir("generated/stonecutter/client").get().asFile

val main: SourceSet = sourceSets["main"]
main.apply {
    java.srcDir("src/main/$loader/java")
    resources.srcDir("src/main/$loader/resources")
    java.srcDir(files(clientSources.resolve("java"), clientSources.resolve("$loader/java"))
        .builtBy("stonecutterGenerateClient"))
    resources.srcDir(files(clientSources.resolve("resources"), clientSources.resolve("$loader/resources"))
        .builtBy("stonecutterGenerateClient"))
    resources.srcDir(rootProject.file("src/main/generated/${sc.current.version}"))
}

/*
 * The same gametests the Fabric nodes run, as their own small mod. `src/gametest/neoforge` holds the
 * harness that finds and registers them in place of Fabric API's; the test classes are shared, and
 * stonecutter.gradle.kts swaps their one Fabric import. See src/gametest/java/AGENTS.md.
 */
val gametest: SourceSet = sourceSets.create("gametest") {
    java.srcDir("src/gametest/$loader/java")
    resources.srcDir("src/gametest/$loader/resources")
    compileClasspath += main.compileClasspath + main.output
    runtimeClasspath += main.runtimeClasspath + main.output
}

/*
 * The optional mods runClient loads. They go on that run alone, not on `runtimeOnly`, where they would
 * load into runServer and runGametest too. A run's classpath is its source set's runtime classpath, so
 * runClient gets a source set with no sources of its own, whose runtime classpath is `main`'s plus these.
 *
 * Added to, never replaced: a source set's own runtime classpath is where ModDevGradle puts DevLaunch,
 * so one that assigns its classpath outright launches nothing.
 */
val clientRunMods: Configuration = configurations.create("clientRunMods")
val clientRun: SourceSet = sourceSets.create("clientRun") {
    runtimeClasspath += main.output + main.runtimeClasspath + clientRunMods
}

neoForge {
    mods {
        create(modId) { sourceSet(main) }
        create(gametestModId) { sourceSet(gametest) }
    }

    runs {
        val node = project.name
        configureEach {
            // One run directory per node, for the reason build.gradle.kts gives.
            gameDirectory.set(rootProject.file(if (name == "gametest") "run/$node/gametest" else "run/$node"))
        }
        create("client") {
            client()
            sourceSet = clientRun
        }
        create("server") {
            server()
        }
        // The GameTest runner: a dedicated server that runs every registered test headlessly and exits
        // with the number of failed required tests, the same contract as the Fabric runner.
        create("gametest") {
            type = "gameTestServer"
            sourceSet = gametest
            // Vanilla's own JUnit report, at the path the Fabric nodes write theirs to. The server's
            // --report option arrived with 1.21.5; before it the harness installs the reporter itself.
            val report = layout.buildDirectory.file("gametest/report.xml").get().asFile.absolutePath
            if (sc.current.parsed >= "1.21.5") programArguments.addAll("--report", report)
            else systemProperty("civillagers.gametest.report", report)
        }

        // ModDevGradle loads every declared mod by default, and each run only has the classes of its own
        // source set. So each is told which mods to load: the gametests belong to the gametest run alone.
        val mainMod = mods.named(modId)
        listOf("client", "server").forEach { run -> named(run) { loadedMods.set(mainMod.map { setOf(it) }) } }
        named("gametest") { loadedMods.set(mainMod.zip(mods.named(gametestModId)) { a, b -> setOf(a, b) }) }
    }
}

dependencies {
    // Optional integrations; see build.gradle.kts. Mod Menu is Fabric only: NeoForge's own mods list
    // takes the config screen through IConfigScreenFactory instead.
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "minecraft" to mcRange,
        "neoforge" to neoForgeVersion,
        "java" to javaVersion,
    )
    inputs.properties(props)
    // Stonecutter rewrites the versioned comments in `src/` into this node's own tree first.
    dependsOn("stonecutterGenerate")
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
    filesMatching("*.mixins.json") { expand("java" to "JAVA_$javaVersion") }
    // Datagen's hash cache, which Loom keeps out of the Fabric jar and nothing keeps out of this one.
    exclude("**/.cache/**")
}

/**
 * Fails when a Fabric-only key survives into the processed resources, from datagen or from a
 * hand-written file: NeoForge would load it as a broken recipe or ignore the condition. When datagen
 * starts writing `fabric:load_conditions`, translate them in processResources the way ThirstWasTaken2's
 * build-logic ResourceTranslation.kt does, and this keeps the translation honest.
 */
tasks.register("checkNeoForgeResources") {
    group = "verification"
    description = "Fails when a Fabric-only JSON key survives into the NeoForge resources"

    val processed = tasks.named<ProcessResources>("processResources").map { it.destinationDir }
    inputs.dir(processed)
    val fabricKey = Regex(""""fabric:[^"]*"\s*:""")

    doLast {
        val root = processed.get()
        val offenders = root.walk().filter { it.isFile && it.extension == "json" }.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> fabricKey.containsMatchIn(line) }
                .map { (index, line) -> "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}: ${line.trim()}" }
        }.toList()
        check(offenders.isEmpty()) { "Fabric keys in the NeoForge resources:\n" + offenders.joinToString("\n") }
    }
}

/*
 * Every ModDevGradle node decompiles and recompiles Minecraft in createMinecraftArtifacts, which takes
 * minutes and gigabytes each. With org.gradle.parallel=true several nodes would do it at once, so a shared
 * build service lets one run at a time.
 */
abstract class CreateMinecraftArtifactsMutex : BuildService<BuildServiceParameters.None>
val createMinecraftArtifactsMutex = gradle.sharedServices
    .registerIfAbsent("createMinecraftArtifactsMutex", CreateMinecraftArtifactsMutex::class) { maxParallelUsages = 1 }

tasks.named("createMinecraftArtifacts") {
    dependsOn("stonecutterGenerate")
    usesService(createMinecraftArtifactsMutex)
}

// Vanilla's reporter writes the file but not the directory it goes in.
tasks.named("runGametest") {
    val reportDir = layout.buildDirectory.dir("gametest")
    doFirst { reportDir.get().asFile.mkdirs() }
}

extra["civ.requiredJava"] = javaVersion
extra["civ.loaderChecks"] = listOf("checkNeoForgeResources")
apply(from = rootProject.file("gradle/shared.gradle.kts"))

// What this node hands `buildAndCollect`: ModDevGradle does not remap, so the plain jar ships.
tasks.named<Copy>("buildAndCollect") {
    from(tasks.jar.flatMap { it.archiveFile }, tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile })
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
