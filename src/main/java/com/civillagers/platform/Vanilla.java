package com.civillagers.platform;

import com.civillagers.Civillagers;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.function.Supplier;

/**
 * Every place where Minecraft itself differs between the versions the mod builds for, behind one
 * signature per difference. A Stonecutter {@code //?} block is allowed here and in {@code mixin/}, and
 * nowhere else in the mod; {@code checkVersionSeam} enforces it. A pure rename goes in
 * stonecutter.gradle.kts instead.
 */
public final class Vanilla {
    private Vanilla() { }

    /** Whether a command source may run game master commands: permission level 2. 1.21.11 typed permissions. */
    public static boolean isGamemaster(CommandSourceStack source) {
        //? if >=1.21.11 {
        return net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(source.permissions());
        //?} else
        //return source.hasPermission(2);
    }

    /** Whether a command source may run owner-only commands: permission level 4. */
    public static boolean isOwner(CommandSourceStack source) {
        //? if >=1.21.11 {
        return net.minecraft.commands.Commands.LEVEL_OWNERS.check(source.permissions());
        //?} else
        //return source.hasPermission(4);
    }

    /**
     * One kind of per-level saved data: its file name, how to make an empty one and how to read and write
     * it. Make one per kind and keep it in a static final: 1.21.5+ looks saved data up by this key.
     *
     * <p>1.21.5 replaced {@code SavedData.Factory} and a separate file name with {@code SavedDataType},
     * which carries both and an id in a namespace.
     */
    public static final class DataKind<T extends SavedData> {
        //? if >=1.21.5 {
        private final net.minecraft.world.level.saveddata.SavedDataType<T> type;
        //?} else {
        /*private final SavedData.Factory<T> factory;
        private final String fileName;
        *///?}

        /** {@code path} names the file, in the mod's namespace. */
        public DataKind(String path, Supplier<T> create, Codec<T> codec) {
            //? if >=1.21.5 {
            type = new net.minecraft.world.level.saveddata.SavedDataType<>(Civillagers.id(path), create, codec, null);
            //?} else {
            /*fileName = Civillagers.MOD_ID + "_" + path;
            factory = new SavedData.Factory<>(create,
                    (tag, registries) -> codec.parse(NbtOps.INSTANCE, tag)
                            .resultOrPartial(error -> Civillagers.LOGGER.error("Cannot read {}: {}", fileName, error))
                            .orElseGet(create),
                    null);
            *///?}
        }
    }

    /** The level's saved data of this kind, read from disk or created empty on first use. */
    public static <T extends SavedData> T savedData(ServerLevel level, DataKind<T> kind) {
        //? if >=1.21.5 {
        return level.getDataStorage().computeIfAbsent(kind.type);
        //?} else
        //return level.getDataStorage().computeIfAbsent(kind.factory, kind.fileName);
    }

    /**
     * Encodes {@code value} to NBT, for the versions where {@code SavedData} writes itself. Unused from
     * 1.21.5, where the storage writes with the codec in {@link DataKind}.
     */
    static <T> net.minecraft.nbt.Tag encode(Codec<T> codec, T value) {
        return codec.encodeStart(NbtOps.INSTANCE, value)
                .resultOrPartial(error -> Civillagers.LOGGER.error("Cannot write saved data: {}", error))
                .orElseGet(net.minecraft.nbt.CompoundTag::new);
    }
}
