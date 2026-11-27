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
 * up after {@link #ANGRY_TICKS} or if you get away.
 */
public final class PettyAnimals {
    /** damage (half-hearts) and knockback per type */
    private record Fight(double damage, double knockback) {}
    private static final Map<EntityType<?>, Fight> FIGHTERS = Map.of(
        EntityTypes.CHICKEN, new Fight(1.0, 0.0),
        EntityTypes.SHEEP, new Fight(1.0, 0.3),
        EntityTypes.PIG, new Fight(2.0, 0.0),
        EntityTypes.COW, new Fight(2.0, 1.0),
        EntityTypes.MOOSHROOM, new Fight(2.0, 1.0));
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
            mob.goalSelector.addGoal(1, new PettyAttackGoal(mob));
            mob.targetSelector.addGoal(1, new HurtByTargetGoal(mob) {
                @Override
                public boolean canUse() {
                    return !mob.isBaby() && super.canUse();
                }
            });
        });
    }

    /** melee for a few seconds after being hit, then back to grazing */
    private static final class PettyAttackGoal extends MeleeAttackGoal {
        private final PathfinderMob mob;

        PettyAttackGoal(PathfinderMob mob) {
            super(mob, 1.25, false);
            this.mob = mob;
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
