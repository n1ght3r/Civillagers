package com.civillagers.entity;

import com.civillagers.Civillagers;
import com.civillagers.entity.guard.Guard;
import com.civillagers.platform.Loader;
import com.civillagers.platform.Vanilla;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/**
 * The mod's entity types and the items that go with them. Each is created inside its registry's
 * registration callback, because NeoForge refuses to create one anywhere else; until then its getter
 * returns null.
 *
 * <p>These statics hold registry entries, which live as long as the game does, never anything from a
 * world.
 */
public final class CivEntities {
    public static final String GUARD = "guard";
    public static final String GUARD_SPAWN_EGG = "guard_spawn_egg";

    private static EntityType<Guard> guard;
    private static Item guardSpawnEgg;

    private CivEntities() { }

    public static EntityType<Guard> guard() {
        return guard;
    }

    public static Item guardSpawnEgg() {
        return guardSpawnEgg;
    }

    /** Called once from {@link Civillagers#initialize()}. */
    public static void register() {
        // Villager-sized, and like villagers in the MISC category, so guards never count against the cap
        // on hostile or passive mobs.
        Loader.onRegister(Registries.ENTITY_TYPE, () -> guard = Registry.register(BuiltInRegistries.ENTITY_TYPE,
                Civillagers.id(GUARD), Vanilla.buildEntityType(EntityType.Builder.of(Guard::new, MobCategory.MISC)
                        .sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10), GUARD)));
        Loader.registerAttributes(CivEntities::guard, Guard::createAttributes);
        // A guard a zombie kills turns, as a villager does, and is cured back into a guard.
        ZombieVillagers.register(CivEntities::guard);

        // Entity types register before items on both loaders, so the type is there for the egg. The egg
        // has a texture of its own on every version; before 1.21.5 vanilla still multiplies a spawn egg's
        // colours over it, so both are white there, which leaves the texture as drawn.
        Loader.onRegister(Registries.ITEM, () -> guardSpawnEgg = Registry.register(BuiltInRegistries.ITEM,
                Civillagers.id(GUARD_SPAWN_EGG),
                Vanilla.spawnEgg(guard, 0xFFFFFF, 0xFFFFFF, Vanilla.itemProperties(GUARD_SPAWN_EGG))));
        Loader.addToCreativeTab(CreativeModeTabs.SPAWN_EGGS, CivEntities::guardSpawnEgg);
    }
}
