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
 * <li>OUT: a full circle, like a real boomerang: out of the throwing hand, turning steadily toward that hand's side,
 *     about 48 blocks across (its far point), and round back toward you. Aimed up or down, it rises or dips and
 *     comes back to the throwing height. A hit on anything (mob or block) ends the circle.</li>
 * <li>POP: a short upward arc.</li>
 * <li>RETURN: homes on the thrower's eyes (chases you if you move), passing through blocks. When it is at most 1 s
 *     away it paces itself to arrive no sooner than 1 s later (slowing down or lingering); in the last 0.4 s it
 *     dings, and the ding is the catch window. Catch with an empty hand (right-click; {@link BoomerangCatchPayload}). Not caught: it hits you for its
 *     own damage and drops at your feet.</li>
 * </ol>
 * Normal boomerangs hit one target, on the way out. Golden ones also hit on the way back (each target once).
 */
public class BoomerangEntity extends Projectile implements ItemSupplier {
    static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(BoomerangEntity.class, EntityDataSerializers.ITEM_STACK);
    /** catch window open (dinged, not arrived): drives the client's catch check and HUD prompt */
    static final EntityDataAccessor<Boolean> WINDOW = SynchedEntityData.defineId(BoomerangEntity.class, EntityDataSerializers.BOOLEAN);
    /** still whooshing: the flying sound loops until the boomerang first hits something */
    static final EntityDataAccessor<Boolean> WHOOSH = SynchedEntityData.defineId(BoomerangEntity.class, EntityDataSerializers.BOOLEAN);
    /** impact sounds are played louder than normal so the thrower always hears them (range scales with volume) */
    private static final float IMPACT_VOLUME = 2.0f;

    private static final int OUT = 0, POP = 1, RETURN = 2;
    /** blocks per tick: along the circle 2.0 (40/s), homing back 1.6 (32/s) */
    private static final double OUT_SPEED = 2.0, RETURN_SPEED = 1.6;
    /** the circle's diameter: its far point is this far from where it was thrown */
    private static final double CIRCLE_DIAMETER = 48;
    /** after this much of the circle (in turns) it homes in on you for the last stretch (you may have moved) */
    private static final double CIRCLE_HOME_AT = 0.72;
    private static final int POP_TICKS = 6;
    /** how sharply it can turn toward you while homing (fraction of the way per tick) */
    private static final double HOMING_TURN = 0.25;
    /** the ding comes at least this many ticks before arrival (1 s) */
    private static final int CATCH_TICKS = 20;
    /** ...and the catch itself is only possible in the last this-many ticks (0.4 s) */
    private static final int WINDOW_TICKS = 8;
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
    /** how far round the circle it is (radians), and its vertical speed at the start */
    private double circleAngle, startVy;

    public BoomerangEntity(EntityType<? extends BoomerangEntity> type, Level level) {
        super(type, level);
    }

    static BoomerangEntity thrown(ServerLevel level, Player owner, ItemStack stack, InteractionHand hand, BoomerangTier tier) {
        BoomerangEntity e = new BoomerangEntity(Boomerangs.ENTITY.get(), level);
        e.setOwner(owner);
        e.setItem(stack);
        e.hand = hand;
        e.damage = tier.damage;
        e.hitsOnReturn = tier.hitsOnReturn;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? owner.getMainArm() : owner.getMainArm().getOpposite();
        e.side = arm == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 look = owner.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        // out of the throwing hand: a little to that side and below the eyes
        Vec3 start = owner.getEyePosition().add(right.scale(0.35 * e.side)).add(0, -0.25, 0).add(look.scale(0.3));
        e.setPos(start.x, start.y, start.z);
        e.setDeltaMovement(look.scale(OUT_SPEED));
        e.startVy = look.y * OUT_SPEED;
        return e;
    }

