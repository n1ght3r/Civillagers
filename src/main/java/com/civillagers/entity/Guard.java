package com.civillagers.entity;

import com.civillagers.ai.GuardBowGoal;
import com.civillagers.ai.GuardCrossbowGoal;
import com.civillagers.ai.GuardMeleeGoal;
import com.civillagers.ai.GuardPatrolGoal;
import com.civillagers.ai.GuardRaidGoal;
import com.civillagers.ai.GuardShieldGoal;
import com.civillagers.ai.GuardSleepGoal;
import com.civillagers.ai.GuardTargetGoal;
import com.civillagers.platform.SavingMob;
import com.civillagers.platform.Vanilla;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
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
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.Predicate;

/**
 * A village guard: its own entity, not a villager profession, run by a {@code GoalSelector}.
 *
 * <p>By day a guard patrols the village it stands in; at night it sleeps in a free bed; it fights hostile
 * mobs it sees and whatever hurts it, and during a raid it heads for the raid. It fights with the weapon
 * in its main hand (a sword, an axe, a bow or a crossbow), raises the shield in its off hand against
 * archers and creepers, and wears the armor it is given. A player hands it gear by using the item on it,
 * and takes gear back by using an empty hand while sneaking, one piece per use.
 *
 * <p>What a guard costs is kept within the budget in docs/ROADMAP.md: it looks for targets at most once
 * every {@link GuardTargetGoal#SCAN_INTERVAL} ticks, in a bounded box, and starts new paths within a
 * per-tick cap. With no player within {@link #ACTIVE_RANGE} it idles: it stops patrolling and looking
 * around, looks for targets less often, and while it stands still with nothing to do it thinks and moves
 * one tick in {@link #REST_EVERY}. Its tick is timed into {@link GuardStats}. Beyond what vanilla saves for
 * every mob (its equipment, health and, while asleep, its bed) it keeps one value of its own: its
 * {@linkplain #villagerType() villager type}, the biome a villager's clothes come from.
 */
public class Guard extends SavingMob {
    /** How far a guard looks for hostile mobs, horizontally, in blocks. */
    public static final double THREAT_RANGE = 16.0;
    private static final double THREAT_HEIGHT = 6.0;
    /** Ticks between two points of health regained out of combat. */
    private static final int REGEN_INTERVAL = 80;
    /** Beyond this distance from every player, in blocks, a guard idles. */
    public static final double ACTIVE_RANGE = 48.0;
    /** Ticks between two looks for a player within {@link #ACTIVE_RANGE}: at least the first, plus up to the second. */
    private static final int PRESENCE_INTERVAL = 40;
    private static final int PRESENCE_JITTER = 10;
    /** A guard at rest runs its AI and movement one tick in this many. Even, so it lands on vanilla's full AI ticks. */
    private static final int REST_EVERY = 4;

    /** The villager type a guard wears when it has no other: the plains, as a villager does. */
    public static final String DEFAULT_VILLAGER_TYPE = "minecraft:plains";
    private static final String VILLAGER_TYPE_KEY = "villager_type";
    /** Synced, so clients draw the right clothes. */
    private static final EntityDataAccessor<String> VILLAGER_TYPE =
            SynchedEntityData.defineId(Guard.class, EntityDataSerializers.STRING);

    /** The first filter on what a scan returns; {@link #isThreat} does the rest. No capture, so no allocation. */
    private static final Predicate<Mob> MAY_THREATEN = mob -> mob instanceof Enemy && mob.isAlive();

    /** {@code GuardRaidGoal}'s cache: the tick count from which to look for a raid again, and what it found. */
    public int raidCheckTick;
    public BlockPos raidCenter;

    /** The tick count from which to look for a player again, and whether the last look found one. */
    private int presenceCheckTick;
    private boolean playerNear = true;

