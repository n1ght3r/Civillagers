/**
 * What the mod's villagers do, as {@code Goal}s on a {@code GoalSelector}, cheap and easy to control. The
 * goals every villager of the mod shares are in {@link com.civillagers.ai.villager}, a guard's own in
 * {@link com.civillagers.ai.guard}; what both lean on is here: the per-tick path budget
 * ({@link com.civillagers.ai.GuardPathBudget}), beds ({@link com.civillagers.ai.Homes}) and where a
 * villager may stand ({@link com.civillagers.ai.Ground}). Every goal keeps to the performance rules in
 * docs/ROADMAP.md; see AGENTS.md here.
 */
package com.civillagers.ai;
