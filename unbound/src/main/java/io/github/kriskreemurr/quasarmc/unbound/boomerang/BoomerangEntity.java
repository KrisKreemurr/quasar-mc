package io.github.kriskreemurr.quasarmc.unbound.boomerang;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown boomerang. Flight (server side; the client only interpolates and spins):
 * <ol>
 * <li>OUT: a capsule: straight along your crosshair (up or down too) for about 32 blocks, so you hit what you aim
 *     at, then a tight U-turn toward the throwing hand's side, then it homes back. A hit on anything (mob or block)
 *     ends it.</li>
 * <li>POP: a short upward arc.</li>
 * <li>RETURN: homes on the thrower's eyes at full speed (chases you if you move), passing through blocks. About
 *     0.5 s out it dings and paces itself to arrive exactly 0.5 s later: the ding is the catch window. Catch with an
 *     empty hand (right-click; {@link BoomerangCatchPayload}). Not caught: it hits you for its own damage and drops
 *     at your feet.</li>
 * </ol>
 * Normal boomerangs hit one target, on the way out. Golden ones also hit on the way back (each target once).
 */
public class BoomerangEntity extends Projectile implements ItemSupplier {
    static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(BoomerangEntity.class, EntityDataSerializers.ITEM_STACK);
    /** catch window open (dinged, not arrived): drives the client's catch check and HUD prompt */
    static final EntityDataAccessor<Boolean> WINDOW = SynchedEntityData.defineId(BoomerangEntity.class, EntityDataSerializers.BOOLEAN);
    /** still whooshing: the flying sound loops until the boomerang first hits something */
    /** thrown from the offhand (each hand can have its own boomerang in the air) */
    static final EntityDataAccessor<Boolean> OFFHAND = SynchedEntityData.defineId(BoomerangEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> WHOOSH = SynchedEntityData.defineId(BoomerangEntity.class, EntityDataSerializers.BOOLEAN);
    /** impact sounds are played louder than normal so the thrower always hears them (range scales with volume) */
    private static final float IMPACT_VOLUME = 2.0f;
    /** the punch sounds: 60% loudness (friends found them loud); their 32-block range comes from sounds.json */
    private static final float HIT_VOLUME = 0.6f;

    private static final int OUT = 0, POP = 1, RETURN = 2, CHAIN = 3;
    /** blocks per tick: along the circle 2.0 (40/s), homing back 1.6 (32/s) */
    private static final double OUT_SPEED = 2.0, RETURN_SPEED = 1.6;
    /** the capsule: straight this far along the aim, then a U-turn of this radius toward the hand's side */
    private static final double STRAIGHT_BLOCKS = 32, TURN_RADIUS = 8;
    /** after turning this far (radians) it homes in on you (you may have moved) */
    private static final double TURN_HOME_AT = Math.PI * 0.85;
    private static final int POP_TICKS = 6;
    /** how sharply it can turn toward you while homing (fraction of the way per tick) */
    private static final double HOMING_TURN = 0.25;
    /** the catch window: from the ding to arrival, exactly this many ticks (0.5 s) */
    private static final int WINDOW_TICKS = 10;
    /** given up on (owner gone or too far): dropped where it is */
    private static final double MAX_OWNER_DIST = 96;
    /** durability is spent on the first impact of a throw (a throw that hits nothing costs nothing) */
    private boolean spent;

    private int phase = OUT, phaseTicks, dingTick = -1;
    private float damage = 5f;
    private boolean hitsOnReturn;
    /** +1 curve right, -1 curve left */
    private int side = 1;
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    private final Set<Integer> hitOnReturn = new HashSet<>();
    /** Multi-Target: bounces left, the first target's group, who was hit, the target it is flying to, bounces done */
    private int chainLeft, chainDone;
    private BoomerangChain.Group chainGroup;
    private final Set<Integer> chainHit = new HashSet<>();
    private Entity chainTarget;
    /** Treasure Collector: level, and the loot carried home */
    private int collectorLevel;
    private final java.util.List<ItemStack> carried = new java.util.ArrayList<>();
    private int carriedXp;
    private boolean chainBlocked;
    /** a Tidalrang: flies through water, and the surface (or lava) is its wall */
    private boolean tidal;
    /** a Sniper-Rang: long straight leg, wide turn, damage by distance from where it was thrown */
    private boolean sniper;
    private Vec3 thrownFrom = Vec3.ZERO;
    private float sniperMax = 12f;
    /** Cracker-Rang carrying gunpowder: its first hit (or a missed catch) explodes */
    private boolean live;
    /** the Cracker-Rang blast: a 1-star firework's (7 at the centre, falling off to 0 at 5 blocks) */
    public static final float CRACKER_BLAST = 7f;
    static final double CRACKER_RADIUS = 5;

    void arm() {
        live = true;
    }

    /**
     * The blast, vanilla firework style: everything alive within 5 blocks with a clear line takes 7 * sqrt((5 - d) / 5),
     * thrower included; no blocks broken. Shields and Blast Protection work (explosion damage). Every client nearby
     * draws a firework burst in random colours.
     */
    private void explode(ServerLevel level, Player owner, Vec3 at) {
        live = false;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 96,
            new CrackerBurstPayload(at.x, at.y, at.z, level.getRandom().nextLong()));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(CRACKER_RADIUS))) {
            // distance to the nearest point of its body (not its feet), so whatever it hit takes the full blast
            AABB bb = e.getBoundingBox();
            double d = Math.sqrt(new Vec3(Math.max(bb.minX, Math.min(at.x, bb.maxX)), Math.max(bb.minY, Math.min(at.y, bb.maxY)),
                Math.max(bb.minZ, Math.min(at.z, bb.maxZ))).distanceToSqr(at));
            if (d > CRACKER_RADIUS || !e.isAlive()) continue;
            boolean seen = false;
            for (int k = 0; k < 2 && !seen; k++) {
                Vec3 to = new Vec3(e.getX(), e.getY(0.5 * k), e.getZ());
                seen = level.clip(new ClipContext(at, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType()
                    == HitResult.Type.MISS;
            }
            if (!seen) continue;
            float dmg = (float) (CRACKER_BLAST * Math.sqrt((CRACKER_RADIUS - d) / CRACKER_RADIUS));
            e.hurtServer(level, damageSources().explosion(this, owner), dmg);
        }
    }
    /** the U-turn's radius (wider for the Sniper-Rang) */
    private double turnRadius = TURN_RADIUS;
    /** Sniper-Rang: 4 damage within 8 blocks, rising to 12 at 56 blocks and beyond */
    public static final float SNIPER_MIN = 4f, SNIPER_NEAR = 8f, SNIPER_FAR = 56f, SNIPER_RANGE = 64f, SNIPER_TURN = 14f;

    static float sniperDamage(double dist, float max) {
        double t = Math.max(0, Math.min(1, (dist - SNIPER_NEAR) / (SNIPER_FAR - SNIPER_NEAR)));
        return (float) (SNIPER_MIN + (max - SNIPER_MIN) * t);
    }
    /** distance flown straight, and how far round the U-turn it is (radians) */
    private double straightFlown, circleAngle, startVy;
    /** this throw's speed and straight-leg length (set by the wind-up) */
    private double outSpeed = OUT_SPEED, straightLength = STRAIGHT_BLOCKS;

    public BoomerangEntity(EntityType<? extends BoomerangEntity> type, Level level) {
        super(type, level);
    }

    static BoomerangEntity thrown(ServerLevel level, Player owner, ItemStack stack, InteractionHand hand, BoomerangTier tier,
                                  float power) {
        BoomerangEntity e = new BoomerangEntity(Boomerangs.ENTITY.get(), level);
        e.setOwner(owner);
        e.setItem(stack);
        e.hand = hand;
        e.entityData.set(OFFHAND, hand == InteractionHand.OFF_HAND);
        // wind-up power: a flick is short, slower and weaker; full power is the full capsule at full speed and damage
        e.damage = tier.damage * (0.5f + 0.5f * power);
        e.outSpeed = OUT_SPEED * (0.55 + 0.45 * power);
        e.straightLength = 8 + (STRAIGHT_BLOCKS - 8) * power;
        e.hitsOnReturn = tier.hitsOnReturn;
        e.tidal = tier.underwater;
        if (tier.sniper()) {
            // the reach is the wind-up's job (a flick can't fly far, so it can't hit hard); damage comes from distance
            e.sniper = true;
            e.straightLength = 8 + (SNIPER_RANGE - 8) * power;
            e.turnRadius = SNIPER_TURN;
            e.damage = SNIPER_MIN; // a missed catch with no hit: the close-range hit
            e.sniperMax = tier.damage;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? owner.getMainArm() : owner.getMainArm().getOpposite();
        e.side = arm == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 look = owner.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        // out of the throwing hand: a little to that side and below the eyes
        Vec3 start = owner.getEyePosition().add(right.scale(0.35 * e.side)).add(0, -0.25, 0).add(look.scale(0.3));
        e.setPos(start.x, start.y, start.z);
        e.thrownFrom = start;
        e.setDeltaMovement(look.scale(e.outSpeed));
        e.startVy = look.y * e.outSpeed;
        e.chainLeft = BoomerangChain.level(level, stack);
        e.collectorLevel = BoomerangCollector.level(level, stack);
        return e;
    }

    /** the hand this boomerang was thrown from */
    public InteractionHand thrownFrom() {
        return entityData.get(OFFHAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    /** this player's boomerang in flight that was thrown from {@code hand}, or null (one per hand) */
    static BoomerangEntity inFlightFor(Level level, Player player, InteractionHand hand) {
        for (BoomerangEntity e : level.getEntitiesOfClass(BoomerangEntity.class, player.getBoundingBox().inflate(MAX_OWNER_DIST))) {
            if (!e.isRemoved() && e.getOwner() == player && e.thrownFrom() == hand) return e;
        }
        return null;
    }

    /** this player's boomerang with its catch window open (the one that opened first), or null */
    static BoomerangEntity catchableFor(Level level, Player player) {
        BoomerangEntity best = null;
        for (BoomerangEntity e : level.getEntitiesOfClass(BoomerangEntity.class, player.getBoundingBox().inflate(MAX_OWNER_DIST))) {
            if (!e.isRemoved() && e.getOwner() == player && e.catchWindow() && (best == null || e.dingTick < best.dingTick)) best = e;
        }
        return best;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(WINDOW, false);
        builder.define(WHOOSH, true);
        builder.define(OFFHAND, false);
    }

    @Override
    public ItemStack getItem() {
        return entityData.get(ITEM);
    }

    void setItem(ItemStack stack) {
        entityData.set(ITEM, stack.copy());
    }

    public boolean catchWindow() {
        return entityData.get(WINDOW);
    }

    public boolean whooshing() {
        return entityData.get(WHOOSH);
    }

    private boolean loopStarted;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            // position comes from the server; the renderer spins it. The flying loop follows it until the first hit.
            if (!loopStarted && whooshing()) {
                loopStarted = true;
                io.github.kriskreemurr.quasarmc.unbound.boomerang.client.BoomerangClient.startFlyingSound(this);
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        Entity ownerEntity = getOwner();
        if (!(ownerEntity instanceof Player owner) || !owner.isAlive() || owner.level() != level
                || owner.distanceToSqr(this) > MAX_OWNER_DIST * MAX_OWNER_DIST) {
            dropHere();
            return;
        }
        phaseTicks++;
        if (collectorLevel > 0) collect(level, owner);
        if (live) level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.02);
        if (tidal && isInWater() && tickCount % 2 == 0)
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE, getX(), getY(), getZ(), 2, 0.1, 0.1, 0.1, 0.02);
        Vec3 pos = position();
        Vec3 vel = getDeltaMovement();
        switch (phase) {
            case OUT -> {
                if (straightFlown < straightLength) {
                    // the straight leg: exactly along the aim, like an arrow without the drop
                    straightFlown += vel.length();
                }
                else {
                    // the U-turn: turn the horizontal heading at a constant rate (speed / radius) toward the throwing
                    // hand's side, levelling out (the homing brings it back to your height)
                    double h = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
                    if (h < 0.4) {
                        // thrown (nearly) straight up or down: there's no heading to turn, and "levelling out" used
                        // to keep it climbing at full speed (a 64-block Sniper-Rang throw went past the 96-block
                        // give-up distance and dropped). Turn for home right away: the homing curve brings it back.
                        circleAngle = TURN_HOME_AT;
                    }
                    else {
                        double turn = outSpeed / turnRadius;
                        double a = turn * side;
                        double cos = Math.cos(a), sin = Math.sin(a);
                        circleAngle += turn;
                        double vy = vel.y * 0.8;
                        double hs = Math.sqrt(Math.max(0, outSpeed * outSpeed - vy * vy)) / h;
                        vel = new Vec3((vel.x * cos - vel.z * sin) * hs, vy, (vel.x * sin + vel.z * cos) * hs);
                    }
                }
                Vec3 next = pos.add(vel);
                HitResult hit = hitAlong(pos, next, true);
                if (hit != null) {
                    if (hit instanceof EntityHitResult eh) {
                        if (sniper) damage = sniperDamage(thrownFrom.distanceTo(hit.getLocation()), sniperMax);
                        // a live Cracker-Rang's hit is the blast (the target is at its centre)
                        if (live) explode(level, owner, hit.getLocation());
                        else strike(level, owner, eh.getEntity(), 1f);
                        chainGroup = BoomerangChain.classify(eh.getEntity(), owner);
                        chainHit.add(eh.getEntity().getId());
                    }
                    else if (hit instanceof BlockHitResult bh) {
                        // the block's own break sound: you hear what it hit
                        var state = level.getBlockState(bh.getBlockPos());
                        boolean water = !state.getFluidState().isEmpty() && state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
                        boolean lava = state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
                        // a Tidalrang leaving the water: splash at the surface, sizzle on lava
                        var sound = lava ? SoundEvents.LAVA_EXTINGUISH
                            : water || (tidal && state.getCollisionShape(level, bh.getBlockPos()).isEmpty()) ? SoundEvents.GENERIC_SPLASH
                            : state.getSoundType().getBreakSound();
                        level.playSound(null, bh.getLocation().x, bh.getLocation().y, bh.getLocation().z,
                            sound, SoundSource.PLAYERS, IMPACT_VOLUME, 1.0f);
                        if (live) explode(level, owner, bh.getLocation());
                    }
                    entityData.set(WHOOSH, false);
                    if (spendDurability(level, owner)) return;
                    setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
                    if (hit instanceof EntityHitResult && startChain(level)) return;
                    // bounce off: up and back toward you, and off the face of a block (never on into what it hit)
                    Vec3 off = hit instanceof BlockHitResult bh2
                        ? Vec3.atLowerCornerOf(bh2.getDirection().getUnitVec3i()) : Vec3.ZERO;
                    startPop(owner, off);
                }
                else {
                    move(next, vel);
                    if (circleAngle >= TURN_HOME_AT) {
                        // last stretch of the circle: home in on wherever you are now
                        phase = RETURN;
                        phaseTicks = 0;
                        noPhysics = true;
                    }
                }
            }
            case CHAIN -> {
                Entity t = chainTarget;
                boolean loot = t instanceof ItemEntity || t instanceof net.minecraft.world.entity.ExperienceOrb;
                if (loot && t.isRemoved()) {
                    // swept up on the way (Treasure Collector): on to the next one
                    if (!startChain(level)) startPop(owner, Vec3.ZERO);
                    break;
                }
                if (t == null || t.isRemoved() || !t.isAlive() || t.distanceToSqr(this) > 24 * 24 || phaseTicks > 40) {
                    startPop(owner, Vec3.ZERO);
                    break;
                }
                // a sharp turn straight at the next target; only that target counts as a hit
                Vec3 aim = t.getBoundingBox().getCenter().subtract(pos);
                double dist = aim.length();
                vel = dist < 1e-6 ? Vec3.ZERO : aim.scale(outSpeed / dist);
                Vec3 next = pos.add(vel);
                if (dist <= outSpeed + 0.5 || t.getBoundingBox().inflate(0.3).clip(pos, next).isPresent()) {
                    setPos(t.getBoundingBox().getCenter().x, t.getBoundingBox().getCenter().y, t.getBoundingBox().getCenter().z);
                    chainHit.add(t.getId());
                    if (loot) collect(level, owner);
                    else {
                        chainDone++;
                        strike(level, owner, t, (float) Math.pow(BoomerangChain.FALLOFF, chainDone));
                    }
                    if (!startChain(level)) startPop(owner, Vec3.ZERO);
                }
                else {
                    HitResult wall = hitAlong(pos, next, true);
                    if (wall instanceof BlockHitResult wb) {
                        setPos(wb.getLocation().x, wb.getLocation().y, wb.getLocation().z);
                        startPop(owner, Vec3.atLowerCornerOf(wb.getDirection().getUnitVec3i()));
                    }
                    else move(next, vel);
                }
            }
            case POP -> {
                vel = tidal ? vel.scale(0.8) : vel.multiply(0.8, 1, 0.8).add(0, -0.04, 0);
                move(pos.add(vel), vel);
                if (phaseTicks >= POP_TICKS) {
                    phase = RETURN;
                    phaseTicks = 0;
                    noPhysics = true;
                }
            }
            default -> {
                Vec3 target = owner.getEyePosition().add(0, -0.3, 0);
                Vec3 to = target.subtract(pos);
                double dist = to.length();
                // full speed home; once the catch window opens (ding) it paces itself to arrive exactly
                // WINDOW_TICKS later, so the window always gets its full length - and a point-blank return is quick
                // instead of lingering for a whole second
                double speed = RETURN_SPEED;
                if (!catchWindow() && dist / RETURN_SPEED <= WINDOW_TICKS) {
                    entityData.set(WINDOW, true);
                    dingTick = tickCount;
                    level.playSound(null, owner.getX(), owner.getEyeY(), owner.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(),
                        SoundSource.PLAYERS, 1.0f, 1.8f);
                }
                if (catchWindow()) {
                    int left = Math.max(1, WINDOW_TICKS - (tickCount - dingTick));
                    speed = Math.min(RETURN_SPEED, dist / left);
                }
                if (dist < 0.6 && catchWindow() && tickCount - dingTick >= WINDOW_TICKS) {
                    missedCatch(level, owner);
                    return;
                }
                // steer toward you (a smooth homing curve, not a snap), at the speed worked out above
                Vec3 step;
                if (dist < 1e-6) step = Vec3.ZERO;
                else {
                    Vec3 want = to.scale(1 / dist);
                    Vec3 cur = vel.lengthSqr() > 1e-8 ? vel.normalize() : want;
                    Vec3 dir = cur.add(want.subtract(cur).scale(HOMING_TURN));
                    if (dir.lengthSqr() < 1e-8 || dist < 3) dir = want;
                    step = dir.normalize().scale(Math.min(speed, dist));
                }
                if (hitsOnReturn) {
                    HitResult hit = hitAlong(pos, pos.add(step), false);
                    if (hit instanceof EntityHitResult eh && hitOnReturn.add(eh.getEntity().getId())) {
                        strike(level, owner, eh.getEntity());
                    }
                }
                move(pos.add(step), step);
            }
        }
    }

    /** Multi-Target: pick the next target in the first target's group (nearest, in sight, not hit yet) and go */
    private boolean startChain(ServerLevel level) {
        if (chainLeft <= 0 || chainGroup == null || chainBlocked || !(getOwner() instanceof Player owner)) return false;
        Entity best = null;
        double bestD = BoomerangChain.RANGE * BoomerangChain.RANGE;
        Vec3 from = position();
        for (Entity c : level.getEntities(this, getBoundingBox().inflate(BoomerangChain.RANGE))) {
            if (chainHit.contains(c.getId()) || BoomerangChain.classify(c, owner) != chainGroup) continue;
            Vec3 cc = c.getBoundingBox().getCenter();
            double d = cc.distanceToSqr(from);
            if (d >= bestD) continue;
            if (level.clip(new ClipContext(from, cc, ClipContext.Block.COLLIDER, fluidMode(), this)).getType()
                    != HitResult.Type.MISS) continue; // not in sight
            best = c;
            bestD = d;
        }
        if (best == null && collectorLevel > 0) {
            // no mob left: leftover bounces fly to loot in range (Treasure Collector + Multi-Target)
            for (Entity c : level.getEntities(this, getBoundingBox().inflate(BoomerangChain.RANGE))) {
                if (chainHit.contains(c.getId()) || !BoomerangCollector.collectable(c, owner)) continue;
                Vec3 cc = c.getBoundingBox().getCenter();
                double d = cc.distanceToSqr(from);
                if (d >= bestD) continue;
                if (level.clip(new ClipContext(from, cc, ClipContext.Block.COLLIDER, fluidMode(), this)).getType()
                        != HitResult.Type.MISS) continue;
                best = c;
                bestD = d;
            }
        }
        if (best == null) return false;
        chainLeft--;
        chainTarget = best;
        phase = CHAIN;
        phaseTicks = 0;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6f, 1.6f + 0.15f * chainDone);
        return true;
    }

    /** Treasure Collector: pull in loot within the level's radius */
    private void collect(ServerLevel level, Player owner) {
        double r = BoomerangCollector.radius(collectorLevel);
        boolean got = false;
        for (Entity e : level.getEntities(this, getBoundingBox().inflate(r))) {
            if (!BoomerangCollector.collectable(e, owner) || e.distanceToSqr(this) > r * r) continue;
            if (e instanceof ItemEntity item) {
                carried.add(item.getItem().copy());
                item.discard();
                got = true;
            }
            else if (e instanceof net.minecraft.world.entity.ExperienceOrb orb) {
                carriedXp += orb.getValue();
                orb.discard();
                got = true;
            }
        }
        if (got) level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.6f);
    }

    /** the loot goes to the catcher (spills if the inventory is full) */
    private void deliverLoot(Player player) {
        for (ItemStack s : carried) {
            player.getInventory().add(s);
            if (!s.isEmpty() && level() instanceof ServerLevel l) player.spawnAtLocation(l, s);
        }
        carried.clear();
        if (carriedXp > 0) player.giveExperiencePoints(carriedXp);
        carriedXp = 0;
    }

    /** the loot falls where the boomerang is (or at {@code at}) */
    private void spillLoot(ServerLevel level, Vec3 at) {
        for (ItemStack s : carried) {
            net.minecraft.world.entity.item.ItemEntity ie = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, s);
            level.addFreshEntity(ie);
        }
        carried.clear();
        if (carriedXp > 0) net.minecraft.world.entity.ExperienceOrb.award(level, at, carriedXp);
        carriedXp = 0;
    }

    /** the bounce after a hit: upward, back toward the thrower, and away from the surface it hit */
    private void startPop(Player owner, Vec3 surfaceNormal) {
        phase = POP;
        phaseTicks = 0;
        Vec3 back = owner.position().subtract(position()).multiply(1, 0, 1);
        back = back.lengthSqr() > 1e-6 ? back.normalize().scale(0.3) : Vec3.ZERO;
        // a Tidalrang floats: no upward hop (that would throw it out of the water), just off the wall and back to you
        setDeltaMovement(tidal ? back.add(surfaceNormal.scale(0.4)) : back.add(surfaceNormal.scale(0.25)).add(0, 0.5, 0));
    }

    private void move(Vec3 next, Vec3 vel) {
        setDeltaMovement(vel);
        setPos(next.x, next.y, next.z);
    }

    /** the first block (if blocks count) or entity hit between a and b */
    private HitResult hitAlong(Vec3 a, Vec3 b, boolean blocks) {
        Vec3 end = b;
        BlockHitResult bh = null;
        if (blocks) {
            // water counts as solid: a boomerang can't fly into it (it bounces off the surface)
            bh = level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, fluidMode(), this));
            if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();
            else bh = null;
            if (tidal) {
                // the Tidalrang's walls: wherever the water stops (the surface from below, an air pocket, lava)
                BlockHitResult out = leavesWater(a, end);
                if (out != null) {
                    bh = out;
                    end = out.getLocation();
                }
            }
        }
        AABB sweep = getBoundingBox().expandTowards(end.subtract(a)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level(), this, a, end, sweep, this::canHitEntity);
        if (eh != null) return eh;
        return bh;
    }

    /** normal boomerangs treat water as solid; a Tidalrang sees through it */
    private ClipContext.Fluid fluidMode() {
        return tidal ? ClipContext.Fluid.NONE : ClipContext.Fluid.WATER;
    }

    /** the first point between a and b that is out of the water (null if it stays in), as a hit on that block */
    private BlockHitResult leavesWater(Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int steps = Math.max(1, (int) Math.ceil(d.length() / 0.25));
        for (int k = 1; k <= steps; k++) {
            Vec3 p = a.add(d.scale(k / (double) steps));
            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(p);
            if (level().getFluidState(bp).is(net.minecraft.tags.FluidTags.WATER)) continue;
            Vec3 at = a.add(d.scale((k - 1) / (double) steps));
            // the face it came through: against the main direction of travel (up into the surface = the DOWN face)
            net.minecraft.core.Direction face = net.minecraft.core.Direction.getApproximateNearest(-d.x, -d.y, -d.z);
            return new BlockHitResult(at, face, bp, false);
        }
        return null;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return target != getOwner() && target.isPickable() && !target.isSpectator() && target.isAlive()
            && !(target instanceof BoomerangEntity);
    }

    private void strike(ServerLevel level, Player owner, Entity target) {
        strike(level, owner, target, 1f);
    }

    /** {@code scale}: this hit's share of the boomerang's damage (Multi-Target bounces fall off) */
    /** true if it did damage (false: blocked by a shield, or immune) */
    private boolean strike(ServerLevel level, Player owner, Entity target, float scale) {
        // Power and Punch (their vanilla files are overridden to accept boomerangs, see data/minecraft/enchantment)
        var source = damageSources().thrown(this, owner);
        ItemStack weapon = getItem();
        float dmg = net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level, weapon, target, source, damage) * scale;
        boolean hurt = target.hurtServer(level, source, dmg);
        float punch = net.minecraft.world.item.enchantment.EnchantmentHelper.modifyKnockback(level, weapon, target, source, 0f);
        if (hurt && punch > 0 && target instanceof LivingEntity living) {
            double resist = Math.max(0, 1 - living.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
            Vec3 push = getDeltaMovement().multiply(1, 0, 1);
            if (push.lengthSqr() > 1e-6) {
                push = push.normalize().scale(punch * 0.6 * resist);
                living.push(push.x, 0.1, push.z);
            }
        }
        if (hurt) net.minecraft.world.item.enchantment.EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, weapon);
        else chainBlocked = true; // a shield (or immunity) stops a Multi-Target chain: it deflects home
        if (hurt) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(), Boomerangs.HIT_ENTITY.get(), SoundSource.PLAYERS,
                HIT_VOLUME, 0.9f + random.nextFloat() * 0.2f);
        }
        return hurt;
    }

    /** caught with an empty hand (server, from the catch payload) */
    boolean tryCatch(Player player, InteractionHand with) {
        if (player != getOwner() || !catchWindow() || isRemoved() || !player.getItemInHand(with).isEmpty()) return false;
        player.setItemInHand(with, getItem().copy());
        deliverLoot(player);
        level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7f, 1.1f);
        discard();
        return true;
    }

    /** one durability on the first impact of a throw; true if the boomerang broke (it is then gone) */
    private boolean spendDurability(ServerLevel level, Player owner) {
        if (spent) return false;
        spent = true;
        if (owner.getAbilities().instabuild) return false; // creative: no wear, like vanilla tools
        ItemStack stack = getItem().copy();
        stack.hurtAndBreak(1, level, owner instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null, item -> {});
        if (stack.isEmpty()) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
            spillLoot(level, position());
            discard();
            return true;
        }
        setItem(stack);
        return false;
    }

    private void missedCatch(ServerLevel level, Player owner) {
        // it hits you exactly like it hits anything else: Power, Punch and all (user: stay consistent) - and a
        // Cracker-Rang that hasn't gone off yet goes off on you
        if (live) explode(level, owner, owner.getBoundingBox().getCenter());
        else strike(level, owner, owner);
        owner.spawnAtLocation(level, getItem().copy());
        spillLoot(level, owner.position());
        discard();
    }

    private void dropHere() {
        if (level() instanceof ServerLevel level) {
            if (!getItem().isEmpty()) spawnAtLocation(level, getItem().copy());
            spillLoot(level, position());
        }
        discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.store("Item", ItemStack.OPTIONAL_CODEC, getItem());
        out.putBoolean("Tidal", tidal);
        out.putBoolean("Sniper", sniper);
        out.putBoolean("Live", live);
        out.store("Carried", ItemStack.OPTIONAL_CODEC.listOf(), new java.util.ArrayList<>(carried));
        out.putInt("CarriedXp", carriedXp);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.read("Item", ItemStack.OPTIONAL_CODEC).ifPresent(this::setItem);
        in.read("Carried", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(l -> { carried.clear(); carried.addAll(l); });
        carriedXp = in.getIntOr("CarriedXp", 0);
        tidal = in.getBooleanOr("Tidal", false);
        sniper = in.getBooleanOr("Sniper", false);
        live = in.getBooleanOr("Live", false);
        if (sniper) turnRadius = SNIPER_TURN;
        // a boomerang loaded from disk just comes home
        phase = RETURN;
        noPhysics = true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 64 * 64;
    }
}
