package com.civillagers.platform;

/**
 * Every place where Minecraft itself differs between the versions the mod builds for, behind one
 * signature per difference. A Stonecutter {@code //?} block is allowed here and in {@code mixin/}, and
 * nowhere else in the mod; {@code checkVersionSeam} enforces it. A pure rename goes in
 * stonecutter.gradle.kts instead.
 */
public final class Vanilla {
    private Vanilla() { }
}
