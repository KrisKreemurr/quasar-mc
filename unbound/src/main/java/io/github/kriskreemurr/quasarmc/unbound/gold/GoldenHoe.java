package io.github.kriskreemurr.quasarmc.unbound.gold;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Golden hoe abilities (the hoe itself keeps gold's 32 durability):
 *
 *   Golden harvest: right-click a ripe crop to harvest every ripe crop in a 5x5
 *   square around it. Crops are replanted for free and drop twice (double yield).
 *   Costs 1 durability per crop.
 *
 *   3x3 tilling: tilling dirt also tills the 8 blocks around it.
 *   Costs 1 durability per extra block.
 */
public final class GoldenHoe {
    public static final int HARVEST_RADIUS = 2; // 5x5
    public static final int TILL_RADIUS = 1;    // 3x3

    private GoldenHoe() {
    }

    public static void register(IEventBus gameEventBus) {
        gameEventBus.addListener(GoldenHoe::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.GOLDEN_HOE) || event.getEntity().isSecondaryUseActive()) {
            return;
        }
        BlockState clicked = event.getLevel().getBlockState(event.getPos());

        if (clicked.getBlock() instanceof CropBlock crop && crop.isMaxAge(clicked)) {
            if (event.getLevel() instanceof ServerLevel level) {
                harvest(level, event.getEntity(), event, stack);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (event.getFace() != Direction.DOWN && isTillable(clicked)
                && event.getLevel().getBlockState(event.getPos().above()).isAir()
                && event.getLevel() instanceof ServerLevel level) {
            // Vanilla tills the clicked block itself; we only add the ring around it.
            tillAround(level, event.getEntity(), event, stack);
        }
    }

    private static void harvest(ServerLevel level, Player player, PlayerInteractEvent.RightClickBlock event, ItemStack stack) {
        BlockPos center = event.getPos();
        boolean any = false;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-HARVEST_RADIUS, 0, -HARVEST_RADIUS),
                center.offset(HARVEST_RADIUS, 0, HARVEST_RADIUS))) {
            if (stack.isEmpty()) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
                continue;
            }
            BlockPos at = pos.immutable();
            Item seed = state.getBlock().asItem();
            boolean seedTaken = false;
            for (int roll = 0; roll < 2; roll++) { // double yield
                List<ItemStack> drops = Block.getDrops(state, level, at, null, player, stack);
                for (ItemStack drop : drops) {
                    if (!seedTaken && drop.is(seed)) {
                        drop.shrink(1); // the replant uses one seed
                        seedTaken = true;
                    }
                    if (!drop.isEmpty()) {
                        Block.popResource(level, at, drop);
                    }
                }
            }
            level.setBlock(at, crop.getStateForAge(0), Block.UPDATE_ALL);
            stack.hurtAndBreak(1, player, event.getHand());
            any = true;
        }
        if (any) {
            level.playSound(null, center, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private static void tillAround(ServerLevel level, Player player, PlayerInteractEvent.RightClickBlock event, ItemStack stack) {
        BlockPos center = event.getPos();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-TILL_RADIUS, 0, -TILL_RADIUS),
                center.offset(TILL_RADIUS, 0, TILL_RADIUS))) {
            if (stack.isEmpty()) {
                break;
            }
            if (pos.equals(center)) {
                continue;
            }
            if (isTillable(level.getBlockState(pos)) && level.getBlockState(pos.above()).isAir()) {
                level.setBlock(pos.immutable(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
                stack.hurtAndBreak(1, player, event.getHand());
            }
        }
    }

    /** The blocks vanilla turns straight into farmland. */
    private static boolean isTillable(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH);
    }
}
