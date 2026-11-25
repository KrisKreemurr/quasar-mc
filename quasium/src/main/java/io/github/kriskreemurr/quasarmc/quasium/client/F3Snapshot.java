package io.github.kriskreemurr.quasarmc.quasium.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.kriskreemurr.quasarmc.quasium.Quasium;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F3 snapshot: one key (F7 by default) copies everything the F3 screen would show to the clipboard, without opening
 * F3. Drawing the F3 text every frame costs frame time itself, so numbers read with F3 open are slightly worse than
 * normal play; the snapshot gathers the same lines once, at the moment you press the key.
 *
 * <p>Takes every F3 entry set to "Always" or "In F3" (F3+F6 to choose), in F3's order, plus the [Quasium] lines
 * (frame stats measured over the last 5 seconds with F3 closed). Colour codes are stripped. Also saved to
 * {@code quasium/f3-snapshots/} in the game folder.</p>
 */
public final class F3Snapshot {
    private static final KeyMapping KEY = new KeyMapping("key.quasarmc_quasium.f3_snapshot",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_F7, KeyMapping.Category.DEBUG);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter HEADER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private F3Snapshot() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(KEY));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> {
            while (KEY.consumeClick()) take();
        });
    }

    private static void take() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            String text = collect(mc);
            mc.keyboardHandler.setClipboard(text);
            String saved = save(mc, text);
            mc.player.sendOverlayMessage(Component.literal("F3 snapshot copied to clipboard"
                    + (saved != null ? " (saved as " + saved + ")" : "")));
        } catch (Throwable t) {
            Quasium.LOGGER.warn("F3 snapshot failed", t);
            mc.player.sendOverlayMessage(Component.literal("F3 snapshot failed: " + t));
        }
    }

    private static String collect(Minecraft mc) {
        DebugScreenOverlay overlay = mc.getDebugOverlay();
        Level level = call(overlay, "getLevel", Level.class);
        if (level == null) level = mc.level;
        LevelChunk clientChunk = call(overlay, "getClientChunk", LevelChunk.class);
        if (clientChunk == null && mc.getCameraEntity() != null) {
            ChunkPos cp = ChunkPos.containing(mc.getCameraEntity().blockPosition());
            clientChunk = mc.level.getChunk(cp.x(), cp.z());
        }
        LevelChunk serverChunk = call(overlay, "getServerChunk", LevelChunk.class);

        List<String> priority = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        Map<Identifier, List<String>> groups = new LinkedHashMap<>();
        DebugScreenDisplayer displayer = new DebugScreenDisplayer() {
            @Override public void addPriorityLine(String s) { priority.add(s); }
            @Override public void addLine(String s) { lines.add(s); }
            @Override public void addToGroup(Identifier id, Collection<String> c) {
                groups.computeIfAbsent(id, k -> new ArrayList<>()).addAll(c);
            }
            @Override public void addToGroup(Identifier id, String s) {
                groups.computeIfAbsent(id, k -> new ArrayList<>()).add(s);
            }
        };

        boolean reduced = mc.showOnlyReducedInfo();
        for (Map.Entry<Identifier, DebugScreenEntry> e : DebugScreenEntries.allEntries().entrySet()) {
            DebugScreenEntryStatus status = mc.debugEntries.getStatus(e.getKey());
            if (status == DebugScreenEntryStatus.NEVER) continue;
            DebugScreenEntry entry = e.getValue();
            if (!entry.isAllowed(reduced)) continue;
            try {
                entry.display(displayer, level, clientChunk, serverChunk);
            } catch (Throwable t) {
                lines.add("(" + e.getKey() + " failed: " + t + ")");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("F3 snapshot ").append(LocalDateTime.now().format(HEADER_TIME)).append('\n');
        appendAll(sb, priority);
        appendAll(sb, lines);
        for (List<String> g : groups.values()) {
            sb.append('\n');
            appendAll(sb, g);
        }
        return sb.toString();
    }

    private static void appendAll(StringBuilder sb, List<String> lines) {
        for (String s : lines) {
            String plain = ChatFormatting.stripFormatting(s);
            sb.append(plain == null ? "" : plain).append('\n');
        }
    }

    private static String save(Minecraft mc, String text) {
        try {
            Path dir = mc.gameDirectory.toPath().resolve("quasium").resolve("f3-snapshots");
            Files.createDirectories(dir);
            String name = LocalDateTime.now().format(FILE_TIME) + ".txt";
            Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
            return name;
        } catch (IOException e) {
            Quasium.LOGGER.warn("Could not save the F3 snapshot", e);
            return null;
        }
    }

    /** F3's own (private) helpers, so entries get the same level and chunks as on the F3 screen. */
    private static <T> T call(Object target, String name, Class<T> type) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            Object r = m.invoke(target);
            return type.isInstance(r) ? type.cast(r) : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
