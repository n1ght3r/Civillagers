package com.civillagers.client.platform;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

/**
 * Every client call into the mod loader, for Fabric: entity renderers, model layers, screens, HUD
 * layers. The client half of {@link com.civillagers.platform.Loader}, under the same rules: the NeoForge
 * copy has the same name and signatures.
 */
public final class ClientLoader {
    private ClientLoader() { }

    /** Registers a model layer, baked from {@code definition} whenever resources reload. */
    public static void registerModelLayer(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
        // Fabric API renamed the registry after vanilla's own class in 26.1.
        //? if >=26.1 {
        net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(location, definition::get);
        //?} else
        //net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(location, definition::get);
    }

    /** Draws entities of {@code type} with what {@code provider} makes. The type is looked up once registries are filled. */
    public static <E extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends E>> type,
                                                                 EntityRendererProvider<E> provider) {
        EntityRendererRegistry.register(type.get(), provider);
    }
}
