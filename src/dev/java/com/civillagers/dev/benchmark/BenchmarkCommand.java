package com.civillagers.dev.benchmark;

import com.civillagers.command.CivillagersCommands;
import com.civillagers.platform.Vanilla;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /civillagers benchmark [quick|standard|status|cancel]}, for server owners. */
public final class BenchmarkCommand {
    private BenchmarkCommand() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Merges into the mod's own /civillagers node.
        dispatcher.register(CivillagersCommands.root().then(Commands.literal("benchmark")
                .requires(Vanilla::isOwner)
                .executes(context -> start(context.getSource(), BenchmarkProfile.QUICK))
                .then(profile(BenchmarkProfile.QUICK))
                .then(profile(BenchmarkProfile.STANDARD))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("cancel").executes(context -> cancel(context.getSource())))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> profile(BenchmarkProfile profile) {
        return Commands.literal(profile.name()).executes(context -> start(context.getSource(), profile));
    }

    private static int start(CommandSourceStack source, BenchmarkProfile profile) {
        if (!BenchmarkRunner.start(source, profile)) {
            source.sendFailure(Component.literal("A benchmark is already running: " + BenchmarkRunner.status()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Civillagers benchmark '" + profile.name() + "' started: "
                + profile.describe() + ". /civillagers benchmark status shows progress."), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        boolean running = BenchmarkRunner.isRunning();
        source.sendSuccess(() -> Component.literal(running ? BenchmarkRunner.status() : "No benchmark is running"), false);
        return running ? 1 : 0;
    }

    private static int cancel(CommandSourceStack source) {
        if (!BenchmarkRunner.cancel()) {
            source.sendFailure(Component.literal("No benchmark is running"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Cancelling the benchmark; the partial report follows on the next tick"), false);
        return 1;
    }
}
