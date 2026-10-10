package com.civillagers.client.platform;

import com.civillagers.client.render.GuardModelLayers;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The guard's model: {@link GuardModelLayers#createBodyLayer()} on a {@link HumanoidModel}, so the arms
 * swing and take a player's poses. The robe is hidden while the guard wears leggings, which would poke
 * through it.
 *
 * <p>One class per side of 1.21.2's render states; see {@link ClientVanilla}.
 */
//? if >=1.21.2 {
public final class GuardModel extends HumanoidModel<net.minecraft.client.renderer.entity.state.HumanoidRenderState> {
    private final ModelPart jacket;

    public GuardModel(ModelPart root) {
        super(root);
        jacket = body.getChild("jacket");
    }

    @Override
    public void setupAnim(net.minecraft.client.renderer.entity.state.HumanoidRenderState state) {
        super.setupAnim(state);
        jacket.visible = state.legsEquipment.isEmpty();
    }
}
//?} else {
/*public final class GuardModel extends HumanoidModel<com.civillagers.entity.Guard> {
    private final ModelPart jacket;

    public GuardModel(ModelPart root) {
        super(root);
        jacket = body.getChild("jacket");
    }

    @Override
    public void prepareMobModel(com.civillagers.entity.Guard guard, float limbSwing, float limbSwingAmount, float partialTick) {
        rightArmPose = GuardModelLayers.armPose(guard, net.minecraft.world.entity.HumanoidArm.RIGHT);
        leftArmPose = GuardModelLayers.armPose(guard, net.minecraft.world.entity.HumanoidArm.LEFT);
        super.prepareMobModel(guard, limbSwing, limbSwingAmount, partialTick);
    }

    @Override
    public void setupAnim(com.civillagers.entity.Guard guard, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(guard, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        jacket.visible = guard.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty();
    }
}
*///?}
