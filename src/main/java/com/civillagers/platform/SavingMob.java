package com.civillagers.platform;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * A {@link PathfinderMob} that saves values of its own beside what vanilla saves for every mob. Vanilla's
 * save hooks changed shape in 1.21.6, a {@code CompoundTag} before and {@code ValueOutput} and
 * {@code ValueInput} after, so a mob with data of its own extends this and overrides {@link #saveExtra}
 * and {@link #loadExtra} instead of those hooks.
 */
public abstract class SavingMob extends PathfinderMob {
    protected SavingMob(EntityType<? extends SavingMob> type, Level level) {
        super(type, level);
    }

    /** Writes what the mob keeps of its own. */
    protected abstract void saveExtra(Output output);

    /** Reads back what {@link #saveExtra} wrote; a key never written reads as its fallback. */
    protected abstract void loadExtra(Input input);

    public interface Output {
        void putString(String key, String value);
    }

    public interface Input {
        String getString(String key, String fallback);
    }

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        saveExtra(output::putString);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        loadExtra(input::getStringOr);
    }
    //?} else {
    /*@Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        saveExtra(tag::putString);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        loadExtra((key, fallback) -> tag.contains(key, net.minecraft.nbt.Tag.TAG_STRING) ? tag.getString(key) : fallback);
    }
    *///?}
}
