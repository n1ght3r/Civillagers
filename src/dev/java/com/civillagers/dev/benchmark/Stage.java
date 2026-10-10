package com.civillagers.dev.benchmark;

/**
 * One step of a benchmark run. The run calls {@link #run} at most once per server tick per stage, and a
 * stage only does as much as fits before the deadline, so a long run never holds a tick long enough to
 * trip the server watchdog. A stage that measures real ticks returns at once and is called again on the
 * next one.
 */
interface Stage {
    String name();

    /** Works until {@code deadlineNanos} on the {@link System#nanoTime()} clock at the latest; true once complete. */
    boolean run(long deadlineNanos);

    /** A short progress note for {@code /civillagers benchmark status}. */
    default String progress() {
        return "";
    }

    static Stage once(String name, Runnable action) {
        return new Stage() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean run(long deadlineNanos) {
                action.run();
                return true;
            }
        };
    }

    /** Lets the server tick undisturbed, for example so the points of interest of new blocks register. */
    static Stage idle(String name, int ticks) {
        return new Stage() {
            private int waited;

            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean run(long deadlineNanos) {
                return ++waited > ticks;
            }

            @Override
            public String progress() {
                return waited + "/" + ticks + " ticks";
            }
        };
    }
}