    public Guard(EntityType<? extends Guard> type, Level level) {
        super(type, level);
        ((GroundPathNavigation) getNavigation()).setCanOpenDoors(true);
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                // The weapon adds its own damage on top, as it does for a player.
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                // How far a guard keeps after a target, and what bounds a path search: a search visits at
                // most sixteen nodes per block of it.
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VILLAGER_TYPE, DEFAULT_VILLAGER_TYPE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new GuardBowGoal(this));
        goalSelector.addGoal(1, new GuardCrossbowGoal(this));
        goalSelector.addGoal(2, new GuardMeleeGoal(this));
        // No flags, so it runs beside the fight; added after it so that it turns the head last.
        goalSelector.addGoal(2, new GuardShieldGoal(this));
        goalSelector.addGoal(3, new GuardRaidGoal(this));
        goalSelector.addGoal(4, new GuardSleepGoal(this));
        goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        goalSelector.addGoal(6, new GuardPatrolGoal(this));
        // Looking around is for players to see, so it stops with nobody near.
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return playerNear() && super.canUse();
            }
        });
        goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return playerNear() && super.canUse();
            }
        });

        // Guards never take offence at their own, the villagers they protect or the golems beside them,
        // and call the guards nearby when something else hurts one of them.
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Guard.class, Villager.class, IronGolem.class)
                .setAlertOthers(Guard.class));
        targetSelector.addGoal(2, new GuardTargetGoal(this));
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
     * Vanilla's AI and movement step, run one tick in {@link #REST_EVERY} while the guard is at rest
     * ({@link #resting()}): nobody sees it, and nothing it would do differs between the ticks it skips.
     * The tick it runs on is one where vanilla also picks new goals.
     */
    @Override
    public void aiStep() {
        if (!level().isClientSide()) {
            if (tickCount % REGEN_INTERVAL == 0 && getTarget() == null && getHealth() < getMaxHealth()) heal(1.0F);
            if (resting() && (tickCount + getId()) % REST_EVERY != 0) return;
        }
        super.aiStep();
    }

    /**
     * Whether the guard is at rest: no player near, nothing to fight and nothing that just hurt it, not
     * walking, not using an item, standing still on dry ground. Anything that pushes or hurts it ends the
     * rest on the next tick.
     */
    private boolean resting() {
        return !playerNear() && getTarget() == null && getLastHurtByMob() == null && onGround() && !isInWater()
                && !isUsingItem() && getNavigation().isDone() && getDeltaMovement().horizontalDistanceSqr() < 1.0E-6;
    }

    /**
     * Whether a player is within {@link #ACTIVE_RANGE}, as of the last look. A look runs over the level's
     * players, so it is made at most once every {@link #PRESENCE_INTERVAL} ticks; a new guard starts out
     * as if one were near.
     */
    public boolean playerNear() {
        if (tickCount >= presenceCheckTick) {
            presenceCheckTick = tickCount + PRESENCE_INTERVAL + getRandom().nextInt(PRESENCE_JITTER + 1);
            playerNear = anyPlayerWithin(level(), getX(), getY(), getZ(), ACTIVE_RANGE);
        }
        return playerNear;
    }

    /** Whether a living player who is not spectating is within {@code range} of the point. */
    public static boolean anyPlayerWithin(Level level, double x, double y, double z, double range) {
        return level.hasNearbyAlivePlayer(x, y, z, range);
    }

    /**
     * The nearest hostile mob within {@link #THREAT_RANGE} that this guard can see, or null. One bounded
     * box query; the caller rate-limits it.
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
                if (mob == null || !isThreat(mob, ranged)) continue;
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
     * Whether a guard goes after {@code mob} unprovoked. Neutral mobs are left alone until they turn on
     * someone, and creepers are left to guards with a bow or a crossbow: a sword would only set them off.
     */
    public static boolean isThreat(Mob mob, boolean ranged) {
        if (!(mob instanceof Enemy) || !mob.isAlive() || mob.isInvisible()) return false;
        if (mob instanceof NeutralMob) return false;
        return ranged || !(mob instanceof Creeper);
    }

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

    // Clothes.

    /**
     * The id of the villager type whose clothes the guard wears, such as {@code minecraft:plains} or
     * {@code minecraft:snow}: the biome it was born in, as for a villager.
     */
    public String villagerType() {
        return entityData.get(VILLAGER_TYPE);
    }

    /** Dresses the guard as a villager of type {@code id}; anything that is not an id leaves it as it was. */
    public void setVillagerType(String id) {
        if (Identifier.tryParse(id) != null) entityData.set(VILLAGER_TYPE, id);
    }

    // Public, as the gametests read and write them directly.
    @Override
    public void saveExtra(Output output) {
        output.putString(VILLAGER_TYPE_KEY, villagerType());
    }

    @Override
    public void loadExtra(Input input) {
        setVillagerType(input.getString(VILLAGER_TYPE_KEY, DEFAULT_VILLAGER_TYPE));
    }

    // Gear.

    /**
     * The slot a guard wears {@code stack} in, or null if it does not take it: armor in its own slot, a
     * shield in the off hand, a sword, an axe, a bow or a crossbow in the main hand.
     */
    public EquipmentSlot gearSlot(ItemStack stack) {
        if (stack.isEmpty()) return null;
        EquipmentSlot slot = getEquipmentSlotForItem(stack);
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.OFFHAND) return slot;
        if (slot != EquipmentSlot.MAINHAND) return null;
        boolean weapon = stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)
                || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
        return weapon ? EquipmentSlot.MAINHAND : null;
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
            if (!player.isSecondaryUseActive()) return super.mobInteract(player, hand);
            if (!level().isClientSide()) give(player, takeGear());
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

    /** Puts {@code stack} in the player's inventory, or at their feet if it is full. */
    private void give(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty() || player.getInventory().add(stack)) return;
        ItemEntity dropped = new ItemEntity(level(), player.getX(), player.getY(), player.getZ(), stack);
        dropped.setNoPickUpDelay();
        level().addFreshEntity(dropped);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        setVillagerType(Vanilla.villagerTypeAt(level, blockPosition()));
        populateDefaultEquipmentSlots(getRandom(), difficulty);
        return result;
    }

    /** A new guard carries an iron sword, and one in four a shield as well. */
    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        if (random.nextInt(4) == 0) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
    }

    // Vanilla behaviour a guard changes.

    /** Guards stay when no player is near, like villagers. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isSleeping() ? null : SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    /** The level this guard is in, on the server; null on a client. */
    public ServerLevel serverLevel() {
        return level() instanceof ServerLevel server ? server : null;
    }
}
