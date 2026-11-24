package io.github.kriskreemurr.quasarmc.quasium.light;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.kriskreemurr.quasarmc.quasium.Quasium;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Block light colors from {@code assets/quasarmc_quasium/light_colors.json}: each light block's RGB ratio
 * (the brightest channel = 1). Brightness still comes from the light level, so nothing gets brighter or
 * darker, only colored. Unlisted light sources are warm white.
 */
public final class LightColors {
    /** r, g, b in 0..1 with max = 1. */
    public static final float[] WARM_WHITE = {1.0f, 0.93f, 0.82f};

    private static final Map<String, float[]> BY_ID = load();
    private static final Map<Block, float[]> CACHE = new ConcurrentHashMap<>();

    private LightColors() {}

    public static float[] of(Block block) {
        return CACHE.computeIfAbsent(block, b -> {
            String id = BuiltInRegistries.BLOCK.getKey(b).toString();
            float[] c = BY_ID.get(id);
            if (c == null) c = BY_ID.get(baseCopper(id)); // waxed/aged copper variants share the base color
            return c != null ? c : WARM_WHITE;
        });
    }

    /** "minecraft:waxed_weathered_copper_lantern" -> "minecraft:copper_lantern" (unchanged if not copper). */
    static String baseCopper(String id) {
        int colon = id.indexOf(':');
        String ns = id.substring(0, colon + 1), path = id.substring(colon + 1);
        if (path.startsWith("waxed_")) path = path.substring(6);
        for (String age : new String[]{"exposed_", "weathered_", "oxidized_"}) {
            if (path.startsWith(age)) { path = path.substring(age.length()); break; }
        }
        return ns + path;
    }

    /** Color of a moving light (what an entity holds, drops or burns with). */
    public static float[] of(Entity entity) {
        if (entity.isOnFire()) return of(Blocks.FIRE);
        if (entity instanceof LivingEntity living) {
            ItemStack main = living.getMainHandItem(), off = living.getOffhandItem();
            ItemStack brighter = io.github.kriskreemurr.quasarmc.core.light.DynamicLightSources.luminance(main)
                    >= io.github.kriskreemurr.quasarmc.core.light.DynamicLightSources.luminance(off) ? main : off;
            return of(brighter);
        }
        if (entity instanceof ItemEntity item) return of(item.getItem());
        return WARM_WHITE;
    }

    public static float[] of(ItemStack stack) {
        if (stack.is(Items.LAVA_BUCKET)) return of(Blocks.LAVA);
        if (stack.getItem() instanceof BlockItem bi) return of(bi.getBlock());
        return WARM_WHITE;
    }

    private static Map<String, float[]> load() {
        Map<String, float[]> out = new ConcurrentHashMap<>();
        try (InputStream in = LightColors.class.getResourceAsStream("/assets/quasarmc_quasium/light_colors.json")) {
            if (in == null) return out;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (!e.getValue().isJsonArray()) continue;
                var a = e.getValue().getAsJsonArray();
                float r = a.get(0).getAsFloat(), g = a.get(1).getAsFloat(), b = a.get(2).getAsFloat();
                float m = Math.max(r, Math.max(g, b));
                if (m <= 0) continue;
                out.put(e.getKey(), new float[]{r / m, g / m, b / m});
            }
        } catch (Exception ex) {
            Quasium.LOGGER.warn("Could not read light colors; all lights stay warm white", ex);
        }
        return out;
    }
}
