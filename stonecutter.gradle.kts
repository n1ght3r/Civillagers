import com.civillagers.buildlogic.Loader
import com.civillagers.buildlogic.loaderOf
import com.civillagers.buildlogic.minecraftOf

plugins {
    id("dev.kikugie.stonecutter")
    // loaderOf and minecraftOf, below. See settings.gradle.kts.
    id("civillagers.build-logic")
}

// The node the source tree is currently checked out for. Switch it with
// `./gradlew "Set active project to <name>"`; `chiseledBuild` and CI build every node regardless.
stonecutter active "26.3.x"

stonecutter parameters {
    // Every node is `<version>` on Fabric or `<version>-<loader>` on another loader. Tagging both parts
    // lets stonecutter.properties.toml keep what the loaders share in `["1.21.1"]` and the rest in
    // `[fabric."1.21.1"]` or `[neoforge."1.21.1"]`; see the top of that file.
    properties {
        tags(minecraftOf(current.project), loaderOf(current.project).id)
    }

    // Bakes the target Minecraft version into the jar, so the startup log line is never stale.
    swaps["minecraft"] = "\"${node.metadata.version}\";"

    // Every replacement also runs backwards: on each node whose condition is false, and on the sources
    // themselves when "Reset active project" brings them back from another node. So what a rule writes
    // must be the same on every node it applies to, and must not occur in the sources for any other
    // reason. `git diff` after "Set active project" and "Reset active project" shows a round trip.
    //
    // A rename with no change of meaning belongs here. A real difference in behaviour belongs behind
    // platform/Loader or platform/Vanilla, where a `//?` block is allowed; checkVersionSeam enforces it.
    replacements {
        // 1.21.11 renamed ResourceLocation to Identifier, ResourceKey#location to #identifier, and moved
        // Util into net.minecraft.util, changing nothing else about any of them.
        string(current.parsed < "1.21.11") {
            replace("Identifier", "ResourceLocation")
            replace(".identifier()", ".location()")
            replace("net.minecraft.util.Util", "net.minecraft.Util")
            // 1.21.11 also sorted the entity classes into packages by kind, villagers into npc.villager.
            replace("import net.minecraft.world.entity.npc.villager.Villager;",
                    "import net.minecraft.world.entity.npc.Villager;")
        }
        string(current.parsed >= "1.21" && current.parsed < "1.21.11") {
            replace("Identifier.fromNamespaceAndPath(", "ResourceLocation.fromNamespaceAndPath(")
            replace("Identifier.withDefaultNamespace(", "ResourceLocation.withDefaultNamespace(")
            replace("Identifier.parse(", "ResourceLocation.parse(")
        }

        // The Fabric data generation API renamed both of these for 26.1 without changing what they do,
        // so the datagen providers name the newer pair and get the older one here.
        string(current.parsed < "26.1") {
            replace("FabricPackOutput", "FabricDataOutput")
            replace("FabricTagsProvider", "FabricTagProvider")
        }

        // The NeoForge nodes run the same tests through a harness of their own, which reads a @GameTest
        // annotation from src/gametest/neoforge instead of Fabric API's. Only the import changes, so a
        // test keeps writing `@GameTest` with no arguments. See src/gametest/java/AGENTS.md.
        val loader = loaderOf(current.project)
        Loader.entries.filter { it != Loader.FABRIC }.forEach { harness ->
            string(loader == harness) {
                replace("import net.fabricmc.fabric.api.gametest.v1.GameTest;",
                        "import com.civillagers.gametest.${harness.id}.GameTest;")
            }
        }

        // Fabric API's own @GameTest arrived with 1.21.5. Before it a test uses vanilla's annotation and
        // names Fabric's empty structure as its template; no test body changes.
        string(loader == Loader.FABRIC && current.parsed < "1.21.5") {
            replace("import net.fabricmc.fabric.api.gametest.v1.GameTest;",
                    "import net.minecraft.gametest.framework.GameTest;")
            replace("@GameTest",
                    "@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)")
        }
    }
}
