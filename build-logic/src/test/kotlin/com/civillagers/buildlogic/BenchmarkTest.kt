package com.civillagers.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BenchmarkTest {
    @Test
    fun `the recording goes to the file it is given, written when the server stops`() {
        val file = File("run/26.3.x/benchmark/latest.jfr")
        val start = flightRecorder(file).single { it.startsWith("-XX:StartFlightRecording=") }
        assertTrue(start.contains("filename=${file.absolutePath}"))
        assertTrue(start.contains("dumponexit=true"))
    }
}
