package com.civillagers.client.platform;

import com.civillagers.Civillagers;
import com.civillagers.client.render.CivVillagerModelLayers;
import com.civillagers.entity.CivVillager;
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
 * Draws any villager of the mod, the guard today: its {@link CivVillagerModel}, the clothes of its villager
 * type, the weapon or shield in its hands and the armor it wears, the way a player's are drawn.
 * {@link HumanoidMobRenderer} already adds the held items, a worn head and elytra; this adds the clothes,
 * then the armor, with the helmet stretched over the taller villager head. A new role, or an addon's
 * villager, registers it as is ({@code CivVillagerRenderer::new}) or extends it to add layers of its own.
 *
 * <p>One class per side of 1.21.2's render states; see {@link ClientVanilla}. The armor models differ
 * too: one model per armor slot from 1.21.2, an inner and an outer layer before.
 */
//? if >=1.21.2 {
public class CivVillagerRenderer<T extends CivVillager> extends HumanoidMobRenderer<T, CivVillagerRenderer.State, CivVillagerModel> {
    private static final net.minecraft.client.renderer.entity.ArmorModelSet<ModelLayerLocation> ARMOR =
            new net.minecraft.client.renderer.entity.ArmorModelSet<>(armorLayer("helmet"), armorLayer("chestplate"),
                    armorLayer("leggings"), armorLayer("boots"));
    private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {
            net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET,
    };

    public CivVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new CivVillagerModel(context.bakeLayer(CivVillagerModelLayers.BODY)), 0.5F);
        addLayer(new Clothes(this));
        addLayer(new HumanoidArmorLayer<>(this,
                net.minecraft.client.renderer.entity.ArmorModelSet.<HumanoidModel<State>>bake(
                        ARMOR, context.getModelSet(), HumanoidModel::new),
                context.getEquipmentRenderer()));
    }

    /** Registers the model layers every villager of the mod shares: its body, and an armor model per slot with a stretched helmet. */
    public static void registerLayers() {
        ClientLoader.registerModelLayer(CivVillagerModelLayers.BODY, CivVillagerModelLayers::createBodyLayer);
        // Vanilla's own armor deformations, from LayerDefinitions.
        net.minecraft.client.renderer.entity.ArmorModelSet<net.minecraft.client.model.geom.builders.MeshDefinition> meshes =
                HumanoidModel.createArmorMeshSet(new CubeDeformation(0.5F), new CubeDeformation(1.0F));
        net.minecraft.client.model.geom.builders.PartDefinition root = meshes.head().getRoot();
        root.addOrReplaceChild("head", root.getChild("head").transformed(pose -> new net.minecraft.client.model.geom.PartPose(
                pose.x(), pose.y(), pose.z(), pose.xRot(), pose.yRot(), pose.zRot(),
                pose.xScale(), pose.yScale() * CivVillagerModelLayers.HELMET_STRETCH, pose.zScale())));
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
    public void extractRenderState(T villager, State state, float partialTick) {
        super.extractRenderState(villager, state, partialTick);
        state.clothes = CivVillagerModelLayers.clothes(villager.villagerType());
        state.unhappy = villager.isUnhappy();
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return CivVillagerModelLayers.TEXTURE;
    }

    @Override
    protected HumanoidModel.ArmPose getArmPose(T villager, net.minecraft.world.entity.HumanoidArm arm) {
        HumanoidModel.ArmPose vanilla = super.getArmPose(villager, arm);
        return vanilla != HumanoidModel.ArmPose.EMPTY ? vanilla : CivVillagerModelLayers.armPose(villager, arm);
    }

    private static ModelLayerLocation armorLayer(String slot) {
        return new ModelLayerLocation(Civillagers.id("villager"), slot);
    }

    /** What a villager's model is drawn from: a humanoid's, the clothes it wears, and whether it is saying no. */
    public static class State extends net.minecraft.client.renderer.entity.state.HumanoidRenderState {
        public Identifier clothes = CivVillagerModelLayers.clothes(CivVillager.DEFAULT_VILLAGER_TYPE);
        public boolean unhappy;
    }

    /** The clothes of the villager's type, drawn over the body on the same model, as a villager's are. */
    private static final class Clothes extends RenderLayer<State, CivVillagerModel> {
        Clothes(RenderLayerParent<State, CivVillagerModel> parent) {
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
/*public class CivVillagerRenderer<T extends CivVillager> extends HumanoidMobRenderer<T, CivVillagerModel<T>> {
    private static final ModelLayerLocation INNER_ARMOR = new ModelLayerLocation(Civillagers.id("villager"), "inner_armor");
    private static final ModelLayerLocation OUTER_ARMOR = new ModelLayerLocation(Civillagers.id("villager"), "outer_armor");

    public CivVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new CivVillagerModel<>(context.bakeLayer(CivVillagerModelLayers.BODY)), 0.5F);
        addLayer(new Clothes<>(this));
        addLayer(new HumanoidArmorLayer<>(this, new ArmorModel<>(context.bakeLayer(INNER_ARMOR)),
                new ArmorModel<>(context.bakeLayer(OUTER_ARMOR)), context.getModelManager()));
    }

    /^* Registers the model layers every villager of the mod shares: its body, and the inner and outer armor layers. ^/
    public static void registerLayers() {
        ClientLoader.registerModelLayer(CivVillagerModelLayers.BODY, CivVillagerModelLayers::createBodyLayer);
        // Vanilla's own armor deformations, from LayerDefinitions.
        ClientLoader.registerModelLayer(INNER_ARMOR,
                () -> LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F), 64, 32));
        ClientLoader.registerModelLayer(OUTER_ARMOR,
                () -> LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1.0F), 0.0F), 64, 32));
    }

    @Override
    public Identifier getTextureLocation(T villager) {
        return CivVillagerModelLayers.TEXTURE;
    }

    /^*
     * An armor model whose helmet is stretched over the taller villager head. The stretch goes on just
     * before drawing: the armor layer copies the villager model's pose, scale included, onto it first.
     ^/
    private static final class ArmorModel<T extends CivVillager> extends HumanoidModel<T> {
        ArmorModel(ModelPart root) {
            super(root);
        }

        @Override
        public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                                   int light, int overlay, int color) {
            head.yScale = CivVillagerModelLayers.HELMET_STRETCH;
            hat.yScale = CivVillagerModelLayers.HELMET_STRETCH;
            super.renderToBuffer(poseStack, buffer, light, overlay, color);
        }
    }

    /^* The clothes of the villager's type, drawn over the body on the same model, as a villager's are. ^/
    private static final class Clothes<T extends CivVillager> extends RenderLayer<T, CivVillagerModel<T>> {
        Clothes(RenderLayerParent<T, CivVillagerModel<T>> parent) {
            super(parent);
        }

        @Override
        public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer,
                           int light, T villager, float limbSwing, float limbSwingAmount, float partialTick,
                           float ageInTicks, float netHeadYaw, float headPitch) {
            if (villager.isInvisible()) return;
            renderColoredCutoutModel(getParentModel(), CivVillagerModelLayers.clothes(villager.villagerType()), poseStack, buffer,
                    light, villager, -1);
        }
    }
}
*///?}
