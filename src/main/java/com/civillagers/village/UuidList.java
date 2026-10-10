package com.civillagers.village;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * UUIDs packed two {@code long}s each into one array, in the order they were added, without repeats, up to
 * a cap. A village keeps its villagers and guards this way: a {@code UUID} object in a hash set costs
 * about three times the sixteen bytes it holds, and lookups here run on rare events over at most a hundred
 * or so entries, where a linear scan is as fast as a hash.
 */
final class UuidList {
    private static final long[] EMPTY = new long[0];

    private final int cap;
    private long[] bits = EMPTY;
    private int size;

    UuidList(int cap) {
        this.cap = cap;
    }

    int size() {
        return size;
    }

    boolean contains(UUID id) {
        return indexOf(id) >= 0;
    }

    /** Adds {@code id} at the end, unless it is already here or the list is full. Returns whether it was added. */
    boolean add(UUID id) {
        if (size >= cap || contains(id)) return false;
        if (2 * size == bits.length) {
            int grown = Math.min(cap, Math.max(4, size + (size >> 1)));
            long[] next = new long[2 * grown];
            System.arraycopy(bits, 0, next, 0, 2 * size);
            bits = next;
        }
        bits[2 * size] = id.getMostSignificantBits();
        bits[2 * size + 1] = id.getLeastSignificantBits();
        size++;
        return true;
    }

    boolean remove(UUID id) {
        int index = indexOf(id);
        if (index < 0) return false;
        System.arraycopy(bits, 2 * index + 2, bits, 2 * index, 2 * (size - index - 1));
        size--;
        return true;
    }

    void clear() {
        bits = EMPTY;
        size = 0;
    }

    UUID get(int index) {
        return new UUID(bits[2 * index], bits[2 * index + 1]);
    }

    /** Whether this holds exactly the UUIDs in {@code other}, whatever their order. */
    boolean sameAs(Collection<UUID> other) {
        if (other.size() != size) return false;
        for (UUID id : other) {
            if (!contains(id)) return false;
        }
        return true;
    }

    /** A copy, in order. Allocates: for saving and for commands, never for a tick. */
    List<UUID> toList() {
        List<UUID> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) list.add(get(i));
        return Collections.unmodifiableList(list);
    }

    private int indexOf(UUID id) {
        long most = id.getMostSignificantBits();
        long least = id.getLeastSignificantBits();
        for (int i = 0; i < size; i++) {
            if (bits[2 * i] == most && bits[2 * i + 1] == least) return i;
        }
        return -1;
    }
}
