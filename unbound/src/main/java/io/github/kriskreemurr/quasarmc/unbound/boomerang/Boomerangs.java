package io.github.kriskreemurr.quasarmc.unbound.boomerang;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Boomerangs: items per tier, the thrown entity, the catch message. Design: claude/quasar-unbound.md. */
public final class Boomerangs {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, QuasarUnbound.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, QuasarUnbound.MODID);

    public static final Map<BoomerangTier, Supplier<Item>> ITEM = new EnumMap<>(BoomerangTier.class);

    private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, QuasarUnbound.MODID);
    /** the user's sounds (sources in assets/quasarmc_unbound/sounds/boomerang/SOURCES.md) */
    public static final Supplier<net.minecraft.sounds.SoundEvent> FLYING = sound("boomerang.flying");
    public static final Supplier<net.minecraft.sounds.SoundEvent> HIT_ENTITY = sound("boomerang.hit_entity");

    private static Supplier<net.minecraft.sounds.SoundEvent> sound(String name) {
        return SOUNDS.register(name, id -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id));
    }

    public static final Supplier<EntityType<BoomerangEntity>> ENTITY = ENTITIES.register("boomerang",
            id -> EntityType.Builder.<BoomerangEntity>of(BoomerangEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSummon()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    static {
        for (BoomerangTier tier : BoomerangTier.values()) {
            ITEM.put(tier, ITEMS.register(tier.id, id -> {
                Item.Properties p = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))
                        .durability(tier.durability);
                if (tier == BoomerangTier.NETHERITE) p = p.fireResistant();
                return new BoomerangItem(tier, p);
            }));
        }
    }

    private Boomerangs() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, e -> {
            if (e.getTabKey() == CreativeModeTabs.COMBAT) {
                for (BoomerangTier tier : BoomerangTier.values()) e.accept(ITEM.get(tier).get());
            }
        });
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("1")
                .playToServer(BoomerangCatchPayload.TYPE, BoomerangCatchPayload.STREAM_CODEC, (payload, ctx) -> {
                    Player player = ctx.player();
                    BoomerangEntity b = BoomerangEntity.catchableFor(player.level(), player);
                    if (b != null) b.tryCatch(player, payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                }));
    }

    public static boolean isBoomerang(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof BoomerangItem;
    }
}
