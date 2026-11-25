package io.github.kriskreemurr.quasarmc.unbound.gold;

import java.util.ArrayList;
import java.util.List;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Golden tools: absurd speed, gold's vanilla durability (32).
 *
 *   Mining speed 12 -> 20 on the golden pickaxe, shovel and hoe.
 *   Efficiency V gives 46: the golden pickaxe insta-mines stone without Haste,
 *   and the golden shovel insta-mines every shovel block even unenchanted.
 *
 * Mining tier: gold tools now count as diamond tier. That part is data, not code:
 * data/minecraft/tags/block/incorrect_for_gold_tool.json is emptied, like diamond's.
 */
public final class GoldenTools {
    public static final float MINING_SPEED = 20.0F;

    private GoldenTools() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(GoldenTools::modifyComponents);
    }

    private static void modifyComponents(ModifyDefaultComponentsEvent event) {
        event.modify(Items.GOLDEN_PICKAXE, (builder, registries, item) -> setSpeed(builder, item, MINING_SPEED));
        event.modify(Items.GOLDEN_SHOVEL, (builder, registries, item) -> setSpeed(builder, item, MINING_SPEED));
        event.modify(Items.GOLDEN_HOE, (builder, registries, item) -> setSpeed(builder, item, MINING_SPEED));
    }

    /** Every tool rule that sets a mining speed gets the new speed; deny-drops rules stay as they are. */
    private static void setSpeed(DataComponentMap.Builder builder, Item item, float speed) {
        Tool old = builder.get(DataComponents.TOOL);
        if (old == null) {
            QuasarUnbound.LOGGER.warn("[Unbound] {} has no tool component, speed not changed", item);
            return;
        }
        List<Tool.Rule> rules = new ArrayList<>();
        for (Tool.Rule rule : old.rules()) {
            if (rule.speed().isPresent()) {
                rules.add(new Tool.Rule(rule.blocks(), java.util.Optional.of(speed), rule.correctForDrops()));
            } else {
                rules.add(rule);
            }
        }
        builder.set(DataComponents.TOOL, new Tool(List.copyOf(rules), old.defaultMiningSpeed(),
                old.damagePerBlock(), old.canDestroyBlocksInCreative()));
    }
}
