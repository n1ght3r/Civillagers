/*
 * The tasks every node shares, whatever mod loader it builds for: the Java toolchain, the seam checks,
 * what never belongs in a jar, the task that collects the jars, and `ci`, the one task CI runs.
 *
 * Applied at the end of build.gradle.kts (Fabric) and build.neoforge.gradle.kts. A script applied with
 * `apply(from)` sees neither build-logic nor Stonecutter, so each sets these first:
 *
 * | Property            | What                                                                        |
 * |---------------------|-----------------------------------------------------------------------------|
 * | `civ.requiredJava`  | the node's Java version, from build-logic's `requiredJava`                  |
 * | `civ.loaderChecks`  | the verification tasks only that loader has, which `ci` runs as well        |
 *
 * What `buildAndCollect` copies is each script's own business: Loom's remapped jar on Fabric, the plain
 * jar on NeoForge.
 */

@Suppress("UNCHECKED_CAST")
fun <T> handed(name: String): T = project.extensions.extraProperties.get(name) as T

val requiredJava: JavaVersion = JavaVersion.toVersion(handed<Int>("civ.requiredJava"))

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(requiredJava.majorVersion.toInt())
}

configure<JavaPluginExtension> {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava

    toolchain {
        vendor.set(JvmVendorSpec.ADOPTIUM)
        languageVersion.set(JavaLanguageVersion.of(requiredJava.majorVersion))
    }
}

// Per-directory notes for contributors live next to the files they describe, not in the jars.
tasks.withType<ProcessResources>().configureEach {
    exclude("**/AGENTS.md", "**/*.bak")
}
tasks.withType<Jar>().matching { it.name.endsWith("sourcesJar") }.configureEach {
    exclude("**/AGENTS.md", "**/*.bak", "**/.cache/**")
}

val modId = property("mod.id") as String
tasks.named<Jar>("jar") {
    // The licence ships inside the jar, renamed so it cannot collide with another mod's.
    from(rootProject.file("LICENSE")) { rename { "LICENSE_$modId" } }
}

/**
 * Fails when loader independent code names a mod loader. `src/main/java` and `src/client/java` are
 * compiled against Fabric API on a Fabric node, so the compiler cannot catch a Fabric import there; this
 * can. Route loader calls through platform/Loader and client/platform/ClientLoader instead.
 */
tasks.register("checkLoaderSeam") {
    group = "verification"
    description = "Fails when loader independent sources import a mod loader's API"

    val roots = listOf("src/main/java", "src/client/java").map(rootProject::file).filter(File::isDirectory)
    val forbidden = Regex("""\b(net\.fabricmc|net\.neoforged|net\.minecraftforge)\.""")
    inputs.files(roots.map { fileTree(it) { include("**/*.java") } })

    doLast {
        val offenders = roots.flatMap { root ->
            root.walk().filter { it.extension == "java" }.flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> forbidden.containsMatchIn(line) }
                    .map { (index, line) ->
                        "${file.relativeTo(rootProject.projectDir).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                    }
            }
        }
        check(offenders.isEmpty()) {
            "Loader API in loader independent code. Route it through platform/Loader or " +
                "client/platform/ClientLoader instead:\n" + offenders.joinToString("\n")
        }
    }
}

/**
 * Fails when mod code carries a Stonecutter version conditional outside a `platform/` or `mixin/`
 * package. A `//?` block anywhere else means a seam is missing: put the difference behind
 * platform/Vanilla or client/platform/ClientVanilla. A pure rename belongs in stonecutter.gradle.kts.
 * It reads `src/main/java`, `src/client/java` and every loader directory beside them.
 */
tasks.register("checkVersionSeam") {
    group = "verification"
    description = "Fails when mod sources outside platform/ and mixin/ contain a version conditional"

    val roots = listOf("main", "client").flatMap { set ->
        val dir = rootProject.file("src/$set")
        listOf(dir.resolve("java")) + dir.listFiles().orEmpty()
            .filter { it.isDirectory && it.name != "generated" }.map { it.resolve("java") }
    }.filter(File::isDirectory)
    val allowed = setOf("platform", "mixin")
    inputs.files(roots.map { fileTree(it) { include("**/*.java") } })

    doLast {
        val offenders = roots.flatMap { root ->
            root.walk()
                .filter { it.extension == "java" }
                .filterNot { file -> file.relativeTo(root).invariantSeparatorsPath.split('/').any(allowed::contains) }
                .flatMap { file ->
                    file.readLines().withIndex()
                        .filter { (_, line) -> line.contains("//?") }
                        .map { (index, line) ->
                            "${file.relativeTo(rootProject.projectDir).invariantSeparatorsPath}:${index + 1}: ${line.trim()}"
                        }
                }
        }
        check(offenders.isEmpty()) {
            "Version conditional outside platform/ and mixin/. Put the difference behind platform/Vanilla " +
                "or client/platform/ClientVanilla instead:\n" + offenders.joinToString("\n")
        }
    }
}

