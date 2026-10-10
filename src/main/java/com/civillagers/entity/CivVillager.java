package com.civillagers.entity;

import com.civillagers.api.CivVillagerEvents;
import com.civillagers.platform.SavingMob;
import com.civillagers.platform.Vanilla;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * What every villager of the mod's own shares, the guard today and the builder and chief later: a
 * villager's look, voice and health, the clothes of its {@linkplain #villagerType() villager type}, and
 * the cost rules every one of them keeps. A role extends it and adds its own goals in
 * {@link #registerRoleGoals()}; an addon can extend it the same way.
 *
 * <ul>
 *   <li><b>Looks and sounds</b> like a villager: villager sounds, a villager's 20 health, never
 *       despawns, opens doors, picks nothing up.</li>
 *   <li><b>Villager type</b>: taken from the biome it spawns in, saved and synced, so clients draw its
 *       clothes. See docs/knowledge/villager-type.md.</li>
 *   <li><b>Idles</b> with no player within {@link #ACTIVE_RANGE}: the look-around goals stop, and while
 *       it stands still with nothing to do ({@link #resting()}) its AI and movement run one tick in
 *       {@link #REST_EVERY}. A role reads {@link #playerNear()} to idle its own goals too.</li>
 *   <li><b>Goals</b>: floating, doors and looking around at the lowest priorities, the role's goals
 *       ({@link #registerRoleGoals()}), then whatever addons add through
 *       {@link CivVillagerEvents#REGISTER_GOALS}.</li>
 *   <li><b>Zombies</b>: a type registered with {@link ZombieVillagers#register} turns into a zombie
 *       villager when a zombie kills it, as a villager does, and is cured back.</li>
 * </ul>
 *
 * <p>Goal priorities 0 (floating), 5 (doors), 7 and 8 (looking around) are taken here; a role uses the
 * others.
 */
public abstract class CivVillager extends SavingMob {
    /** Beyond this distance from every player, in blocks, a villager of the mod idles. */
    public static final double ACTIVE_RANGE = 48.0;
    /** Ticks between two looks for a player within {@link #ACTIVE_RANGE}: at least the first, plus up to the second. */
    private static final int PRESENCE_INTERVAL = 40;
    private static final int PRESENCE_JITTER = 10;
    /** At rest, the AI and movement run one tick in this many. Even, so it lands on vanilla's full AI ticks. */
    protected static final int REST_EVERY = 4;

    /** The villager type worn when there is no other: the plains, as for a villager. */
    public static final String DEFAULT_VILLAGER_TYPE = "minecraft:plains";
    private static final String VILLAGER_TYPE_KEY = "villager_type";
    /** Synced, so clients draw the right clothes. */
    private static final EntityDataAccessor<String> VILLAGER_TYPE =
            SynchedEntityData.defineId(CivVillager.class, EntityDataSerializers.STRING);

    /** The tick count from which to look for a player again, and whether the last look found one. */
    private int presenceCheckTick;
    private boolean playerNear = true;

    protected CivVillager(EntityType<? extends CivVillager> type, Level level) {
        super(type, level);
        ((GroundPathNavigation) getNavigation()).setCanOpenDoors(true);
        setCanPickUpLoot(false);
    }

    /** A villager's attributes: 20 health, its walking speed, and a 16 block follow range that also bounds path searches. */
    public static AttributeSupplier.Builder createVillagerAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                // A path search visits at most sixteen nodes per block of it.
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VILLAGER_TYPE, DEFAULT_VILLAGER_TYPE);
    }

    // Goals.

    @Override
    protected final void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new OpenDoorGoal(this, true));
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
        registerRoleGoals();
        for (int i = 0; i < CivVillagerEvents.REGISTER_GOALS.size(); i++) {
            CivVillagerEvents.REGISTER_GOALS.get(i).registerGoals(this, goalSelector, targetSelector);
        }
    }

    /**
     * Adds what this role does to {@code goalSelector} and {@code targetSelector}. Server side only, once,
     * from the constructor: fields of the subclass are not set yet.
     */
    protected abstract void registerRoleGoals();

    // Idling.

    /**
     * Vanilla's AI and movement step, run one tick in {@link #REST_EVERY} while the villager is at rest
     * ({@link #resting()}): nobody sees it, and nothing it would do differs between the ticks it skips.
     * The tick it runs on is one where vanilla also picks new goals.
     */
    @Override
    public void aiStep() {
        if (!level().isClientSide() && resting() && (tickCount + getId()) % REST_EVERY != 0) return;
        super.aiStep();
    }

    /**
     * Whether the villager is at rest: no player near, nothing to fight and nothing that just hurt it, not
     * walking, not using an item, standing still on dry ground. Anything that pushes or hurts it ends the
     * rest on the next tick. A role with more to do while unwatched narrows it.
     */
    protected boolean resting() {
        return !playerNear() && getTarget() == null && getLastHurtByMob() == null && onGround() && !isInWater()
                && !isUsingItem() && getNavigation().isDone() && getDeltaMovement().horizontalDistanceSqr() < 1.0E-6;
    }

    /**
     * Whether a player is within {@link #ACTIVE_RANGE}, as of the last look. A look runs over the level's
     * players, so it is made at most once every {@link #PRESENCE_INTERVAL} ticks; a new villager starts out
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

    // Villager type.

    /**
     * The id of the villager type whose clothes this villager wears, such as {@code minecraft:plains} or
     * {@code minecraft:snow}: the biome it was born in, as for a villager.
     */
    public String villagerType() {
        return entityData.get(VILLAGER_TYPE);
    }

    /** Dresses the villager as one of type {@code id}; anything that is not an id leaves it as it was. */
    public void setVillagerType(String id) {
        if (Identifier.tryParse(id) != null) entityData.set(VILLAGER_TYPE, id);
    }

    // Public, as the gametests read and write them directly. A subclass that saves more calls super first.
    @Override
    public void saveExtra(Output output) {
        super.saveExtra(output);
        output.putString(VILLAGER_TYPE_KEY, villagerType());
    }

    @Override
    public void loadExtra(Input input) {
        super.loadExtra(input);
        setVillagerType(input.getString(VILLAGER_TYPE_KEY, DEFAULT_VILLAGER_TYPE));
    }

    // Spawning.

    /** A new villager takes the villager type of its biome, then the role's starting gear. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason reason, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        setVillagerType(Vanilla.villagerTypeAt(level, blockPosition()));
        populateDefaultEquipmentSlots(getRandom(), difficulty);
        return result;
    }

    /** Nothing by default, where vanilla would hand out random armor; a role overrides it with its own gear. */
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
    }

    // Vanilla behaviour a villager of the mod changes.

    /** Stays when no player is near, like villagers. */
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

    /** Puts {@code stack} in the player's inventory, or at their feet if it is full. Null or empty gives nothing. */
    protected void give(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty() || player.getInventory().add(stack)) return;
        ItemEntity dropped = new ItemEntity(level(), player.getX(), player.getY(), player.getZ(), stack);
        dropped.setNoPickUpDelay();
        level().addFreshEntity(dropped);
    }

    /** The level this villager is in, on the server; null on a client. */
    public ServerLevel serverLevel() {
        return level() instanceof ServerLevel server ? server : null;
    }
}
