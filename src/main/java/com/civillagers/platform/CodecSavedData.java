package com.civillagers.platform;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Saved data written with a {@link Codec}, on every version. From 1.21.5 the storage writes it with the
 * codec in its {@link Vanilla.DataKind}; before that {@code SavedData} wrote itself to NBT, which this
 * does with the same codec, so a subclass never knows the difference.
 */
public abstract class CodecSavedData<T extends CodecSavedData<T>> extends SavedData {
    /** The codec this data is read and written with; the same one its {@link Vanilla.DataKind} holds. */
    protected abstract Codec<T> codec();

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }

    //? if <1.21.5 {
    /*@Override
    public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        if (Vanilla.encode(codec(), self()) instanceof net.minecraft.nbt.CompoundTag encoded) tag.merge(encoded);
        return tag;
    }
    *///?}
}
