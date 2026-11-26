package io.github.kriskreemurr.quasarmc.unbound.boomerang;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Right-click throws the boomerang in that hand (main hand first, then offhand: vanilla's hand order, so dual wield
 * alternates). Only one boomerang per player in flight at a time. The thrown stack leaves your hand, which frees that
 * hand for the catch.
 */
public class BoomerangItem extends Item {
    public final BoomerangTier tier;

    public BoomerangItem(BoomerangTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (BoomerangEntity.inFlightFor(level, player) != null) return InteractionResult.FAIL;
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            ItemStack thrown = stack.copyWithCount(1);
            // one durability per throw (a boomerang on its last point breaks as it leaves your hand)
            thrown.hurtAndBreak(1, server, null, item -> {});
            stack.shrink(1);
            if (!thrown.isEmpty()) {
                BoomerangEntity e = BoomerangEntity.thrown(server, player, thrown, hand, tier);
                server.addFreshEntity(e);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 0.8f, 1.3f);
        }
        return InteractionResult.SUCCESS;
    }
}
