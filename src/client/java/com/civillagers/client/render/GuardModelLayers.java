package com.civillagers.client.render;

import com.civillagers.Civillagers;
import com.civillagers.client.platform.ClientVanilla;
import com.civillagers.entity.Guard;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/**
 * What the guard looks like, the same on every Minecraft version: a villager's head, nose, body and robe
 * on a humanoid's two separate arms, drawn with vanilla's own villager texture. The model and renderer
 * classes around it differ between versions and live in {@code client/platform}.
 *
 * <p>The villager texture already has an arm and a hand where the crossed arms take them from: the arm at
 * (44, 22), 4x8x4, and the hands' bar at (40, 38). Each guard arm is that arm with half the bar below it
 * for a hand, twelve pixels long like a player's, so held items sit where they would in a player's hand.
 * The arms hang flush against the body, as a player's do; the robe, half a pixel wider, tucks under them.
 */
public final class GuardModelLayers {
    public static final ModelLayerLocation BODY = new ModelLayerLocation(Civillagers.id("guard"), "main");
    /** Vanilla's villager texture, read by path from the game, never copied into the mod. */
    public static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    /** A helmet is drawn this much taller, to cover the villager's head: ten pixels where a player's is eight. */
    public static final float HELMET_STRETCH = 10.0F / 8.0F;

    private GuardModelLayers() { }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F), PartPose.ZERO);
        ClientVanilla.hatParent(root, head).addOrReplaceChild("hat", CubeListBuilder.create().texOffs(32, 0)
                .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.51F)), PartPose.ZERO);
        head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(24, 0)
                .addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, -2.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(16, 20).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F), PartPose.ZERO);
        body.addOrReplaceChild("jacket", CubeListBuilder.create().texOffs(0, 38)
                .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 20.0F, 6.0F, new CubeDeformation(0.5F)), PartPose.ZERO);

        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(44, 22).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(40, 38).addBox(-3.0F, 6.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror()
                .texOffs(44, 22).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                .texOffs(40, 38).addBox(-1.0F, 6.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 22)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(-2.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 22).mirror()
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(2.0F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /**
     * How the guard holds {@code arm}: drawing a bow while it fights with one, raising a shield it is
     * blocking with, holding out whatever else it carries, or hanging empty.
     */
    public static HumanoidModel.ArmPose armPose(Guard guard, HumanoidArm arm) {
        boolean main = arm == guard.getMainArm();
        ItemStack held = main ? guard.getMainHandItem() : guard.getOffhandItem();
        if (held.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
        if (main && guard.isAggressive() && held.getItem() instanceof BowItem) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        if (guard.isUsingItem() && guard.getUseItem() == held && guard.isBlocking()) return HumanoidModel.ArmPose.BLOCK;
        return HumanoidModel.ArmPose.ITEM;
    }
}
