package com.civillagers.command;

import com.civillagers.Civillagers;
import com.civillagers.ai.GuardPathBudget;
import com.civillagers.ai.guard.GuardTargetGoal;
import com.civillagers.entity.guard.GuardStats;
import com.civillagers.platform.Vanilla;
import com.civillagers.village.Village;
import com.civillagers.village.VillageDetector;
import com.civillagers.village.VillageScheduler;
import com.civillagers.village.VillageStats;
import com.civillagers.village.Villages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;

/**
 * {@code /civillagers}. Today only {@code debug}, for game masters:
 *
 * <ul>
 *   <li>{@code debug stats}: village count, what village work and guards cost per tick, cache sizes;</li>
 *   <li>{@code debug here}: the village at the command's position;</li>
 *   <li>{@code debug scan}: look for villages around the command's position now, rather than waiting
 *       for discovery to come round.</li>
 * </ul>
 *
 * Debug output is plain English on purpose, not translated.
 */
public final class CivillagersCommands {
    private CivillagersCommands() { }

    /** The {@code /civillagers} root, which other parts of the mod (and the dev tools) add to. */
    public static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal(Civillagers.MOD_ID);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root().then(Commands.literal("debug").requires(Vanilla::isGamemaster)
                .then(Commands.literal("stats").executes(CivillagersCommands::stats))
                .then(Commands.literal("here").executes(CivillagersCommands::here))
                .then(Commands.literal("scan").executes(CivillagersCommands::scan))));
    }

    private static int stats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int villages = 0;
        int loaded = 0;
        int levels = 0;
        int lookups = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            Villages data = Villages.get(level);
            if (data.isEmpty()) continue;
            levels++;
            villages += data.size();
            lookups += data.lookupSize();
            for (Village village : data.all()) {
                if (level.hasChunk(village.center().getX() >> 4, village.center().getZ() >> 4)) loaded++;
            }
        }
        String text = String.format(Locale.ROOT, """
                Civillagers: %d villages in %d levels, %d with their center loaded
                Tick: mean %.4f ms, max %.4f ms over the last %d ticks (budget %.2f ms, at most %d updates per tick)
                Since start: %d village updates, %d discovery scans
                Guards: %d ticking, mean %.4f ms, max %.4f ms per tick for all of them; %.3f target scans per guard per %d ticks
                Guard paths: at most %d new walks and %d chases per tick; %d walks and %d chases deferred since start
                Caches: %d runtime lookup entries across levels, stats window %d""",
                villages, levels, loaded,
                VillageStats.meanMillis(), VillageStats.maxMillis(), VillageStats.samples(),
                VillageScheduler.budgetNanos() / 1_000_000.0, VillageScheduler.maxPerTick(),
                VillageStats.updates(), VillageStats.discoveries(),
                GuardStats.lastGuards(), GuardStats.meanMillis(), GuardStats.maxMillis(),
                GuardStats.guardTicks() == 0 ? 0.0
                        : GuardStats.scans() * (double) GuardTargetGoal.SCAN_INTERVAL / GuardStats.guardTicks(),
                GuardTargetGoal.SCAN_INTERVAL,
                GuardPathBudget.PER_TICK, GuardPathBudget.COMBAT_PER_TICK,
                GuardPathBudget.refused(), GuardPathBudget.combatRefused(),
                lookups, VillageStats.WINDOW);
        source.sendSuccess(() -> Component.literal(text), false);
        return villages;
    }

    private static int here(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPos.containing(source.getPosition());
        Village village = Villages.get(source.getLevel()).villageAt(pos);
        if (village == null) {
            source.sendFailure(Component.literal("No village at " + pos.toShortString()));
            return 0;
        }
        String text = String.format(Locale.ROOT,
                "Village #%d: center %s, radius %d, %d bells, %d members, %d guards, %d beds, %d workstations, level %d, founded at tick %d",
                village.id(), village.center().toShortString(), village.radius(), village.anchorCount(),
                village.memberCount(), village.guardCount(), village.beds(), village.workstations(), village.level(), village.founded());
        source.sendSuccess(() -> Component.literal(text), false);
        return village.id();
    }

    private static int scan(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int added = VillageDetector.discover(level, Villages.get(level), BlockPos.containing(source.getPosition()),
                VillageDetector.DISCOVERY_CHUNK_RADIUS, level.getGameTime());
        source.sendSuccess(() -> Component.literal("Added " + added + " bells to villages"), false);
        return added;
    }
}
