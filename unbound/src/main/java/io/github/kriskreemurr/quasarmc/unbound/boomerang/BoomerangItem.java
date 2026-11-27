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
 * alternates). One boomerang in the air per hand, so a dual wielder can have both out. The thrown stack leaves your
 * hand, which frees that hand for the catch.
 */
public class BoomerangItem extends Item {
    public final BoomerangTier tier;

    public BoomerangItem(BoomerangTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // one boomerang in the air per hand; PASS (not FAIL) lets the other hand have its turn
        if (BoomerangEntity.inFlightFor(level, player, hand) != null) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            // durability is spent when it hits something, not on the throw (BoomerangEntity)
            ItemStack thrown = stack.copyWithCount(1);
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
