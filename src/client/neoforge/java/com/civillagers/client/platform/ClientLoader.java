package com.civillagers.client.platform;

import com.civillagers.Civillagers;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.function.Supplier;

/**
 * Every client call into the mod loader, for NeoForge. The counterpart of the Fabric class of the same
 * name; see {@link com.civillagers.platform.Loader}.
 */
public final class ClientLoader {
    private ClientLoader() { }

    /** Registers a model layer, baked from {@code definition} whenever resources reload. */
    public static void registerModelLayer(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
        modBus().addListener((EntityRenderersEvent.RegisterLayerDefinitions event) ->
                event.registerLayerDefinition(location, definition));
    }

    /** Draws entities of {@code type} with what {@code provider} makes. The type is looked up once registries are filled. */
    public static <E extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends E>> type,
                                                                 EntityRendererProvider<E> provider) {
        modBus().addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(type.get(), provider));
    }

    private static IEventBus modBus() {
        return ModList.get().getModContainerById(Civillagers.MOD_ID).orElseThrow().getEventBus();
    }
}
