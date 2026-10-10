package com.civillagers.dev.benchmark;

/**
 * What guards are told about players near them while a mob scenario runs. The benchmark runs with nobody
 * joined, so every guard would idle ({@code Guard#playerNear}); the patrol and combat scenarios measure a
 * guard someone is watching, so they answer yes for every guard, and the "nobody near" scenario answers no.
 * Read by the dev mod's {@code CivVillagerMixin}; null, the default, leaves the question to the game. Server
 * thread only.
 */
public final class PlayerPresence {
    private static Boolean forced;

    private PlayerPresence() { }

    /** Answers {@code near} for every guard from now on, or leaves it to the game again with null. */
    static void force(Boolean near) {
        forced = near;
    }

    /** The answer every guard gets, or null for the game's own. */
    public static Boolean forced() {
        return forced;
    }
}
