package com.civillagers.platform;

import com.civillagers.Civillagers;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.function.Consumer;
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

    /** Whether it is night in {@code level}, the way villagers tell when to go to bed. */
    public static boolean isNight(Level level) {
        // Renamed by 26.1 to say what it measures: the sky's darkness, not the clock.
        //? if >=26.1 {
        return level.isDarkOutside();
        //?} else
        //return level.isNight();
    }

    /**
     * The time of day in {@code level}, 0 to 23999, as villagers keep their schedule: 0 is sunrise, 6000
     * noon. 26.1 reads it from the level's default clock.
     */
    public static int timeOfDay(Level level) {
        //? if >=26.1 {
        return (int) (level.getDefaultClockTime() % 24000L);
        //?} else
        //return (int) (level.getDayTime() % 24000L);
    }

    /**
     * The id of the villager type for the biome at {@code pos}, such as {@code minecraft:plains}: what a
     * villager born there would wear. 1.21.11 moved villager types into a data-driven registry looked up
     * by key.
     */
    public static String villagerTypeAt(LevelReader level, BlockPos pos) {
        //? if >=1.21.11 {
        return net.minecraft.world.entity.npc.villager.VillagerType.byBiome(level.getBiome(pos)).identifier().toString();
        //?} else
        //return net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE.getKey(net.minecraft.world.entity.npc.VillagerType.byBiome(level.getBiome(pos))).toString();
    }

    /** The zombie villager's entity type. By 26.2 vanilla's entity types moved out of {@code EntityType} into {@code EntityTypes}. */
    public static EntityType<ZombieVillager> zombieVillagerType() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.ZOMBIE_VILLAGER;
        //?} else
        //return EntityType.ZOMBIE_VILLAGER;
    }

    /**
     * Turns {@code from} into a new mob of type {@code to} in its place, as a zombie turns a villager: name,
     * equipment and its drop chances carried over, {@code from} removed. {@code after} sets up the new mob.
     * Returns it, or null if it could not be made. 1.21.2 has the conversion take its settings as
     * {@code ConversionParams} and run {@code after} before the new mob joins the level.
     */
    public static <T extends Mob> T convert(Mob from, EntityType<T> to, Consumer<T> after) {
        //? if >=1.21.2 {
        return from.convertTo(to, net.minecraft.world.entity.ConversionParams.single(from, true, true), after::accept);
        //?} else {
        /*T converted = from.convertTo(to, true);
        if (converted != null) after.accept(converted);
        return converted;
        *///?}
    }

    /** The id of the zombie villager's villager type, such as {@code minecraft:snow}, or {@code fallback}. */
    public static String villagerTypeOf(ZombieVillager zombie, String fallback) {
        //? if >=1.21.11 {
        return zombie.getVillagerData().type().unwrapKey().map(key -> key.identifier().toString()).orElse(fallback);
        //?} else {
        /*net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE.getKey(zombie.getVillagerData().getType());
        return id != null ? id.toString() : fallback;
        *///?}
    }

    /** Gives the zombie villager the villager type {@code id}; an id that names no type changes nothing. */
    public static void setVillagerType(ZombieVillager zombie, String id) {
        //? if >=1.21.11 {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) return;
        zombie.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TYPE)
                .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.VILLAGER_TYPE, parsed))
                .ifPresent(type -> zombie.setVillagerData(zombie.getVillagerData().withType(type)));
        //?} else {
        /*Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) return;
        net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE.getOptional(parsed)
                .ifPresent(type -> zombie.setVillagerData(zombie.getVillagerData().setType(type)));
        *///?}
    }

    /** Builds an entity type under {@code path} in the mod's namespace. 1.21.2 keys it by a resource key. */
    public static <T extends Entity> EntityType<T> buildEntityType(EntityType.Builder<T> builder, String path) {
        //? if >=1.21.2 {
        return builder.build(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.ENTITY_TYPE, Civillagers.id(path)));
        //?} else
        //return builder.build(Civillagers.id(path).toString());
    }

    /** The properties of an item registered under {@code path}. 1.21.2 has an item carry its own id. */
    public static Item.Properties itemProperties(String path) {
        //? if >=1.21.2 {
        return new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.ITEM, Civillagers.id(path)));
        //?} else
        //return new Item.Properties();
    }

    /**
     * A spawn egg for {@code type}. Before 1.21.5 an egg is a template tinted with two colours; by 26.1 it
     * is an ordinary item with a texture of its own, and the colours are unused.
     */
    public static Item spawnEgg(EntityType<? extends Mob> type, int background, int highlight, Item.Properties properties) {
        //? if >=26.1 {
        return new SpawnEggItem(properties.spawnEgg(type));
        //?} else
        //return new SpawnEggItem(type, background, highlight, properties);
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
