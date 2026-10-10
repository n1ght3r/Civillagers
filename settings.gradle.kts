pluginManagement {
    // Plain Kotlin both loader scripts and stonecutter.gradle.kts share: node naming and the like. Data
    // and pure functions only, never Loom or ModDevGradle; see build-logic/settings.gradle.kts. A script
    // puts it on its classpath with `id("civillagers.build-logic")`.
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
    }
}

plugins {
    // One source tree, one jar per node. See the root AGENTS.md.
    id("dev.kikugie.stonecutter") version "0.9.8"
    // 26.1 dropped obfuscation, which changed how Loom consumes mod dependencies. This applies the
    // Loom variant each version needs and keeps `modImplementation` meaning the same thing on both.
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    // NeoForge's build plugin, resolved here and applied only by build.neoforge.gradle.kts: applying it
    // to a Fabric node would put a second Minecraft provider on a project Loom already owns.
    id("net.neoforged.moddev") version "2.0.148" apply false
    // Provisions the JDK a node needs when it is not installed: 26.1+ wants Java 25, 1.21.x Java 21.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        version("1.21.1", "1.21.1")
        version("1.21.1-neoforge", "1.21.1").buildscript = "build.neoforge.gradle.kts"
        version("26.2.x", "26.2")
        version("26.3.x", "26.3")
        // A NeoForge node for 26.x is one line each, plus its `[neoforge."<node>"]` table:
        // version("26.3.x-neoforge", "26.3").buildscript = "build.neoforge.gradle.kts"
        vcsVersion = "26.3.x"
    }
}

rootProject.name = "Civillagers"
