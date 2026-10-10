plugins {
    // Gradle's own Kotlin, so the classes here always match the Kotlin the build scripts are compiled
    // with. No Kotlin version is named anywhere in this build.
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        // Applying it does nothing. It exists so that a build script can put the classes of this build
        // on its classpath with one line in `plugins { }`.
        register("buildLogic") {
            id = "civillagers.build-logic"
            implementationClass = "com.civillagers.buildlogic.BuildLogicPlugin"
        }
    }
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useKotlinTest(embeddedKotlinVersion)
        }
    }
}
