/**
 * What the mod's entities do. Guards run on a {@code GoalSelector}, which is cheap and easy to control:
 * patrolling, sleeping, defending against raids, picking and fighting targets. Each goal reads the village
 * from {@link com.civillagers.village} and keeps to the performance rules in docs/ROADMAP.md: scans are
 * rate-limited and new unhurried paths share a per-tick budget, {@link com.civillagers.ai.GuardPathBudget}.
 */
package com.civillagers.ai;
