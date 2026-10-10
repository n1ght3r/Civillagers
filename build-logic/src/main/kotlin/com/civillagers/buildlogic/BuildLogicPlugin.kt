package com.civillagers.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Does nothing when applied. `id("civillagers.build-logic")` in a script's `plugins { }` is how that
 * script gets [Loader], [loaderOf] and [requiredJava] on its classpath.
 */
class BuildLogicPlugin : Plugin<Project> {
    override fun apply(target: Project) { }
}