    /** this player's boomerang currently in flight in this level, or null (one at a time) */
    static BoomerangEntity inFlightFor(Level level, Player player) {
        for (BoomerangEntity e : level.getEntitiesOfClass(BoomerangEntity.class, player.getBoundingBox().inflate(MAX_OWNER_DIST))) {
            if (!e.isRemoved() && e.getOwner() == player) return e;
        }
        return null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(WINDOW, false);
        builder.define(WHOOSH, true);
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
        Vec3 pos = position();
        Vec3 vel = getDeltaMovement();
        switch (phase) {
            case OUT -> {
                // the circle: turn the horizontal heading at a constant rate (speed / radius) toward the throwing
                // hand's side; the vertical speed follows cos(angle), so it rises (or dips) and returns to the
                // throwing height over the full turn
                double h = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
                double turn = Math.max(h, 0.4) / (CIRCLE_DIAMETER / 2);
                double a = turn * side;
                double cos = Math.cos(a), sin = Math.sin(a);
                circleAngle += turn;
                vel = new Vec3(vel.x * cos - vel.z * sin, startVy * Math.cos(circleAngle), vel.x * sin + vel.z * cos);
                Vec3 next = pos.add(vel);
                HitResult hit = hitAlong(pos, next, true);
                if (hit != null) {
                    if (hit instanceof EntityHitResult eh) strike(level, owner, eh.getEntity());
                    else if (hit instanceof BlockHitResult bh) {
                        // the block's own break sound: you hear what it hit
                        var state = level.getBlockState(bh.getBlockPos());
                        level.playSound(null, bh.getLocation().x, bh.getLocation().y, bh.getLocation().z,
                            state.getSoundType().getBreakSound(), SoundSource.PLAYERS, IMPACT_VOLUME, 1.0f);
                    }
                    entityData.set(WHOOSH, false);
                    if (spendDurability(level, owner)) return;
                    setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
                    // bounce off: up and back toward you, and off the face of a block (never on into what it hit)
                    Vec3 off = hit instanceof BlockHitResult bh2
                        ? Vec3.atLowerCornerOf(bh2.getDirection().getUnitVec3i()) : Vec3.ZERO;
                    startPop(owner, off);
                }
                else {
                    move(next, vel);
                    if (circleAngle >= CIRCLE_HOME_AT * 2 * Math.PI) {
                        // last stretch of the circle: home in on wherever you are now
                        phase = RETURN;
                        phaseTicks = 0;
                        noPhysics = true;
                    }
                }
            }
            case POP -> {
                vel = vel.multiply(0.8, 1, 0.8).add(0, -0.04, 0);
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
                // pacing: from 1 s out it arrives no sooner than 1 s later (slows down or lingers), so the catch
                // window below always gets its full length
                if (dingTick < 0 && dist / RETURN_SPEED <= CATCH_TICKS) dingTick = tickCount;
                double speed = RETURN_SPEED;
                if (dingTick >= 0) {
                    int left = Math.max(1, CATCH_TICKS - (tickCount - dingTick));
                    speed = Math.min(RETURN_SPEED, dist / left);
                }
                // the ding IS the catch window: it plays the moment the window (and the "Catch!" prompt) opens, in
                // the last WINDOW_TICKS before it reaches you (by its real remaining distance, so running away from
                // it doesn't open the window early)
                if (dingTick >= 0 && !catchWindow() && tickCount - dingTick >= CATCH_TICKS - WINDOW_TICKS
                        && dist / Math.max(speed, 1e-3) <= WINDOW_TICKS + 1) {
                    entityData.set(WINDOW, true);
                    level.playSound(null, owner.getX(), owner.getEyeY(), owner.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(),
                        SoundSource.PLAYERS, 1.0f, 1.8f);
                }
                if (dist < 0.6 && catchWindow() && tickCount - dingTick >= CATCH_TICKS) {
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

    /** the bounce after a hit: upward, back toward the thrower, and away from the surface it hit */
    private void startPop(Player owner, Vec3 surfaceNormal) {
        phase = POP;
        phaseTicks = 0;
        Vec3 back = owner.position().subtract(position()).multiply(1, 0, 1);
        back = back.lengthSqr() > 1e-6 ? back.normalize().scale(0.3) : Vec3.ZERO;
        setDeltaMovement(back.add(surfaceNormal.scale(0.25)).add(0, 0.5, 0));
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
            bh = level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();
            else bh = null;
        }
        AABB sweep = getBoundingBox().expandTowards(end.subtract(a)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level(), this, a, end, sweep, this::canHitEntity);
        if (eh != null) return eh;
        return bh;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return target != getOwner() && target.isPickable() && !target.isSpectator() && target.isAlive()
            && !(target instanceof BoomerangEntity);
    }

    private void strike(ServerLevel level, Player owner, Entity target) {
        target.hurtServer(level, damageSources().thrown(this, owner), damage);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), Boomerangs.HIT_ENTITY.get(), SoundSource.PLAYERS,
            IMPACT_VOLUME, 0.9f + random.nextFloat() * 0.2f);
    }

    /** caught with an empty hand (server, from the catch payload) */
    boolean tryCatch(Player player, InteractionHand with) {
        if (player != getOwner() || !catchWindow() || isRemoved() || !player.getItemInHand(with).isEmpty()) return false;
        player.setItemInHand(with, getItem().copy());
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
            discard();
            return true;
        }
        setItem(stack);
        return false;
    }

    private void missedCatch(ServerLevel level, Player owner) {
        owner.hurtServer(level, damageSources().thrown(this, owner), damage);
        level.playSound(null, owner.getX(), owner.getEyeY(), owner.getZ(), Boomerangs.HIT_ENTITY.get(), SoundSource.PLAYERS,
            IMPACT_VOLUME, 0.8f);
        owner.spawnAtLocation(level, getItem().copy());
        discard();
    }

    private void dropHere() {
        if (level() instanceof ServerLevel level && !getItem().isEmpty()) spawnAtLocation(level, getItem().copy());
        discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.store("Item", ItemStack.OPTIONAL_CODEC, getItem());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.read("Item", ItemStack.OPTIONAL_CODEC).ifPresent(this::setItem);
        // a boomerang loaded from disk just comes home
        phase = RETURN;
        noPhysics = true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 64 * 64;
    }
}
