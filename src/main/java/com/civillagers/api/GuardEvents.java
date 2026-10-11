package com.civillagers.api;

import com.civillagers.entity.guard.Guard;
import net.minecraft.world.entity.Mob;

/** Hooks only guards fire. */
public final class GuardEvents {
    private GuardEvents() { }

    /**
     * Whether a guard goes after a hostile mob it sees, unprovoked. The first listener that answers
     * {@link Verdict#YES} or {@link Verdict#NO} decides; with none, the mod's rule does (neutral mobs no,
     * creepers only with a bow or crossbow). Only hostile mobs ({@code Enemy}) are asked, and this runs in
     * every guard's target scan, so a listener must be cheap and allocate nothing. A guard always fights
     * back against whatever hurts it, whatever this answers.
     */
    public static final Listeners<IsThreat> IS_THREAT = new Listeners<>();

    @FunctionalInterface
    public interface IsThreat {
        /** {@code ranged}: whether the guard holds a bow or crossbow. */
        Verdict isThreat(Guard guard, Mob mob, boolean ranged);
    }
}
