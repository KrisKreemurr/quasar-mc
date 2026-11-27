package io.github.kriskreemurr.quasarmc.unbound.boomerang;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Hold right-click to wind up (trident pose), release to throw; the wind-up sets the power (a flick is a short,
 * slower, weaker throw; 0.75 s is full power; a tap does nothing). Throws the boomerang in that hand (main hand first, then offhand: vanilla's hand order, so dual wield
 * alternates). One boomerang in the air per hand, so a dual wielder can have both out. The thrown stack leaves your
 * hand, which frees that hand for the catch.
 */
public class BoomerangItem extends Item {
    public final BoomerangTier tier;

    public BoomerangItem(BoomerangTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** ticks of wind-up for a full-power throw (0.75 s), and the least that throws at all (a tap does nothing) */
    static final int FULL_WINDUP = 15, MIN_WINDUP = 4;

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // one boomerang in the air per hand; PASS (not FAIL) lets the other hand have its turn
        if (BoomerangEntity.inFlightFor(level, player, hand) != null) return InteractionResult.PASS;
        // wind up: hold right-click (trident pose), release to throw
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return false;
        int held = getUseDuration(stack, entity) - timeLeft;
        if (held < MIN_WINDUP) return false;
        float power = Math.min(1f, held / (float) FULL_WINDUP);
        InteractionHand hand = player.getUsedItemHand();
        if (BoomerangEntity.inFlightFor(level, player, hand) != null) return false;
        if (level instanceof ServerLevel server) {
            // durability is spent when it hits something, not on the throw (BoomerangEntity)
            ItemStack thrown = stack.copyWithCount(1);
            stack.shrink(1);
            BoomerangEntity e = BoomerangEntity.thrown(server, player, thrown, hand, tier, power);
            server.addFreshEntity(e);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 0.6f + 0.3f * power, 1.5f - 0.3f * power);
        }
        return true;
    }
}
