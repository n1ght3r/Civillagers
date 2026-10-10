package com.civillagers.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals

class NodesTest {
    @Test
    fun `a node without a suffix is Fabric`() {
        assertEquals(Loader.FABRIC, loaderOf("26.3.x"))
        assertEquals("26.3.x", minecraftOf("26.3.x"))
    }

    @Test
    fun `the suffix names the loader and is not part of the Minecraft version`() {
        assertEquals(Loader.NEOFORGE, loaderOf("1.21.1-neoforge"))
        assertEquals("1.21.1", minecraftOf("1.21.1-neoforge"))
    }

    @Test
    fun `the Java version follows the Minecraft version`() {
        val versions = listOf("1.20.1", "1.20.5", "1.21.1", "26.1", "26.3")
        fun on(version: String) = requiredJava { versions.indexOf(version) >= versions.indexOf(it) }
        assertEquals(17, on("1.20.1"))
        assertEquals(21, on("1.21.1"))
        assertEquals(25, on("26.3"))
    }
}
