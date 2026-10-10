package com.civillagers.entity;

import com.civillagers.ai.GuardBowGoal;
import com.civillagers.ai.GuardCrossbowGoal;
import com.civillagers.ai.GuardMeleeGoal;
import com.civillagers.ai.GuardPatrolGoal;
import com.civillagers.ai.GuardRaidGoal;
import com.civillagers.ai.GuardRallyGoal;
import com.civillagers.ai.GuardShieldGoal;
import com.civillagers.ai.GuardSleepGoal;
import com.civillagers.ai.GuardTargetGoal;
import com.civillagers.api.CivTags;
import com.civillagers.api.GuardEvents;
import com.civillagers.api.Verdict;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.Predicate;

/**
 * A village guard: a {@link CivVillager} who fights. What it shares with the mod's other villagers (look,
 * voice, clothes, idling, saving) is in {@link CivVillager}; this is the guard's role.
 *
 * <p>By day a guard patrols the village it stands in; at night it sleeps in a free bed; it fights hostile
 * mobs it sees and whatever hurts it, and during a raid it heads for the raid. It fights with the weapon
 * in its main hand (any item in {@link CivTags#GUARD_WEAPONS}: swords, axes, bows and crossbows), raises
 * the shield in its off hand against archers and creepers, and wears the armor it is given. A player hands
 * it gear by using the item on it, and takes gear back by using an empty hand while sneaking, one piece per
 * use. Out of combat it regains health slowly.
 *
 * <p>A guard is a {@linkplain #isCombatant() combatant} only while it holds a weapon. Disarmed, it is a
 * villager like any other: it flees from zombies and raiders and hides when the bell rings, and if it is
 * hurt it calls the guards near it but does not fight back. Armed, it runs to a bell that rings
 * ({@link GuardRallyGoal}) instead of hiding.
 *
 * <p>What a guard costs is kept within the budget in docs/ROADMAP.md: it looks for targets at most once
 * every {@link GuardTargetGoal#SCAN_INTERVAL} ticks, in a bounded box, and starts new paths within a
 * per-tick cap. Idle with nobody near, as every {@link CivVillager} is, it also stops patrolling and looks
 * for targets less often. Its tick is timed into {@link GuardStats}.
 *
 * <p>Addons change what a guard does through {@link GuardEvents} and the
 * {@link com.civillagers.api.CivVillagerEvents} every villager of the mod fires; see
 * docs/knowledge/addon-api.md.
 */
public class Guard extends CivVillager {
    /** How far a guard looks for hostile mobs, horizontally, in blocks. */
    public static final double THREAT_RANGE = 16.0;
    private static final double THREAT_HEIGHT = 6.0;
    /** Ticks between two points of health regained out of combat. */
    private static final int REGEN_INTERVAL = 80;

    /** The first filter on what a scan returns; {@link #goesAfter} does the rest. No capture, so no allocation. */
    private static final Predicate<Mob> MAY_THREATEN = mob -> mob instanceof Enemy && mob.isAlive();

    public Guard(EntityType<? extends Guard> type, Level level) {
        super(type, level);
    }

