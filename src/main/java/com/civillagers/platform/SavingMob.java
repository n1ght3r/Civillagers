package com.civillagers.platform;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * A {@link PathfinderMob} that saves values of its own beside what vanilla saves for every mob. Vanilla's
 * save hooks changed shape in 1.21.6, a {@code CompoundTag} before and {@code ValueOutput} and
 * {@code ValueInput} after, so a mob with data of its own extends this and overrides {@link #saveExtra}
 * and {@link #loadExtra} instead of those hooks, calling {@code super} first so every class in between
 * keeps its own.
 */
public abstract class SavingMob extends PathfinderMob {
    protected SavingMob(EntityType<? extends SavingMob> type, Level level) {
        super(type, level);
    }

    /** Writes what the mob keeps of its own. Nothing here. */
    protected void saveExtra(Output output) {
    }

    /** Reads back what {@link #saveExtra} wrote; a key never written reads as its fallback. Nothing here. */
    protected void loadExtra(Input input) {
    }

    /** Where {@link #saveExtra} writes. A value with a codec is written with the level's registries, so items keep their components. */
    public interface Output {
        void putString(String key, String value);

        void putInt(String key, int value);

        void putLong(String key, long value);

        <T> void store(String key, Codec<T> codec, T value);
    }

    /** Where {@link #loadExtra} reads; a key never written gives the fallback, or an empty optional. */
    public interface Input {
        String getString(String key, String fallback);

        int getInt(String key, int fallback);

        long getLong(String key, long fallback);

        <T> Optional<T> read(String key, Codec<T> codec);
    }

    /**
     * The chance the item in {@code slot} drops when the mob dies: 0 to 1, or above 1 for an item that
     * always drops undamaged, as a player's gear given to a mob does. 1.21.5 keeps them in a record.
     */
    protected float dropChance(EquipmentSlot slot) {
        //? if >=1.21.5 {
        return getDropChances().byEquipment(slot);
        //?} else
        //return getEquipmentDropChance(slot);
    }

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        saveExtra(new Output() {
            @Override
            public void putString(String key, String value) {
                output.putString(key, value);
            }

            @Override
            public void putInt(String key, int value) {
                output.putInt(key, value);
            }

            @Override
            public void putLong(String key, long value) {
                output.putLong(key, value);
            }

            @Override
            public <T> void store(String key, Codec<T> codec, T value) {
                output.store(key, codec, value);
            }
        });
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        loadExtra(new Input() {
            @Override
            public String getString(String key, String fallback) {
                return input.getStringOr(key, fallback);
            }

            @Override
            public int getInt(String key, int fallback) {
                return input.getIntOr(key, fallback);
            }

            @Override
            public long getLong(String key, long fallback) {
                return input.getLongOr(key, fallback);
            }

            @Override
            public <T> Optional<T> read(String key, Codec<T> codec) {
                return input.read(key, codec);
            }
        });
    }
    //?} else {
    /*@Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops = registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        saveExtra(new Output() {
            @Override
            public void putString(String key, String value) {
                tag.putString(key, value);
            }

            @Override
            public void putInt(String key, int value) {
                tag.putInt(key, value);
            }

            @Override
            public void putLong(String key, long value) {
                tag.putLong(key, value);
            }

            @Override
            public <T> void store(String key, Codec<T> codec, T value) {
                codec.encodeStart(ops, value)
                        .resultOrPartial(error -> com.civillagers.Civillagers.LOGGER.error("Cannot save {}: {}", key, error))
                        .ifPresent(encoded -> tag.put(key, encoded));
            }
        });
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops = registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        loadExtra(new Input() {
            @Override
            public String getString(String key, String fallback) {
                return tag.contains(key, net.minecraft.nbt.Tag.TAG_STRING) ? tag.getString(key) : fallback;
            }

            @Override
            public int getInt(String key, int fallback) {
                return tag.contains(key, net.minecraft.nbt.Tag.TAG_INT) ? tag.getInt(key) : fallback;
            }

            @Override
            public long getLong(String key, long fallback) {
                return tag.contains(key, net.minecraft.nbt.Tag.TAG_LONG) ? tag.getLong(key) : fallback;
            }

            @Override
            public <T> Optional<T> read(String key, Codec<T> codec) {
                if (!tag.contains(key)) return Optional.empty();
                return codec.parse(ops, tag.get(key))
                        .resultOrPartial(error -> com.civillagers.Civillagers.LOGGER.error("Cannot read {}: {}", key, error));
            }
        });
    }
    *///?}
}
