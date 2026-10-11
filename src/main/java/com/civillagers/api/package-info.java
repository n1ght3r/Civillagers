/**
 * What addons build on. Everything here is meant to be used from another mod and changes only with a note
 * in the changelog; the rest of the mod may change in any release.
 *
 * <ul>
 *   <li>{@link com.civillagers.api.CivVillagerEvents}: hooks every villager of the mod fires (goals,
 *       turning into a zombie, being cured).</li>
 *   <li>{@link com.civillagers.api.GuardEvents}: hooks only guards fire (what they go after).</li>
 *   <li>{@link com.civillagers.api.CivTags}: tags a datapack or another mod adds to, such as the weapons a
 *       guard takes.</li>
 *   <li>{@link com.civillagers.api.CivillagersApi}: registrations, such as a mob of an addon's own that
 *       turns into a zombie villager and is cured back.</li>
 * </ul>
 *
 * <p>Beside this package, these classes are public and kept stable for addons: {@link
 * com.civillagers.entity.CivVillager} (extend it for a villager of an addon's own) and the public methods
 * of {@link com.civillagers.entity.guard.Guard}. Register listeners from the addon's own initializer, on either
 * loader; nothing here names one. See docs/knowledge/addon-api.md.
 */
package com.civillagers.api;
