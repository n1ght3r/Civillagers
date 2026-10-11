package com.civillagers.entity.guard;

import com.civillagers.ai.guard.GuardBowGoal;
import com.civillagers.ai.guard.GuardCrossbowGoal;
import com.civillagers.ai.guard.GuardMeleeGoal;
import com.civillagers.ai.guard.GuardPatrolGoal;
import com.civillagers.ai.guard.GuardRaidGoal;
import com.civillagers.ai.guard.GuardRallyGoal;
import com.civillagers.ai.guard.GuardShieldGoal;
import com.civillagers.ai.guard.GuardSleepGoal;
import com.civillagers.ai.guard.GuardTargetGoal;
import com.civillagers.ai.guard.GuardTradeGoal;
import com.civillagers.api.CivTags;
import com.civillagers.api.GuardEvents;
import com.civillagers.api.Verdict;
import com.civillagers.entity.CivVillager;
import com.civillagers.platform.Vanilla;
import com.civillagers.platform.ZombieVillagerOffers;
import com.civillagers.village.Village;
import com.civillagers.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
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
 * <p><b>Levels.</b> A guard has a villager's five levels, novice to master, with vanilla's experience
 * thresholds ({@link #XP_TO_REACH}) and the badge of its level on its belt. It gains experience two ways:
 * trading, as a villager does ({@link GuardTrades}), and fighting: a mob it kills gives it the
 * experience it would drop for a player ({@link #killed}).
 * A new level adds no stat: it upgrades the guard's own gear ({@link GuardGear}) and teaches it two more
 * trades. A spawn egg makes a guard of any level, at random.
 *
 * <p><b>Home.</b> Like an iron golem, a guard keeps to its village: the last one it stood in is its home,
 * and it gives up a chase more than {@link #LEASH} blocks past the edge and walks back. It defends the
 * villagers first: a mob going after one is the target it picks before a nearer one.
 *
 * <p><b>Its own gear and a player's.</b> What a guard wears for its level is its own: it never drops,
 * never wears out, and nobody takes it off. A piece a player hands over goes on top of it, always drops,
 * and is the one piece the player can take back; the guard's own piece waits under it, keeps upgrading
 * with the levels, and goes back on when the player's comes off. Which is which is the slot's drop
 * chance, which a zombie villager keeps through turning and the cure: above 1 for a player's piece.
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
public class Guard extends CivVillager implements Merchant {
    /** How far a guard looks for hostile mobs, horizontally, in blocks. */
    public static final double THREAT_RANGE = 16.0;
    private static final double THREAT_HEIGHT = 6.0;
    /** Ticks between two points of health regained out of combat. */
    private static final int REGEN_INTERVAL = 80;

    /** The first filter on what a scan returns; {@link #goesAfter} does the rest. No capture, so no allocation. */
    private static final Predicate<Mob> MAY_THREATEN = mob -> mob instanceof Enemy && mob.isAlive();

    /** The highest level, master. */
    public static final int MAX_LEVEL = 5;
    /** The experience a guard needs to reach each level, by level: vanilla's thresholds for a villager. */
    public static final int[] XP_TO_REACH = { 0, 0, 10, 70, 150, 250 };
    /** Experience stops adding up here, far past master, so it never overflows. */
    private static final int MAX_XP = 1_000_000;
    /** Ticks between enough experience for a level and the level, out of trading: vanilla's two seconds. */
    public static final int LEVEL_UP_DELAY = 40;
    /** Game ticks between two restocks of its trades: twice a day, as often as a villager at most. */
    public static final long RESTOCK_INTERVAL = 12_000L;
    private static final int RESTOCK_CHECK_INTERVAL = 100;
    /** How far past its village's edge, in blocks, a guard follows a fight before it turns back, as a golem keeps to its village. */
    public static final double LEASH = 16.0;
    /** Ticks between two looks at which village the guard stands in, and at how far it strayed. */
    private static final int HOME_CHECK_INTERVAL = 20;
    /** A mob going after a villager counts as this much nearer, squared: a guard protects villagers first. */
    private static final double DEFENDS_VILLAGERS = 0.25;
    /** Ticks of regeneration a new level gives, as it gives a villager. */
    private static final int LEVEL_UP_REGENERATION = 200;

    private static final String LEVEL_KEY = "level";
    private static final String XP_KEY = "xp";
    private static final String OFFERS_KEY = "offers";
    private static final String RESTOCK_KEY = "last_restock";
    private static final String OWN_GEAR_KEY = "own_";
    private static final String HOME_KEY = "home";
    private static final String HOME_RADIUS_KEY = "home_radius";
    /** Synced, so clients draw the badge of its level. */
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(Guard.class, EntityDataSerializers.INT);

    private int xp;
    private MerchantOffers offers = new MerchantOffers();
    /** The player trading with it, while the trading screen is open; never saved. */
    private Player tradingPlayer;
    /** Ticks left before the level its experience has earned; 0 for none due. */
    private int levelUpIn;
    /** The game time of the last restock; 0 until the first look. */
    private long lastRestock;
    /** The center and radius of the village the guard last stood in: its home, which it keeps to. No home: radius 0. */
    private long homeCenter;
    private int homeRadius;
    /** The guard's own gear under a player's, by {@link GuardGear#SLOTS}; empty where none is under. */
    private final ItemStack[] ownUnder = new ItemStack[GuardGear.SLOTS.length];

    public Guard(EntityType<? extends Guard> type, Level level) {
        super(type, level);
        Arrays.fill(ownUnder, ItemStack.EMPTY);
        // Its own gear never drops; a player's is set to always drop as it goes on.
        for (EquipmentSlot slot : GuardGear.SLOTS) setDropChance(slot, 0.0F);
    }

    /** A villager's attributes, and a base attack of 1: the weapon adds its own damage, as for a player. */
    public static AttributeSupplier.Builder createAttributes() {
        return createVillagerAttributes().add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LEVEL, 1);
    }

    @Override
    protected void registerRoleGoals() {
        goalSelector.addGoal(1, new GuardTradeGoal(this));
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
     * Here too, every tick even at rest: it stops trading to fight, takes the level its experience earned
     * once nobody trades with it, and restocks its trades.
     */
    @Override
    public void aiStep() {
        if (!level().isClientSide()) {
            // A dead target is let go of here too: one set straight, as in a raid, has no target goal to drop it.
            if (getTarget() != null && (!isCombatant() || !getTarget().isAlive())) setTarget(null);
            if (tickCount % REGEN_INTERVAL == 0 && getTarget() == null && getHealth() < getMaxHealth()) heal(1.0F);
            if (tradingPlayer != null && (getTarget() != null || !(tradingPlayer.containerMenu instanceof MerchantMenu)
                    || !stillValid(tradingPlayer))) {
                stopTrading();
            }
            if (tradingPlayer == null && levelUpIn > 0 && --levelUpIn == 0) {
                levelUp();
                if (canLevelUp()) levelUpIn = LEVEL_UP_DELAY;
            }
            if (tickCount % RESTOCK_CHECK_INTERVAL == 0) restockIfDue();
            if ((tickCount + getId()) % HOME_CHECK_INTERVAL == 0) keepToHome();
        }
        super.aiStep();
    }

    @Override
    public void die(DamageSource source) {
        stopTrading();
        super.die(source);
    }

    // Threats.

    /**
     * The nearest hostile mob within {@link #THREAT_RANGE} that this guard can see and
     * {@linkplain #goesAfter goes after}, or null; one going after a villager counts as half as far, so the
     * guard takes it first. One bounded box query; the caller rate-limits it.
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
                double distance = distanceToSqr(mob) * (attacksVillager(mob) ? DEFENDS_VILLAGERS : 1.0);
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

    /** Whether {@code mob} is going after a villager, or a villager of the mod that does not fight. */
    public static boolean attacksVillager(Mob mob) {
        LivingEntity target = mob.getTarget();
        return target instanceof AbstractVillager || target instanceof CivVillager villager && !villager.isCombatant();
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

    // Home.

    /**
     * Keeps to its village, as an iron golem does: notes the village it stands in as its home, and lets go
     * of a target it chased more than {@link #LEASH} blocks past the home's edge, to walk back
     * ({@link GuardPatrolGoal} heads home from outside). One village lookup, a hash read. Public for the gametests.
     */
    public void keepToHome() {
        ServerLevel level = serverLevel();
        if (level == null) return;
        Village village = Villages.get(level).villageAt(blockPosition());
        if (village != null) {
            homeCenter = village.center().asLong();
            homeRadius = village.radius();
        } else if (getTarget() != null && !nearHome(getX(), getZ(), LEASH)) {
            setTarget(null);
        }
    }

    /** Whether the guard has a home village, the last one it stood in. */
    public boolean hasHome() {
        return homeRadius > 0;
    }

    /** The center of its home village; meaningful only with {@link #hasHome()}. */
    public BlockPos homeCenter() {
        return BlockPos.of(homeCenter);
    }

    /** Whether {@code x}, {@code z} is within {@code margin} blocks of its home village's edge; always, with no home. */
    public boolean nearHome(double x, double z, double margin) {
        if (!hasHome()) return true;
        double dx = x - (BlockPos.getX(homeCenter) + 0.5);
        double dz = z - (BlockPos.getZ(homeCenter) + 0.5);
        double reach = homeRadius + margin;
        return dx * dx + dz * dz <= reach * reach;
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

    // Levels and experience.

    /** Its level, 1 (novice) to 5 (master), on either side. */
    @Override
    public int villagerLevel() {
        return entityData.get(LEVEL);
    }

    /** Sets its level, 1 to {@link #MAX_LEVEL}, as is: its gear, trades and experience stay as they are. */
    public void setVillagerLevel(int level) {
        entityData.set(LEVEL, Math.max(1, Math.min(level, MAX_LEVEL)));
    }

    /**
     * Gains {@code amount} experience, from a trade or a kill. Once it is enough for the next
     * level, the guard takes it {@link #LEVEL_UP_DELAY} ticks later, when nobody is trading with it.
     */
    public void addXp(int amount) {
        if (amount <= 0) return;
        xp = (int) Math.min((long) xp + amount, MAX_XP);
        if (levelUpIn == 0 && canLevelUp()) levelUpIn = LEVEL_UP_DELAY;
    }

    /** Whether its experience is enough for a level above the one it has. */
    public boolean canLevelUp() {
        int level = villagerLevel();
        return level < MAX_LEVEL && xp >= XP_TO_REACH[level + 1];
    }

    /**
     * Goes up one level: its own gear upgraded ({@link GuardGear#upgrade}), two more trades, and, as for a
     * villager, ten seconds of regeneration and happy particles. Nothing happens at master.
     */
    public void levelUp() {
        int from = villagerLevel();
        if (from >= MAX_LEVEL) return;
        MerchantOffers known = getOffers();
        for (EquipmentSlot slot : GuardGear.SLOTS) {
            setOwnGear(slot, GuardGear.upgrade(slot, ownGear(slot), from, getRandom(), registryAccess()));
        }
        setVillagerLevel(from + 1);
        GuardTrades.learn(from + 1, known, getRandom(), registryAccess());
        addEffect(new MobEffectInstance(MobEffects.REGENERATION, LEVEL_UP_REGENERATION, 0));
        level().broadcastEntityEvent(this, EntityEvent.VILLAGER_HAPPY);
        playSound(SoundEvents.VILLAGER_CELEBRATE, 1.0F, getVoicePitch());
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id != EntityEvent.VILLAGER_HAPPY) {
            super.handleEntityEvent(id);
            return;
        }
        for (int i = 0; i < 5; i++) {
            level().addParticle(ParticleTypes.HAPPY_VILLAGER, getRandomX(1.0), getRandomY() + 1.0, getRandomZ(1.0),
                    getRandom().nextGaussian() * 0.02, getRandom().nextGaussian() * 0.02, getRandom().nextGaussian() * 0.02);
        }
    }

    /**
     * Gains the {@code experience} a mob it just killed is worth: what it would drop for a player, as
     * orbs. Called from {@code mixin/LivingEntityMixin}; nothing drops for the guard.
     */
    public void killed(int experience) {
        addXp(experience);
    }

    // Trading.

    @Override
    public void setTradingPlayer(Player player) {
        tradingPlayer = player;
    }

    @Override
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    /** Its trades. A guard that has none yet (made without spawning, or saved before guards traded) learns those of every level it has. */
    @Override
    public MerchantOffers getOffers() {
        if (offers.isEmpty() && !level().isClientSide()) {
            for (int level = 1; level <= villagerLevel(); level++) GuardTrades.learn(level, offers, getRandom(), registryAccess());
        }
        return offers;
    }

    /** Only a client's copy of a merchant takes offers from outside; the guard keeps its own. */
    @Override
    public void overrideOffers(MerchantOffers offers) {
    }

    /** A trade was made: the offer is used once, the guard gains its experience and, as from a villager, the player a few orbs. */
    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        ambientSoundTime = -getAmbientSoundInterval();
        boolean couldLevelUp = canLevelUp();
        addXp(offer.getXp());
        if (offer.shouldRewardExp() && tradingPlayer != null) {
            int orbs = 3 + getRandom().nextInt(4) + (!couldLevelUp && canLevelUp() ? 5 : 0);
            // At the player's feet, where vanilla puts them by the villager.
            level().addFreshEntity(new ExperienceOrb(level(), tradingPlayer.getX(), tradingPlayer.getY() + 0.5, tradingPlayer.getZ(), orbs));
        }
    }

    /** Says yes or no to what the player puts in the trading slots, as a villager does, at most once a second. */
    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        if (level().isClientSide() || ambientSoundTime <= -getAmbientSoundInterval() + 20) return;
        ambientSoundTime = -getAmbientSoundInterval();
        playSound(stack.isEmpty() ? SoundEvents.VILLAGER_NO : SoundEvents.VILLAGER_YES, 1.0F, getVoicePitch());
    }

    @Override
    public int getVillagerXp() {
        return xp;
    }

    /** Only a client's copy of a merchant takes experience from outside. */
    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return true;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean canRestock() {
        return true;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide();
    }

    /** Whether {@code player} may go on trading: the one trading, alive and near. Not part of 1.21.1's merchant, so not marked as an override. */
    public boolean stillValid(Player player) {
        return tradingPlayer == player && isAlive() && player.isAlive() && distanceToSqr(player) <= 64.0;
    }

    /** Opens its trades to {@code player}, or shakes its head while it fights, sleeps or trades with someone else. */
    private InteractionResult startTrading(Player player) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (tradingPlayer != null || isSleeping() || getTarget() != null || getOffers().isEmpty()) {
            refuse();
            return InteractionResult.SUCCESS;
        }
        setTradingPlayer(player);
        getNavigation().stop();
        openTradingScreen(player, getDisplayName(), villagerLevel());
        return InteractionResult.SUCCESS;
    }

    /** Ends a trade, closing the player's trading screen if it is still open. */
    public void stopTrading() {
        Player player = tradingPlayer;
        tradingPlayer = null;
        if (player instanceof ServerPlayer server && server.containerMenu instanceof MerchantMenu) server.closeContainer();
    }

    /** At most every {@link #RESTOCK_INTERVAL} game ticks, and only once a trade was used, every trade is full again. */
    private void restockIfDue() {
        long now = level().getGameTime();
        if (lastRestock == 0L) lastRestock = now;
        if (now - lastRestock < RESTOCK_INTERVAL || tradingPlayer != null) return;
        boolean used = false;
        for (int i = 0; i < offers.size(); i++) used |= offers.get(i).getUses() > 0;
        if (!used) return;
        for (int i = 0; i < offers.size(); i++) {
            offers.get(i).updateDemand();
            offers.get(i).resetUses();
        }
        lastRestock = now;
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

    /** Whether a player handed over the piece in {@code slot}: it always drops, and the player can take it back. */
    public boolean isPlayerGear(EquipmentSlot slot) {
        return dropChance(slot) > 1.0F;
    }

    /** The guard's own gear in {@code slot}, worn or waiting under a player's piece; empty for none. */
    public ItemStack ownGear(EquipmentSlot slot) {
        return isPlayerGear(slot) ? ownUnder[gearIndex(slot)] : getItemBySlot(slot);
    }

    /** Sets the guard's own gear in {@code slot}: worn, or kept under the player's piece until that comes off. */
    public void setOwnGear(EquipmentSlot slot, ItemStack stack) {
        if (isPlayerGear(slot)) {
            ownUnder[gearIndex(slot)] = stack;
        } else {
            setItemSlot(slot, stack);
            setDropChance(slot, 0.0F);
        }
    }

    /**
     * Puts one of {@code stack} on the guard and returns the player's piece it wore in that slot before:
     * empty if the slot held none, or held the guard's own, which waits under the new one. Null if it does
     * not take the item. Gear a player hands over always drops when the guard dies.
     */
    public ItemStack equipGear(ItemStack stack) {
        EquipmentSlot slot = gearSlot(stack);
        if (slot == null) return null;
        ItemStack previous = ItemStack.EMPTY;
        if (isPlayerGear(slot)) previous = getItemBySlot(slot);
        else ownUnder[gearIndex(slot)] = getItemBySlot(slot);
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
     * Takes off the first piece a player handed over, in {@link #HAND_BACK_ORDER}, puts the guard's own
     * back on, and returns the player's piece, or an empty stack: the guard's own gear never comes off. A
     * crossbow comes back unloaded: the guard's bolt was never the player's.
     */
    public ItemStack takeGear() {
        for (EquipmentSlot slot : HAND_BACK_ORDER) {
            if (!isPlayerGear(slot)) continue;
            ItemStack worn = getItemBySlot(slot);
            int index = gearIndex(slot);
            setItemSlot(slot, ownUnder[index]);
            setDropChance(slot, 0.0F);
            ownUnder[index] = ItemStack.EMPTY;
            if (worn.isEmpty()) continue;
            if (worn.getItem() instanceof CrossbowItem) worn.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            return worn;
        }
        return ItemStack.EMPTY;
    }

    private static int gearIndex(EquipmentSlot slot) {
        for (int i = 0; i < GuardGear.SLOTS.length; i++) if (GuardGear.SLOTS[i] == slot) return i;
        throw new IllegalArgumentException("A guard has no gear in " + slot);
    }

    /**
     * A sneaking player with an empty hand takes back a piece they gave; one holding a piece of gear hands it
     * over; anything else opens the guard's trades, as a villager's.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() && player.isSecondaryUseActive()) {
            if (!level().isClientSide()) give(player, takeGear());
            return InteractionResult.SUCCESS;
        }
        if (held.isEmpty() || gearSlot(held) == null) return startTrading(player);
        if (!level().isClientSide()) {
            ItemStack previous = equipGear(held);
            if (!player.hasInfiniteMaterials()) held.shrink(1);
            give(player, previous);
            playSound(SoundEvents.VILLAGER_YES, 1.0F, getVoicePitch());
        }
        return InteractionResult.SUCCESS;
    }

    // Zombies.

    /** A zombie guard keeps its level, experience and trades in its villager data, as a zombie villager keeps a villager's. */
    @Override
    protected void carryIntoZombie(ZombieVillager zombie) {
        stopTrading();
        Vanilla.setVillagerLevel(zombie, villagerLevel());
        zombie.setVillagerXp(xp);
        zombie.setTradeOffers(getOffers().copy());
    }

    @Override
    protected void carryFromZombie(ZombieVillager zombie) {
        setVillagerLevel(Vanilla.villagerLevelOf(zombie));
        xp = Math.max(zombie.getVillagerXp(), 0);
        MerchantOffers kept = ((ZombieVillagerOffers) zombie).civ$tradeOffers();
        if (kept != null) offers = kept.copy();
    }

    // Spawning and saving.

    /**
     * A guard from a spawn egg is of any level, 1 to 5, at random, with that level's experience; any other
     * new guard is a novice. Then its gear for that level, and the trades of every level it has.
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason reason, SpawnGroupData data) {
        int start = Vanilla.fromSpawnEgg(reason) ? 1 + getRandom().nextInt(MAX_LEVEL) : 1;
        setVillagerLevel(start);
        xp = XP_TO_REACH[start];
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        offers = new MerchantOffers();
        getOffers();
        return result;
    }

    /** A new guard's own gear for its level, rolled from {@link GuardGear}'s tables. */
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        for (EquipmentSlot slot : GuardGear.SLOTS) setOwnGear(slot, GuardGear.roll(slot, villagerLevel(), random, registryAccess()));
    }

    @Override
    public void saveExtra(Output output) {
        super.saveExtra(output);
        output.putInt(LEVEL_KEY, villagerLevel());
        output.putInt(XP_KEY, xp);
        output.putLong(RESTOCK_KEY, lastRestock);
        output.putLong(HOME_KEY, homeCenter);
        output.putInt(HOME_RADIUS_KEY, homeRadius);
        if (!offers.isEmpty()) output.store(OFFERS_KEY, MerchantOffers.CODEC, offers);
        for (int i = 0; i < GuardGear.SLOTS.length; i++) {
            if (!ownUnder[i].isEmpty()) output.store(OWN_GEAR_KEY + GuardGear.SLOTS[i].getName(), ItemStack.CODEC, ownUnder[i]);
        }
    }

    @Override
    public void loadExtra(Input input) {
        super.loadExtra(input);
        setVillagerLevel(input.getInt(LEVEL_KEY, 1));
        xp = Math.max(input.getInt(XP_KEY, 0), 0);
        lastRestock = input.getLong(RESTOCK_KEY, 0L);
        homeCenter = input.getLong(HOME_KEY, 0L);
        homeRadius = Math.max(input.getInt(HOME_RADIUS_KEY, 0), 0);
        offers = input.read(OFFERS_KEY, MerchantOffers.CODEC).orElseGet(MerchantOffers::new);
        for (int i = 0; i < GuardGear.SLOTS.length; i++) {
            ownUnder[i] = input.read(OWN_GEAR_KEY + GuardGear.SLOTS[i].getName(), ItemStack.CODEC).orElse(ItemStack.EMPTY);
        }
        if (canLevelUp()) levelUpIn = LEVEL_UP_DELAY;
    }
}
