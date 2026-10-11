package com.civillagers.client.render;

import com.civillagers.Civillagers;
import com.civillagers.client.platform.ClientVanilla;
import com.civillagers.entity.CivVillager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * What every villager of the mod looks like (a guard today), the same on every Minecraft version: a
 * villager's head, nose, body and robe on a humanoid's two separate arms, drawn with vanilla's own
 * villager texture. The model and renderer classes around it differ between versions and live in
 * {@code client/platform}.
 *
 * <p>The villager texture already has an arm and a hand where the crossed arms take them from: the arm at
 * (44, 22), 4x8x4, and the hands' bar at (40, 38). Each arm is that arm with half the bar below it for a
 * hand, twelve pixels long like a player's, so held items sit where they would in a player's hand.
 * The arms hang flush against the body, as a player's do; the robe, half a pixel wider, tucks under them.
 *
 * <p>That texture is a villager's bare body, in a grey shirt. Its clothes are a second texture drawn over
 * it on the same model, one per villager type ({@link #clothes}), as vanilla draws a villager's, and the
 * badge of its level a third ({@link #badge}).
 */
public final class CivVillagerModelLayers {
    public static final ModelLayerLocation BODY = new ModelLayerLocation(Civillagers.id("villager"), "main");
    /** Vanilla's villager texture, read by path from the game, never copied into the mod. */
    public static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    /** The clothes of vanilla's villager types, made once so that drawing a villager allocates nothing. Never changes. */
    private static final Map<String, Identifier> VANILLA_CLOTHES = new HashMap<>();
    static {
        for (String type : new String[] { "desert", "jungle", "plains", "savanna", "snow", "swamp", "taiga" }) {
            Identifier id = Identifier.withDefaultNamespace(type);
            VANILLA_CLOTHES.put(id.toString(), clothesTexture(id));
        }
    }
    /** The badges of the five levels, novice to master, as vanilla draws them on a villager's belt. Never changes. */
    private static final Identifier[] BADGES = {
            badgeTexture("stone"), badgeTexture("iron"), badgeTexture("gold"), badgeTexture("emerald"), badgeTexture("diamond"),
    };
    /** A helmet is drawn this much taller, to cover the villager's head: ten pixels where a player's is eight. */
    public static final float HELMET_STRETCH = 10.0F / 8.0F;

    private CivVillagerModelLayers() { }

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
     * The clothes of the villager type {@code type}, such as {@code minecraft:snow}: the texture vanilla
     * draws over a villager of that type, read by path from the game. Another mod's type gets its own
     * texture by the same path, and anything that is not an id the plains' clothes.
     */
    public static Identifier clothes(String type) {
        Identifier vanilla = VANILLA_CLOTHES.get(type);
        if (vanilla != null) return vanilla;
        Identifier id = Identifier.tryParse(type);
        return id != null ? clothesTexture(id) : VANILLA_CLOTHES.get(CivVillager.DEFAULT_VILLAGER_TYPE);
    }

    private static Identifier clothesTexture(Identifier type) {
        return type.withPath("textures/entity/villager/type/" + type.getPath() + ".png");
    }

    /**
     * The badge on the belt of a villager of {@code level}, 1 (novice, stone) to 5 (master, diamond): vanilla's
     * own, read by path from the game, drawn over the robe on the same model. Null for no badge. The robe
     * stands out past a chestplate, so the badge shows over armor too.
     */
    public static Identifier badge(int level) {
        return level >= 1 && level <= BADGES.length ? BADGES[level - 1] : null;
    }

    private static Identifier badgeTexture(String name) {
        return Identifier.withDefaultNamespace("textures/entity/villager/profession_level/" + name + ".png");
    }

    /**
     * Shakes {@code head} from side to side, chin down, while {@code unhappy}, as vanilla's villager model
     * does when a villager says no; otherwise leaves it upright.
     */
    public static void shakeHead(ModelPart head, boolean unhappy, float ageInTicks) {
        if (unhappy) {
            head.zRot = 0.3F * Mth.sin(0.45F * ageInTicks);
            head.xRot = 0.4F;
        } else {
            head.zRot = 0.0F;
        }
    }

    /**
     * How {@code mob} holds {@code arm}: drawing a bow while it fights with one, charging a crossbow or
     * aiming a loaded one, raising a shield it is blocking with, holding out whatever else it carries, or
     * hanging empty. Charging is the mob using the crossbow and loaded is the bolt the crossbow carries,
     * both of which the client knows, as it does for a pillager.
     */
    public static HumanoidModel.ArmPose armPose(Mob mob, HumanoidArm arm) {
        boolean main = arm == mob.getMainArm();
        ItemStack held = main ? mob.getMainHandItem() : mob.getOffhandItem();
        if (held.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
        if (main && mob.isAggressive() && held.getItem() instanceof BowItem) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        if (main && held.getItem() instanceof CrossbowItem) {
            if (mob.isUsingItem() && mob.getUseItem() == held) return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            if (CrossbowItem.isCharged(held)) return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        if (mob.isUsingItem() && mob.getUseItem() == held && mob.isBlocking()) return HumanoidModel.ArmPose.BLOCK;
        return HumanoidModel.ArmPose.ITEM;
    }
}
