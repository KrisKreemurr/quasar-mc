package io.github.kriskreemurr.quasarmc.unbound.gold;

import java.util.function.Supplier;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Flint and Gold: the Nether-side way home. Lights fire and portals like flint and steel, but is made from flint and
 * a gold ingot (both easy to get in the Nether, where iron is scarce). Gold's rule: the best at its job, for a short
 * time - 16 uses, no repair or Mending ({@link GoldenGear}).
 *
 * Planned (with the portal frame work): opens a Nether portal frame with up to a fifth of its obsidian missing, the
 * gaps filled with gilded obsidian.
 *
 * TNT is primed through {@link #igniteTnt} (vanilla TNT only knows flint and steel and fire charges).
 *
 * Texture by KrisKreemurr.
 */
public final class FlintAndGold {
    public static final int USES = 16;

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, QuasarUnbound.MODID);

    public static final Supplier<Item> ITEM = ITEMS.register("flint_and_gold",
            id -> new FlintAndSteelItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))
                    .durability(USES)));

    private FlintAndGold() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, e -> {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(ITEM.get());
        });
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, FlintAndGold::igniteTnt);
    }

    /**
     * TNT checks for flint and steel / fire charge by item, not by tag, so it would just ignore Flint and Gold. Same
     * as vanilla's TntBlock.useItemOn: prime it (onCaughtFire), remove the block, use up one durability.
     */
    private static void igniteTnt(PlayerInteractEvent.RightClickBlock e) {
        net.minecraft.world.item.ItemStack stack = e.getItemStack();
        if (!stack.is(ITEM.get())) return;
        net.minecraft.world.level.Level level = e.getLevel();
        net.minecraft.core.BlockPos pos = e.getPos();
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.TntBlock tnt)) return;
        net.minecraft.world.entity.player.Player player = e.getEntity();
        if (tnt.onCaughtFire(state, level, pos, e.getFace(), player, stack)) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 11);
            stack.hurtAndBreak(1, player, e.getHand().asEquipmentSlot());
            player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(stack.getItem()));
        }
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        e.setCanceled(true);
    }
}
