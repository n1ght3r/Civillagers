package com.civillagers.client.platform;

import com.civillagers.Civillagers;
import com.civillagers.client.render.GuardModelLayers;
import com.civillagers.entity.Guard;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/**
 * Draws a guard: its {@link GuardModel}, the clothes of its villager type, the weapon or shield in its
 * hands and the armor it wears, the way a player's are drawn. {@link HumanoidMobRenderer} already adds the
 * held items, a worn head and elytra; this adds the clothes, then the armor, with the helmet stretched
 * over the taller villager head.
 *
 * <p>One class per side of 1.21.2's render states; see {@link ClientVanilla}. The armor models differ
 * too: one model per armor slot from 1.21.2, an inner and an outer layer before.
 */
//? if >=1.21.2 {
public final class GuardRenderer extends HumanoidMobRenderer<Guard, GuardRenderer.State, GuardModel> {
    private static final net.minecraft.client.renderer.entity.ArmorModelSet<ModelLayerLocation> ARMOR =
            new net.minecraft.client.renderer.entity.ArmorModelSet<>(armorLayer("helmet"), armorLayer("chestplate"),
                    armorLayer("leggings"), armorLayer("boots"));
    private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {
            net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET,
    };

    public GuardRenderer(EntityRendererProvider.Context context) {
        super(context, new GuardModel(context.bakeLayer(GuardModelLayers.BODY)), 0.5F);
        addLayer(new Clothes(this));
        addLayer(new HumanoidArmorLayer<>(this,
                net.minecraft.client.renderer.entity.ArmorModelSet.<HumanoidModel<State>>bake(
                        ARMOR, context.getModelSet(), HumanoidModel::new),
                context.getEquipmentRenderer()));
    }

    /** Registers the guard's model layers: its body, and an armor model per slot with a stretched helmet. */
    public static void registerLayers() {
        ClientLoader.registerModelLayer(GuardModelLayers.BODY, GuardModelLayers::createBodyLayer);
        // Vanilla's own armor deformations, from LayerDefinitions.
        net.minecraft.client.renderer.entity.ArmorModelSet<net.minecraft.client.model.geom.builders.MeshDefinition> meshes =
                HumanoidModel.createArmorMeshSet(new CubeDeformation(0.5F), new CubeDeformation(1.0F));
        net.minecraft.client.model.geom.builders.PartDefinition root = meshes.head().getRoot();
        root.addOrReplaceChild("head", root.getChild("head").transformed(pose -> new net.minecraft.client.model.geom.PartPose(
                pose.x(), pose.y(), pose.z(), pose.xRot(), pose.yRot(), pose.zRot(),
                pose.xScale(), pose.yScale() * GuardModelLayers.HELMET_STRETCH, pose.zScale())));
        net.minecraft.client.renderer.entity.ArmorModelSet<LayerDefinition> layers =
                meshes.map(mesh -> LayerDefinition.create(mesh, 64, 32));
        for (net.minecraft.world.entity.EquipmentSlot slot : ARMOR_SLOTS) {
            ClientLoader.registerModelLayer(ARMOR.get(slot), () -> layers.get(slot));
        }
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Guard guard, State state, float partialTick) {
        super.extractRenderState(guard, state, partialTick);
        state.clothes = GuardModelLayers.clothes(guard.villagerType());
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return GuardModelLayers.TEXTURE;
    }

    @Override
    protected HumanoidModel.ArmPose getArmPose(Guard guard, net.minecraft.world.entity.HumanoidArm arm) {
        HumanoidModel.ArmPose vanilla = super.getArmPose(guard, arm);
        return vanilla != HumanoidModel.ArmPose.EMPTY ? vanilla : GuardModelLayers.armPose(guard, arm);
    }

    private static ModelLayerLocation armorLayer(String slot) {
        return new ModelLayerLocation(Civillagers.id("guard"), slot);
    }

    /** What a guard's model is drawn from: a humanoid's, and the clothes it wears. */
    public static final class State extends net.minecraft.client.renderer.entity.state.HumanoidRenderState {
        public Identifier clothes = GuardModelLayers.clothes(Guard.DEFAULT_VILLAGER_TYPE);
    }

    /** The clothes of the guard's villager type, drawn over the body on the same model, as a villager's are. */
    private static final class Clothes extends RenderLayer<State, GuardModel> {
        Clothes(RenderLayerParent<State, GuardModel> parent) {
            super(parent);
        }

        @Override
        public void submit(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
                           int light, State state, float yRot, float xRot) {
            if (state.isInvisible) return;
            renderColoredCutoutModel(getParentModel(), state.clothes, poseStack, collector, light, state, -1, 1);
        }
    }
}
//?} else {
/*public final class GuardRenderer extends HumanoidMobRenderer<Guard, GuardModel> {
    private static final ModelLayerLocation INNER_ARMOR = new ModelLayerLocation(Civillagers.id("guard"), "inner_armor");
    private static final ModelLayerLocation OUTER_ARMOR = new ModelLayerLocation(Civillagers.id("guard"), "outer_armor");

    public GuardRenderer(EntityRendererProvider.Context context) {
        super(context, new GuardModel(context.bakeLayer(GuardModelLayers.BODY)), 0.5F);
        addLayer(new Clothes(this));
        addLayer(new HumanoidArmorLayer<>(this, new ArmorModel(context.bakeLayer(INNER_ARMOR)),
                new ArmorModel(context.bakeLayer(OUTER_ARMOR)), context.getModelManager()));
    }

    /^* Registers the guard's model layers: its body, and the inner and outer armor layers. ^/
    public static void registerLayers() {
        ClientLoader.registerModelLayer(GuardModelLayers.BODY, GuardModelLayers::createBodyLayer);
        // Vanilla's own armor deformations, from LayerDefinitions.
        ClientLoader.registerModelLayer(INNER_ARMOR,
                () -> LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F), 64, 32));
        ClientLoader.registerModelLayer(OUTER_ARMOR,
                () -> LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1.0F), 0.0F), 64, 32));
    }

    @Override
    public Identifier getTextureLocation(Guard guard) {
        return GuardModelLayers.TEXTURE;
    }

    /^*
     * An armor model whose helmet is stretched over the taller villager head. The stretch goes on just
     * before drawing: the armor layer copies the guard model's pose, scale included, onto it first.
     ^/
    private static final class ArmorModel extends HumanoidModel<Guard> {
        ArmorModel(ModelPart root) {
            super(root);
        }

        @Override
        public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                                   int light, int overlay, int color) {
            head.yScale = GuardModelLayers.HELMET_STRETCH;
            hat.yScale = GuardModelLayers.HELMET_STRETCH;
            super.renderToBuffer(poseStack, buffer, light, overlay, color);
        }
    }

    /^* The clothes of the guard's villager type, drawn over the body on the same model, as a villager's are. ^/
    private static final class Clothes extends RenderLayer<Guard, GuardModel> {
        Clothes(RenderLayerParent<Guard, GuardModel> parent) {
            super(parent);
        }

        @Override
        public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer,
                           int light, Guard guard, float limbSwing, float limbSwingAmount, float partialTick,
                           float ageInTicks, float netHeadYaw, float headPitch) {
            if (guard.isInvisible()) return;
            renderColoredCutoutModel(getParentModel(), GuardModelLayers.clothes(guard.villagerType()), poseStack, buffer,
                    light, guard, -1);
        }
    }
}
*///?}