    /** A villager's attributes, and a base attack of 1: the weapon adds its own damage, as for a player. */
    public static AttributeSupplier.Builder createAttributes() {
        return createVillagerAttributes().add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void registerRoleGoals() {
        goalSelector.addGoal(1, new GuardBowGoal(this));
        goalSelector.addGoal(1, new GuardCrossbowGoal(this));
        goalSelector.addGoal(2, new GuardMeleeGoal(this));
        // No flags, so it runs beside the fight; added after it so that it turns the head last.
        goalSelector.addGoal(2, new GuardShieldGoal(this));
        goalSelector.addGoal(3, new GuardRaidGoal(this));
        goalSelector.addGoal(3, new GuardRallyGoal(this));
        goalSelector.addGoal(4, new GuardSleepGoal(this));
        goalSelector.addGoal(6, new GuardPatrolGoal(this));

        // Guards never take offence at their own, the villagers they protect or the golems beside them,
        // and call the guards nearby when something else hurts one of them.
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Guard.class, Villager.class, IronGolem.class)
                .setAlertOthers(Guard.class));
        targetSelector.addGoal(2, new GuardTargetGoal(this));
    }

    /** A guard fights while it holds a weapon ({@link CivTags#GUARD_WEAPONS}); without one it is a civilian. */
    @Override
    public boolean isCombatant() {
        return getMainHandItem().is(CivTags.GUARD_WEAPONS);
    }

    @Override
    public void tick() {
        if (level().isClientSide()) {
            super.tick();
            return;
        }
        long start = System.nanoTime();
        super.tick();
        GuardStats.recordGuardTick(System.nanoTime() - start);
    }

    /**
     * Regains 1 health every {@link #REGEN_INTERVAL} ticks while it has nothing to fight, even at rest. A
     * guard with no weapon lets go of its target: being hurt still calls the guards near it, then it flees.
     */
    @Override
    public void aiStep() {
        if (!level().isClientSide()) {
            if (getTarget() != null && !isCombatant()) setTarget(null);
            if (tickCount % REGEN_INTERVAL == 0 && getTarget() == null && getHealth() < getMaxHealth()) heal(1.0F);
        }
        super.aiStep();
    }

    // Threats.

    /**
     * The nearest hostile mob within {@link #THREAT_RANGE} that this guard can see and
     * {@linkplain #goesAfter goes after}, or null. One bounded box query; the caller rate-limits it.
     */
    public LivingEntity findThreat() {
        AABB box = getBoundingBox().inflate(THREAT_RANGE, THREAT_HEIGHT, THREAT_RANGE);
        List<Mob> mobs = level().getEntitiesOfClass(Mob.class, box, MAY_THREATEN);
        boolean ranged = holdsRangedWeapon();
        // Nearest first, but a line of sight is a ray cast, so only the nearest few are looked at.
        for (int tries = 0; tries < 3; tries++) {
            Mob nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (int i = 0; i < mobs.size(); i++) {
                Mob mob = mobs.get(i);
                if (mob == null || !goesAfter(mob, ranged)) continue;
                double distance = distanceToSqr(mob);
                if (distance < nearestDistance) {
                    nearest = mob;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) return null;
            if (getSensing().hasLineOfSight(nearest)) return nearest;
            mobs.set(mobs.indexOf(nearest), null);
        }
        return null;
    }

    /**
     * Whether this guard goes after {@code mob} unprovoked: the first {@link GuardEvents#IS_THREAT} listener
     * with a verdict decides, and with none, {@link #isThreat}. Only hostile mobs ({@code Enemy}) reach it.
     */
    public boolean goesAfter(Mob mob, boolean ranged) {
        for (int i = 0; i < GuardEvents.IS_THREAT.size(); i++) {
            Verdict verdict = GuardEvents.IS_THREAT.get(i).isThreat(this, mob, ranged);
            if (verdict != Verdict.PASS) return verdict == Verdict.YES;
        }
        return isThreat(mob, ranged);
    }

    /**
     * The default rule for whether a guard goes after {@code mob} unprovoked. Neutral mobs are left alone
     * until they turn on someone, and creepers are left to guards with a bow or a crossbow: a sword would
     * only set them off.
     */
    public static boolean isThreat(Mob mob, boolean ranged) {
        if (!(mob instanceof Enemy) || !mob.isAlive() || mob.isInvisible()) return false;
        if (mob instanceof NeutralMob) return false;
        return ranged || !(mob instanceof Creeper);
    }

    // Weapons.

    /** Whether the main hand holds a bow, which the guard shoots instead of closing in. */
    public boolean holdsBow() {
        return getMainHandItem().getItem() instanceof BowItem;
    }

    /** Whether the main hand holds a crossbow, which the guard charges and shoots. */
    public boolean holdsCrossbow() {
        return getMainHandItem().getItem() instanceof CrossbowItem;
    }

    /** Whether the main hand holds a bow or a crossbow: the guard fights from afar. */
    public boolean holdsRangedWeapon() {
        return holdsBow() || holdsCrossbow();
    }

    /** Whether the off hand holds a shield. */
    public boolean holdsShield() {
        return getOffhandItem().getItem() instanceof ShieldItem;
    }

    /** A guard's bow and crossbow need no arrows: it always has one, as a skeleton does. */
    @Override
    public ItemStack getProjectile(ItemStack weapon) {
        return weapon.getItem() instanceof ProjectileWeaponItem ? new ItemStack(Items.ARROW) : ItemStack.EMPTY;
    }

    // Gear.

    /**
     * The slot a guard wears {@code stack} in, or null if it does not take it: armor in its own slot, a
     * shield in the off hand, a weapon ({@link CivTags#GUARD_WEAPONS}) in the main hand.
     */
    public EquipmentSlot gearSlot(ItemStack stack) {
        if (stack.isEmpty()) return null;
        EquipmentSlot slot = getEquipmentSlotForItem(stack);
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.OFFHAND) return slot;
        return slot == EquipmentSlot.MAINHAND && stack.is(CivTags.GUARD_WEAPONS) ? EquipmentSlot.MAINHAND : null;
    }

    /**
     * Puts one of {@code stack} on the guard and returns what it wore in that slot before, or null if it
     * does not take the item. Gear a player hands over always drops when the guard dies.
     */
    public ItemStack equipGear(ItemStack stack) {
        EquipmentSlot slot = gearSlot(stack);
        if (slot == null) return null;
        ItemStack previous = getItemBySlot(slot);
        setItemSlot(slot, stack.copyWithCount(1));
        setDropChance(slot, 2.0F);
        return previous;
    }

    /** The order {@link #takeGear()} hands gear back in: weapons first, then armor from the top. */
    private static final EquipmentSlot[] HAND_BACK_ORDER = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
    };

    /**
     * Takes off the first piece of gear in {@link #HAND_BACK_ORDER} and returns it, or an empty stack. A
     * crossbow comes back unloaded: the guard's bolt was never the player's.
     */
    public ItemStack takeGear() {
        for (EquipmentSlot slot : HAND_BACK_ORDER) {
            ItemStack worn = getItemBySlot(slot);
            if (worn.isEmpty()) continue;
            setItemSlot(slot, ItemStack.EMPTY);
            if (worn.getItem() instanceof CrossbowItem) worn.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            return worn;
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            // An empty hand: sneaking takes gear back, otherwise the guard has nothing to trade.
            if (!level().isClientSide()) {
                if (player.isSecondaryUseActive()) give(player, takeGear());
                else refuse();
            }
            return InteractionResult.SUCCESS;
        }
        if (gearSlot(held) == null) return super.mobInteract(player, hand);
        if (!level().isClientSide()) {
            ItemStack previous = equipGear(held);
            if (!player.hasInfiniteMaterials()) held.shrink(1);
            give(player, previous);
            playSound(SoundEvents.VILLAGER_YES, 1.0F, getVoicePitch());
        }
        return InteractionResult.SUCCESS;
    }

    /** A new guard carries an iron sword, and one in four a shield as well. */
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        if (random.nextInt(4) == 0) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
    }
}
