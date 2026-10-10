package com.civillagers.client.platform;

import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The client half of {@link com.civillagers.platform.Vanilla}: where Minecraft's client code differs
 * between the versions the mod builds for, under the same rules.
 *
 * <p>Two whole classes sit beside it for the same reason: {@link GuardModel} and {@link GuardRenderer}.
 * 1.21.2 moved entity rendering onto render states, which changed the type parameters and the overridden
 * methods of every model and renderer, so each is one class per side of that line rather than a method
 * here.
 */
public final class ClientVanilla {
    private ClientVanilla() { }

    /** Where a humanoid model's hat goes: under the head from 1.21.2, beside it at the root before. */
    public static PartDefinition hatParent(PartDefinition root, PartDefinition head) {
        //? if >=1.21.2 {
        return head;
        //?} else
        //return root;
    }
}
