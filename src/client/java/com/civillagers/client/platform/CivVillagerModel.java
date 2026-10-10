package com.civillagers.client.platform;

import com.civillagers.client.render.CivVillagerModelLayers;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The model of every villager of the mod: {@link CivVillagerModelLayers#createBodyLayer()} on a
 * {@link HumanoidModel}, so the arms swing and take a player's poses. The robe is hidden while the
 * villager wears leggings, which would poke through it, and the head shakes while it says no
 * ({@link CivVillagerModelLayers#shakeHead}), as a villager's does. Not final: an addon's villager with a
 * part of its own extends it.
 *
 * <p>One class per side of 1.21.2's render states; see {@link ClientVanilla}. Before them the model is
 * typed by the entity it draws.
 */
//? if >=1.21.2 {
public class CivVillagerModel extends HumanoidModel<CivVillagerRenderer.State> {
    private final ModelPart jacket;

    public CivVillagerModel(ModelPart root) {
        super(root);
        jacket = body.getChild("jacket");
    }

    @Override
    public void setupAnim(CivVillagerRenderer.State state) {
        super.setupAnim(state);
        jacket.visible = state.legsEquipment.isEmpty();
        CivVillagerModelLayers.shakeHead(head, state.unhappy, state.ageInTicks);
    }
}
//?} else {
/*public class CivVillagerModel<T extends com.civillagers.entity.CivVillager> extends HumanoidModel<T> {
    private final ModelPart jacket;

    public CivVillagerModel(ModelPart root) {
        super(root);
        jacket = body.getChild("jacket");
    }

    @Override
    public void prepareMobModel(T villager, float limbSwing, float limbSwingAmount, float partialTick) {
        rightArmPose = CivVillagerModelLayers.armPose(villager, net.minecraft.world.entity.HumanoidArm.RIGHT);
        leftArmPose = CivVillagerModelLayers.armPose(villager, net.minecraft.world.entity.HumanoidArm.LEFT);
        super.prepareMobModel(villager, limbSwing, limbSwingAmount, partialTick);
    }

    @Override
    public void setupAnim(T villager, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(villager, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        jacket.visible = villager.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty();
        CivVillagerModelLayers.shakeHead(head, villager.isUnhappy(), ageInTicks);
        // The hat sits beside the head here, not on it, so it follows the shake by hand.
        hat.copyFrom(head);
    }
}
*///?}
