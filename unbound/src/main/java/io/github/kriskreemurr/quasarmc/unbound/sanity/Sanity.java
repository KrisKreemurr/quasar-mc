package io.github.kriskreemurr.quasarmc.unbound.sanity;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;

import io.github.kriskreemurr.quasarmc.unbound.QuasarUnbound;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.LightLayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Sanity, v1: light only. Server-side, once a second per player:
 * <ul>
 * <li>Daylight (dimension with a sky, daytime, sky visible from the eyes): +4/min</li>
 * <li>Pure darkness (light level 0, same as mob spawning): -12.5/min (full to empty in 8 min)</li>
 * <li>Lit by a light source (block light 1+): 0 (safe, not healing)</li>
 * <li>Otherwise lit only by the sky: daytime 0 (indoors/shade), not daytime -4/min</li>
 * </ul>
 * "Daytime" is day ticks 0..11999 of 24000, so sunset and sunrise count as night: a full day
 * outside with no light is exactly even (+40 / -40). Saved with the player; resets on death.
 */
public final class Sanity {
    public static final float MAX = 100f;
    static final float DAY_PER_MIN = 4f, NIGHT_PER_MIN = -4f, DARK_PER_MIN = -12.5f;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, QuasarUnbound.MODID);
    public static final Supplier<AttachmentType<Float>> SANITY = ATTACHMENTS.register("sanity",
            () -> AttachmentType.builder(() -> MAX).serialize(Codec.FLOAT.fieldOf("value")).build());

    /** Last value sent to each player, in dial pixels (5% each); forces a resend when it changes. */
    private static final Map<ServerPlayer, Integer> SENT = new WeakHashMap<>();
    /** Last rate sent to each player; a change of arrow tier is sent at once. */
    private static final Map<ServerPlayer, Float> SENT_RATE = new WeakHashMap<>();
    /** Current rate per player (per minute), worked out once a second. */
    private static final Map<ServerPlayer, Float> RATE = new WeakHashMap<>();
    private static final int RESEND_TICKS = 100;

    private Sanity() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("1")
                .playToClient(SanitySyncPayload.TYPE, SanitySyncPayload.STREAM_CODEC,
                        (payload, ctx) -> {
                            SanityClientState.value = payload.value();
                            SanityClientState.ratePerMinute = payload.ratePerMinute();
                        }));
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) tick(p);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> resync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> resync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> resync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, Sanity::registerCommands);
    }

    public static float get(ServerPlayer p) {
        return p.getData(SANITY);
    }

    public static void set(ServerPlayer p, float value) {
        p.setData(SANITY, Math.max(0f, Math.min(MAX, value)));
        send(p);
    }

    private static void tick(ServerPlayer p) {
        if (p.isSpectator() || p.tickCount % 20 != 0) return;
        float perMin = ratePerMinute(p);
        float v = get(p);
        if (perMin != 0f) {
            float nv = Math.max(0f, Math.min(MAX, v + perMin / 60f));
            if (nv != v) p.setData(SANITY, nv);
            v = nv;
        }
        RATE.put(p, perMin);
        Integer last = SENT.get(p);
        Float lastRate = SENT_RATE.get(p);
        if (last == null || last != pixel(v) || lastRate == null || arrowTier(lastRate) != arrowTier(perMin)
                || p.tickCount % RESEND_TICKS == 0) send(p);
    }

    /** The light rule for where the player's eyes are right now. */
    static float ratePerMinute(ServerPlayer p) {
        ServerLevel level = p.level();
        BlockPos eyes = BlockPos.containing(p.getEyePosition());
        boolean hasSky = level.dimensionType().hasSkyLight();
        boolean day = hasSky && Math.floorMod(level.getDefaultClockTime(), 24000L) < 12000L;
        if (day && level.canSeeSky(eyes)) return DAY_PER_MIN;
        if (level.getMaxLocalRawBrightness(eyes) == 0) return DARK_PER_MIN;
        if (level.getBrightness(LightLayer.BLOCK, eyes) > 0) return 0f;
        return day ? 0f : NIGHT_PER_MIN;
    }

    /**
     * Arrow tier for a rate: 0 none, 1 slow (up to 5/min), 2 medium (up to 15/min), 3 fast. Negative
     * for draining. Daylight/night are slow, darkness is medium, fast is kept for things like monsters.
     */
    public static int arrowTier(float perMin) {
        float a = Math.abs(perMin);
        int t = a == 0f ? 0 : a <= 5f ? 1 : a <= 15f ? 2 : 3;
        return perMin < 0 ? -t : t;
    }

    private static int pixel(float v) {
        return Math.round(v / 5f);
    }

    private static void resync(Object player) {
        if (player instanceof ServerPlayer p) send(p);
    }

    private static void send(ServerPlayer p) {
        float v = get(p);
        float rate = p.isSpectator() ? 0f : RATE.getOrDefault(p, 0f);
        SENT.put(p, pixel(v));
        SENT_RATE.put(p, rate);
        PacketDistributor.sendToPlayer(p, new SanitySyncPayload(v, rate));
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("quasarsanity")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            int pct = IntegerArgumentType.getInteger(ctx, "percent");
                            set(p, pct);
                            ctx.getSource().sendSuccess(() -> Component.literal("Sanity set to " + pct + "%"), true);
                            return 1;
                        })
                        .then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> {
                            int pct = IntegerArgumentType.getInteger(ctx, "percent");
                            var targets = EntityArgument.getPlayers(ctx, "targets");
                            for (ServerPlayer p : targets) set(p, pct);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Sanity set to " + pct + "% for " + targets.size() + " player(s)"), true);
                            return targets.size();
                        }))));
    }
}
