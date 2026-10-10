package com.civillagers.buildlogic

import java.io.File

/**
 * The JVM arguments `-Pprofile` adds to `runBenchmark`: a JFR recording of the whole run, written to
 * [recording] when the server stops, for JDK Mission Control or `jfr print`. `settings=profile` is the
 * heavier of JFR's two built-in profiles; its execution samples come every 10 ms, too few to say where a
 * guard's 30 microseconds go, so they come every millisecond here. The deep stack lets a sample name the
 * mod's own call site, and DebugNonSafepoints lets a sample land where the code really was. A recorded run
 * is slower than a plain one, which is why the report says so in `environment.profiling`.
 */
fun flightRecorder(recording: File): List<String> = listOf(
    "-XX:StartFlightRecording=filename=${recording.absolutePath},settings=profile,dumponexit=true," +
        "jdk.ExecutionSample#period=1ms",
    "-XX:FlightRecorderOptions=stackdepth=1024",
    "-XX:+UnlockDiagnosticVMOptions",
    "-XX:+DebugNonSafepoints",
)
