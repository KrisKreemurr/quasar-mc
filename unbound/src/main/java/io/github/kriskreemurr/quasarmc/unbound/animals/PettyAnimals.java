package io.github.kriskreemurr.quasarmc.unbound.animals;

import java.util.Map;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Petty farm animals (user, 2026-11-27): chickens, sheep, pigs and cows stay neutral, but hit one and it fights back for
 * a few seconds with petty damage instead of panicking: chicken peck 1/2 heart, sheep headbutt 1/2 heart with a small
 * shove, pig 1 heart, cow 1 heart with Knockback I. Babies still run. They give
 * up after {@link #ANGRY_TICKS} or if you get away. Horses, donkeys and mules bite in front and buck whoever is behind
 * them; tamed ones never go after their owner.
 */
public final class PettyAnimals {
    /**
     * damage (half-hearts) and knockback per type; horses also have a rear kick ({@code kick} damage, {@code kickKnockback}
     * in Knockback levels) for whoever is behind them, 0 = none
     */
    private record Fight(double damage, double knockback, float kick, float kickKnockback) {
        Fight(double damage, double knockback) {
            this(damage, knockback, 0f, 0f);
        }
    }
    private static final Map<EntityType<?>, Fight> FIGHTERS = Map.of(
        EntityTypes.CHICKEN, new Fight(1.0, 0.0),
        EntityTypes.SHEEP, new Fight(1.0, 0.3),
        EntityTypes.PIG, new Fight(2.0, 0.0),
        EntityTypes.COW, new Fight(2.0, 1.0),
        EntityTypes.MOOSHROOM, new Fight(2.0, 1.0),
        // horses: a 1-heart bite in front, a 3.5-heart Knockback II buck behind; donkeys and mules: 2 hearts, Knockback I
        EntityTypes.HORSE, new Fight(2.0, 0.0, 7f, 2f),
        EntityTypes.DONKEY, new Fight(2.0, 0.0, 4f, 1f),
        EntityTypes.MULE, new Fight(2.0, 0.0, 4f, 1f));
    /** how long one stays angry after being hit (5 s) */
    private static final int ANGRY_TICKS = 100;
    /** it gives up if you get further away than this */
    private static final double GIVE_UP_DISTANCE = 12;

    private PettyAnimals() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityAttributeModificationEvent.class, e -> {
            for (Map.Entry<EntityType<?>, Fight> f : FIGHTERS.entrySet()) {
                @SuppressWarnings("unchecked")
                EntityType<? extends LivingEntity> type = (EntityType<? extends LivingEntity>) f.getKey();
                if (!e.has(type, Attributes.ATTACK_DAMAGE)) e.add(type, Attributes.ATTACK_DAMAGE, f.getValue().damage());
                if (!e.has(type, Attributes.ATTACK_KNOCKBACK)) e.add(type, Attributes.ATTACK_KNOCKBACK, f.getValue().knockback());
            }
        });
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, e -> {
            if (e.getLevel().isClientSide() || !(e.getEntity() instanceof PathfinderMob mob)) return;
            if (!FIGHTERS.containsKey(mob.getType())) return;
            // grown-ups fight back instead of panicking; babies keep panicking (the panic goal checks nothing about age,
            // so it is kept and the attack goals only run for adults)
            mob.goalSelector.removeAllGoals(g -> g instanceof PanicGoal && !mob.isBaby());
            mob.goalSelector.addGoal(1, new PettyAttackGoal(mob, FIGHTERS.get(mob.getType())));
            mob.targetSelector.addGoal(1, new HurtByTargetGoal(mob) {
                @Override
                public boolean canUse() {
                    // a tamed animal forgives its owner
                    if (mob instanceof net.minecraft.world.entity.OwnableEntity own && own.getOwner() != null
                            && own.getOwner() == mob.getLastHurtByMob()) return false;
                    return !mob.isBaby() && super.canUse();
                }
            });
        });
    }

    /** melee for a few seconds after being hit, then back to grazing */
    private static final class PettyAttackGoal extends MeleeAttackGoal {
        private final PathfinderMob mob;
        private final Fight fight;
        private int kickCooldown;

        PettyAttackGoal(PathfinderMob mob, Fight fight) {
            super(mob, 1.25, false);
            this.mob = mob;
            this.fight = fight;
        }

        @Override
        public void tick() {
            if (kickCooldown > 0) kickCooldown--;
            LivingEntity t = mob.getTarget();
            if (fight.kick() > 0 && t != null && kickCooldown == 0 && behind(t) && mob.distanceToSqr(t) < 2.5 * 2.5
                    && mob.level() instanceof net.minecraft.server.level.ServerLevel level) {
                // the buck: whoever stands behind a horse gets both hind legs
                kickCooldown = 30;
                if (t.hurtServer(level, mob.damageSources().mobAttack(mob), fight.kick())) {
                    net.minecraft.world.phys.Vec3 away = t.position().subtract(mob.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() > 1e-6 ? away.normalize() : net.minecraft.world.phys.Vec3.ZERO;
                    double strength = 0.5 * fight.kickKnockback();
                    t.push(away.x * strength, 0.25 + 0.1 * fight.kickKnockback(), away.z * strength);
                    t.needsSync = true;
                }
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), net.minecraft.sounds.SoundEvents.HORSE_ANGRY,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
                return;
            }
            super.tick();
        }

        /** the target is behind the animal (more than 120 degrees from where its body faces) */
        private boolean behind(LivingEntity t) {
            double yaw = Math.toRadians(mob.yBodyRot);
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            double dx = t.getX() - mob.getX(), dz = t.getZ() - mob.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            return len > 1e-6 && (fx * dx + fz * dz) / len < -0.5;
        }

        private boolean stillAngry() {
            LivingEntity t = mob.getTarget();
            boolean angry = !mob.isBaby() && t != null && t.isAlive()
                && mob.tickCount - mob.getLastHurtByMobTimestamp() < ANGRY_TICKS
                && mob.distanceToSqr(t) < GIVE_UP_DISTANCE * GIVE_UP_DISTANCE;
            if (!angry && t != null) mob.setTarget(null); // calm down
            return angry;
        }

        @Override
        public boolean canUse() {
            return stillAngry() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return stillAngry() && super.canContinueToUse();
        }
    }
}