/**
 * Fails when a lang file lacks a key `en_us.json` has, or has one it lacks: a missing key shows the raw
 * key to that player. Reads every hand-written `assets/<namespace>/lang` directory under `src/main`.
 */
tasks.register("checkLang") {
    group = "verification"
    description = "Fails when a lang file does not have exactly the keys en_us.json has"

    val langDirs = rootProject.file("src/main").walk()
        .onEnter { it.name != "generated" && it.name != "java" }
        .filter { it.isDirectory && it.name == "lang" && it.parentFile.parentFile.name == "assets" }
        .toList()
    inputs.files(langDirs.map { fileTree(it) { include("*.json") } })

    doLast {
        @Suppress("UNCHECKED_CAST")
        fun keys(file: File): Set<String> = (groovy.json.JsonSlurper().parse(file) as Map<String, Any?>).keys
        val problems = langDirs.flatMap { dir ->
            val english = dir.resolve("en_us.json")
            if (!english.isFile) return@flatMap emptyList<String>()
            val expected = keys(english)
            dir.listFiles().orEmpty().filter { it.extension == "json" && it.name != "en_us.json" }.sortedBy { it.name }
                .flatMap { file ->
                    val actual = keys(file)
                    val path = file.relativeTo(rootProject.projectDir).invariantSeparatorsPath
                    (expected - actual).map { "$path: missing $it" } + (actual - expected).map { "$path: not in en_us: $it" }
                }
        }
        check(problems.isEmpty()) {
            "Every lang file must have exactly the keys en_us.json has:\n" + problems.joinToString("\n")
        }
    }
}

/** Collects the jars every node produces into one directory. */
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds the mod jar and copies it to build/libs/"
    into(rootProject.layout.buildDirectory.dir("libs"))
}

/*
 * The gametests are written against a fresh world and config, which is what CI runs them on. The runner
 * would reopen the world a previous local run left behind, so it goes first. `world` is the Fabric
 * runner's and NeoForge 1.21.1's, `gametestserver` vanilla's own from 1.21.5.
 */
val gametestDir = rootProject.file("run/${project.name}/gametest")
tasks.matching { it.name == "runGametest" }.configureEach {
    doFirst {
        gametestDir.resolve("config").deleteRecursively()
        gametestDir.resolve("world").deleteRecursively()
        gametestDir.resolve("gametestserver").deleteRecursively()
    }
}

// The test server's heap, capped, so a run does not take a quarter of the machine's memory.
// `-PgametestHeap=<size>` raises it.
tasks.withType<JavaExec>().matching { it.name == "runGametest" }.configureEach {
    maxHeapSize = providers.gradleProperty("gametestHeap").getOrElse("1536m")
}

/*
 * `-Ptests=VillageData,Guard` runs only those test classes, named with or without the `GameTest` suffix,
 * case ignored. Both runners find the test classes in the `fabric-gametest` entrypoints of the gametest
 * mod's fabric.mod.json, so the filter drops the others from the built copy of that file. A name that
 * matches no class fails rather than run nothing.
 */
val gametestFilter = providers.gradleProperty("tests").getOrElse("")
    .split(',').map { it.trim().lowercase().removeSuffix("gametest") }.filter { it.isNotEmpty() }
tasks.named<ProcessResources>("processGametestResources") {
    inputs.property("tests", gametestFilter.joinToString(","))
    if (gametestFilter.isNotEmpty()) {
        val manifest = destinationDir.resolve("fabric.mod.json")
        doLast {
            @Suppress("UNCHECKED_CAST")
            val json = groovy.json.JsonSlurper().parse(manifest) as MutableMap<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val entrypoints = json["entrypoints"] as MutableMap<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val classes = entrypoints["fabric-gametest"] as List<String>
            fun key(name: String) = name.substringAfterLast('.').lowercase().removeSuffix("gametest")
            val unknown = gametestFilter.filter { wanted -> classes.none { key(it) == wanted } }
            require(unknown.isEmpty()) {
                "-Ptests names no test class: ${unknown.joinToString()}. The classes are:\n" +
                    classes.joinToString("\n") { it.substringAfterLast('.') }
            }
            entrypoints["fabric-gametest"] = classes.filter { key(it) in gametestFilter }
            manifest.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)))
        }
    }
}

/**
 * Everything CI runs on a node, as one task, so a local `./gradlew :<node>:ci` checks exactly what CI
 * checks and the workflow never lists tasks of its own that could drift from this one.
 */
tasks.register("ci") {
    group = "verification"
    description = "Builds the jar and runs every check and the gametests, as CI does"
    dependsOn(listOf("buildAndCollect", "checkLoaderSeam", "checkVersionSeam", "checkLang", "runGametest") +
        handed<List<String>>("civ.loaderChecks"))
}
// The gametests and the checks read what the build wrote; run them after it so a failure points at them.
tasks.matching { it.name == "runGametest" }.configureEach { mustRunAfter("buildAndCollect") }
