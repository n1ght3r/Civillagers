package com.civillagers.entity;

import com.civillagers.ai.villager.CivCelebrateGoal;
import com.civillagers.ai.villager.CivHideGoal;
import com.civillagers.ai.villager.CivMeetGoal;
import com.civillagers.ai.villager.CivPanicGoal;
import com.civillagers.Civillagers;
import com.civillagers.api.CivVillagerEvents;
import com.civillagers.platform.SavingMob;
import com.civillagers.platform.Vanilla;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * What every villager of the mod's own shares, the guard today and the builder and chief later: a
 * villager's look, voice and health, the clothes of its {@linkplain #villagerType() villager type}, and
 * the cost rules every one of them keeps. A role extends it and adds its own goals in
 * {@link #registerRoleGoals()}; an addon can extend it the same way.
 *
 * <ul>
 *   <li><b>Looks and sounds</b> like a villager: villager sounds, a villager's 20 health, never
 *       despawns, opens doors, picks nothing up, shakes its head when it has nothing for a player
 *       ({@link #refuse()}).</li>
 *   <li><b>Lives</b> like a villager: meets the others at the village bell in vanilla's meeting hours
 *       and looks at them as it talks ({@link CivMeetGoal}), and celebrates a raid the village won
 *       ({@link CivCelebrateGoal}).</li>
 *   <li><b>Civilian or combatant</b>: one that does not fight ({@link #isCombatant()} false: a builder,
 *       or a guard with no weapon) flees from zombies and raiders and panics when hurt
 *       ({@link CivPanicGoal}), and hides at home when the bell rings or a raid comes
 *       ({@link CivHideGoal}), as a villager does. A combatant stands its ground; its role decides what
 *       it does instead.</li>
 *   <li><b>Pace</b>: a villager's speed, each with its own pace up to {@link #SPEED_VARIANT_RANGE}
 *       thousandths faster or slower than the others, drawn once and saved.</li>
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
 * <p>Goal priorities taken here: 0 floating, 1 panic, 2 hiding, 3 celebrating, 5 doors, 6 meeting, 7 and
 * 8 looking around. A role adds its own beside them; one that shares a priority with a goal here never
 * cuts it short.
 */
public abstract class CivVillager extends SavingMob {
    /** Beyond this distance from every player, in blocks, a villager of the mod idles. */
    public static final double ACTIVE_RANGE = 48.0;
    /** Ticks between two looks for a player within {@link #ACTIVE_RANGE}: at least the first, plus up to the second. */
    private static final int PRESENCE_INTERVAL = 40;
    private static final int PRESENCE_JITTER = 10;
    /** At rest, the AI and movement run one tick in this many. Even, so it lands on vanilla's full AI ticks. */
    protected static final int REST_EVERY = 4;
    /** Ticks between two raid lookups. */
    private static final int RAID_CHECK_INTERVAL = 40;
    /** How far a ringing bell is heard, in blocks: vanilla's, for villagers. */
    public static final double HEAR_BELL_RADIUS = 32.0;
    /** Ticks a rung bell keeps a villager alarmed: vanilla's 15 seconds of hiding. */
    public static final int ALARM_TICKS = 300;
    /** What a step into water costs a path; its edge keeps vanilla's cost, so banks and bridges stay walkable. */
    private static final float WATER_MALUS = 16.0F;
    /** A villager's walking speed attribute, as vanilla's: 0.5, which a goal multiplies (0.5 to walk, 0.75 to flee). */
    public static final double BASE_SPEED = 0.5;
    /** How far each villager's own pace is from the others', at most, in thousandths: five percent either way. */
    public static final int SPEED_VARIANT_RANGE = 50;
    private static final String SPEED_VARIANT_KEY = "speed_variant";
    private static final Identifier SPEED_VARIANT_ID = Civillagers.id("speed_variant");
    /** Ticks a villager shakes its head for. */
    private static final int UNHAPPY_TICKS = 40;

    /** The villager type worn when there is no other: the plains, as for a villager. */
    public static final String DEFAULT_VILLAGER_TYPE = "minecraft:plains";
    private static final String VILLAGER_TYPE_KEY = "villager_type";
    /** Synced, so clients draw the right clothes. */
    private static final EntityDataAccessor<String> VILLAGER_TYPE =
            SynchedEntityData.defineId(CivVillager.class, EntityDataSerializers.STRING);
    /** Ticks left of shaking its head; synced, so clients draw it. */
    private static final EntityDataAccessor<Integer> UNHAPPY =
            SynchedEntityData.defineId(CivVillager.class, EntityDataSerializers.INT);

    /** This villager's own pace, in thousandths above or below everyone's; drawn once, saved. */
    private int speedVariant;
    /** The tick count from which to look for a player again, and whether the last look found one. */
    private int presenceCheckTick;
    private boolean playerNear = true;

    /** The tick count from which to look for a raid again, and what the last look found. */
    private int raidCheckTick;
    private BlockPos raidCenter;
    private boolean raidWon;

    /** The bell last heard ringing, and the game time it rang; never saved, an alarm is short. */
    private BlockPos alarmBell;
    private long alarmTime;

    protected CivVillager(EntityType<? extends CivVillager> type, Level level) {
        super(type, level);
        ((GroundPathNavigation) getNavigation()).setCanOpenDoors(true);
        // Twice vanilla's cost for a step through water, so a path takes a bridge or goes round a pond
        // rather than wading: villagers keep out of water too.
        setPathfindingMalus(PathType.WATER, WATER_MALUS);
        setCanPickUpLoot(false);
        setSpeedVariant(getRandom().nextInt(2 * SPEED_VARIANT_RANGE + 1) - SPEED_VARIANT_RANGE);
    }

    /** A villager's attributes: 20 health, its walking speed, and a 16 block follow range that also bounds path searches. */
    public static AttributeSupplier.Builder createVillagerAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                // A path search visits at most sixteen nodes per block of it.
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VILLAGER_TYPE, DEFAULT_VILLAGER_TYPE);
        builder.define(UNHAPPY, 0);
    }

    // Goals.

    @Override
    protected final void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CivPanicGoal(this));
        goalSelector.addGoal(2, new CivHideGoal(this));
        goalSelector.addGoal(3, new CivCelebrateGoal(this));
        goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        goalSelector.addGoal(6, new CivMeetGoal(this));
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
        if (!level().isClientSide()) {
            int unhappy = entityData.get(UNHAPPY);
            if (unhappy > 0) entityData.set(UNHAPPY, unhappy - 1);
            if (resting() && (tickCount + getId()) % REST_EVERY != 0) return;
        }
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

    // Fighting or not.

    /**
     * Whether this villager fights. One that does not is a civilian, and flees and hides as a villager
     * does. False here; a guard is a combatant while it holds a weapon.
     */
    public boolean isCombatant() {
        return false;
    }

    // Raids and the bell.

    /**
     * The center of the raid being fought where the villager stands, or null. Looked up at most once every
     * {@link #RAID_CHECK_INTERVAL} ticks, with {@link #raidWon()}.
     */
    public BlockPos raidCenter() {
        refreshRaid();
        return raidCenter;
    }

    /** Whether a raid where the villager stands has just been won, and the village is celebrating. */
    public boolean raidWon() {
        refreshRaid();
        return raidWon;
    }

    private void refreshRaid() {
        if (tickCount < raidCheckTick) return;
        raidCheckTick = tickCount + RAID_CHECK_INTERVAL;
        ServerLevel level = serverLevel();
        Raid raid = level == null ? null : level.getRaidAt(blockPosition());
        raidCenter = raid != null && raid.isActive() && !raid.isOver() ? raid.getCenter() : null;
        raidWon = raid != null && raid.isVictory();
    }

    /** Hears the bell at {@code bell} ring at {@code gameTime}: alarmed for {@link #ALARM_TICKS}. */
    public void hearBell(BlockPos bell, long gameTime) {
        alarmBell = bell.immutable();
        alarmTime = gameTime;
    }

    /** The bell that rang within the last {@link #ALARM_TICKS}, or null. */
    public BlockPos alarmBell() {
        ServerLevel level = serverLevel();
        if (alarmBell != null && (level == null || level.getGameTime() - alarmTime > ALARM_TICKS)) alarmBell = null;
        return alarmBell;
    }

    /** A bell rang at {@code bell}: every villager of the mod within {@link #HEAR_BELL_RADIUS} hears it, as villagers do. */
    public static void bellRang(ServerLevel level, BlockPos bell) {
        List<CivVillager> near = level.getEntitiesOfClass(CivVillager.class, new AABB(bell).inflate(HEAR_BELL_RADIUS));
        for (int i = 0; i < near.size(); i++) {
            CivVillager villager = near.get(i);
            if (villager.isAlive() && bell.closerToCenterThan(villager.position(), HEAR_BELL_RADIUS)) {
                villager.hearBell(bell, level.getGameTime());
            }
        }
    }

    // Shaking its head.

    /** Shakes its head and says no, as a villager with nothing to trade does. */
    public void refuse() {
        entityData.set(UNHAPPY, UNHAPPY_TICKS);
        playSound(SoundEvents.VILLAGER_NO, 1.0F, getVoicePitch());
    }

    /** Whether it is shaking its head, on either side. */
    public boolean isUnhappy() {
        return entityData.get(UNHAPPY) > 0;
    }

    // Level.

    /**
     * The level of the badge on its belt, as a villager's shows its level: 1 (novice, stone) to 5 (master,
     * diamond), or 0 for none. None here; a guard has one.
     */
    public int villagerLevel() {
        return 0;
    }

    // Zombies.

    /**
     * Gives {@code zombie}, the zombie villager this villager is turning into, what it should keep for the
     * cure beyond its gear, name and clothes. Nothing here; a guard keeps its level, experience and trades.
     */
    protected void carryIntoZombie(ZombieVillager zombie) {
    }

    /** Takes back what {@link #carryIntoZombie} gave {@code zombie}, once its cure brought this villager back. */
    protected void carryFromZombie(ZombieVillager zombie) {
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

    // Pace.

    /**
     * This villager's own pace: {@code permille} thousandths faster (or slower, below 0) than everyone's,
     * within {@link #SPEED_VARIANT_RANGE}, so no two walk quite alike. It applies to every move, walking or
     * running, as a modifier of the speed attribute, which vanilla syncs to clients.
     */
    public void setSpeedVariant(int permille) {
        speedVariant = Math.max(-SPEED_VARIANT_RANGE, Math.min(permille, SPEED_VARIANT_RANGE));
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SPEED_VARIANT_ID);
        if (speedVariant != 0) {
            speed.addTransientModifier(new AttributeModifier(SPEED_VARIANT_ID, speedVariant / 1000.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /** This villager's own pace, in thousandths above or below everyone's. */
    public int speedVariant() {
        return speedVariant;
    }

    // Public, as the gametests read and write them directly. A subclass that saves more calls super first.
    @Override
    public void saveExtra(Output output) {
        super.saveExtra(output);
        output.putString(VILLAGER_TYPE_KEY, villagerType());
        output.putInt(SPEED_VARIANT_KEY, speedVariant);
    }

    @Override
    public void loadExtra(Input input) {
        super.loadExtra(input);
        setVillagerType(input.getString(VILLAGER_TYPE_KEY, DEFAULT_VILLAGER_TYPE));
        // A villager saved before paces keeps the one it drew when it was made.
        setSpeedVariant(input.getInt(SPEED_VARIANT_KEY, speedVariant));
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
