package com.civillagers.gametest.neoforge;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a gametest method on the NeoForge nodes, in place of Fabric API's annotation of the same name.
 * {@code stonecutter.gradle.kts} swaps the import on these nodes only, so a test file never names this
 * class itself. It takes no arguments on purpose: every test runs with Fabric API's defaults, which
 * {@link CivillagersGameTests} copies.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
}
