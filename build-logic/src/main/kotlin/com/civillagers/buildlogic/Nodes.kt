package com.civillagers.buildlogic

/** A mod loader a node builds for, by the name its source directories use: `src/main/<id>`. */
enum class Loader(val id: String) {
    FABRIC("fabric"),
    NEOFORGE("neoforge"),
    ;

    /** What follows the Minecraft version in the name of a node of this loader: nothing on Fabric. */
    val nodeSuffix: String get() = if (this == FABRIC) "" else "-$id"
}

/**
 * The loader a node builds for, from its name: `1.21.1-neoforge` is NeoForge, and a node with no loader
 * in its name is Fabric. Every script that needs a node's loader asks this, or `tools/node_names.py`.
 */
fun loaderOf(node: String): Loader =
    Loader.entries.firstOrNull { it != Loader.FABRIC && node.endsWith(it.nodeSuffix) } ?: Loader.FABRIC

/**
 * A node's name without its loader: `1.21.1` for `1.21.1-neoforge`. It names the table in
 * stonecutter.properties.toml that every loader of that Minecraft version shares.
 */
fun minecraftOf(node: String): String = node.removeSuffix(loaderOf(node).nodeSuffix)

/**
 * The Java version a Minecraft version runs on: 25 from 26.1, 21 from 1.20.5. Both loader scripts ask
 * this rather than each keeping its own `when`, which is how the two used to drift apart.
 *
 * @param atLeast Stonecutter's comparison for the node, `{ sc.current.parsed >= it }`
 */
fun requiredJava(atLeast: (String) -> Boolean): Int = when {
    atLeast("26.1") -> 25
    atLeast("1.20.5") -> 21
    else -> 17
}
