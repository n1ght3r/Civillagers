package com.civillagers.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The listeners of one hook, in the order they were registered. Register from a mod's initializer, while
 * the game loads; the mod reads them on the server thread, by index, so firing a hook allocates nothing.
 *
 * @param <L> the listener's functional interface
 */
public final class Listeners<L> {
    private final List<L> listeners = new ArrayList<>();

    Listeners() { }

    /** Adds {@code listener}, after those already registered. */
    public synchronized void register(L listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** How many listeners are registered. */
    public int size() {
        return listeners.size();
    }

    /** The listener at {@code index}, in registration order. */
    public L get(int index) {
        return listeners.get(index);
    }
}
