package io.github.kriskreemurr.quasarmc.quasium.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.kriskreemurr.quasarmc.quasium.Quasium;
import io.github.kriskreemurr.quasarmc.quasium.QuasiumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Quasium colour grading: saturation, contrast and whiteness as a cheap full-screen pass.
 * <p>
 * Minecraft 26.3 runs a post effect called {@code minecraft:end_of_frame} every frame when one exists; Quasium ships it
 * ({@code assets/minecraft/post_effect/end_of_frame.json} → {@code quasarmc_quasium:post/grade}). Post-effect uniforms are
 * fixed when the effect loads, so the live slider values reach the shader through a 1×1 texture that is rewritten whenever a
 * setting changes. Each channel holds one value, 128 = neutral:
 * R saturation (0..2), G contrast (0.5..1.5), B whiteness (shoulder of the tone curve).
 */
public final class ColorGrade {
    /** The post effect's texture input refers to this id (registered under both spellings a TextureInput may resolve to). */
    public static final Identifier SHORT_ID = Identifier.fromNamespaceAndPath(Quasium.MODID, "grade");
    public static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(Quasium.MODID, "textures/effect/grade.png");

    private static NativeImage image;
    private static DynamicTexture texture;

    private ColorGrade() {}

    public static void init() {
        try {
            image = new NativeImage(1, 1, false);
            texture = new DynamicTexture(() -> "Quasium colour grade", image);
            var textures = Minecraft.getInstance().getTextureManager();
            textures.register(TEXTURE_ID, texture);
            textures.register(SHORT_ID, texture);
            update();
        } catch (Throwable t) {
            Quasium.LOGGER.warn("Could not set up Quasium colour grading", t);
        }
    }

    /** Writes the current settings into the parameter texture. Call on the render thread after a setting changes. */
    public static void update() {
        if (image == null || texture == null) return;
        int r = encode((QuasiumConfig.saturation - 100) / 100.0);
        int g = encode((QuasiumConfig.contrast - 100) / 50.0);
        int b = encode((QuasiumConfig.whiteness - 50) / 50.0);
        image.setPixel(0, 0, 0xFF000000 | (r << 16) | (g << 8) | b);
        texture.upload();
    }

    /** -1..1 → 1..255 with 128 exactly neutral. */
    private static int encode(double v) {
        v = Math.max(-1, Math.min(1, v));
        return (int) Math.round(128 + v * 127);
    }
}
