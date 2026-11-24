package io.github.kriskreemurr.quasarmc.quasium;

import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Quasium settings, saved to config/quasarmc_quasium.properties. */
public final class QuasiumConfig {
    /** Skip chunk faces that point away from the camera. Takes effect on the next frame. */
    public static volatile boolean faceCulling = true;

    /** Far entities rebuild their animation/pose fewer times per second. */
    public static volatile boolean entityLod = true;

    /** Entities blend light between blocks and ease over time instead of snapping between light levels. */
    public static volatile boolean smoothEntityLight = true;

    /** Terrain block light falls off by straight-line distance (round, no rings) instead of vanilla's diamond. */
    public static volatile boolean roundLight = true;

    /** Dynamic lights (held torches etc.): 0 off, 1 eco (~2 moves/s), 2 balanced (~5/s), 3 smooth (every block). */
    public static volatile int dynamicLights = 3;

    /** Block light takes the color of its source (needs Quasar Meshelium). */
    public static volatile boolean coloredLight = true;

    /** Quasar Horizons: draw far LODs from a rarely-refreshed 360° panorama instead of every frame. */
    public static volatile boolean lodPanorama = false;
    /** Quasar Horizons: distance (blocks) where the panorama takes over from live LOD drawing. */
    public static volatile int panoramaStart = 512;
    public static final int[] PANORAMA_STARTS = {256, 512, 768, 1024, 1536, 2048};

    /** Colour grading (percent). Saturation 0..200 (100 = unchanged), contrast 50..150 (100 = unchanged),
     * whiteness 0..100 (50 = unchanged; higher = bright colours reach white sooner). */
    public static volatile int saturation = 100;
    public static volatile int contrast = 100;
    public static volatile int whiteness = 50;

    private QuasiumConfig() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("quasarmc_quasium.properties");
    }

    /** Pass the settings that other Quasar mods read (Quasar Horizons) through system properties. */
    public static void apply() {
        System.setProperty("quasarhorizons.panorama", Boolean.toString(lodPanorama));
        System.setProperty("quasarhorizons.panoramaStart", Integer.toString(panoramaStart));
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            save();
            apply();
            return;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f)) {
            p.load(r);
            faceCulling = Boolean.parseBoolean(p.getProperty("faceCulling", "true"));
            entityLod = Boolean.parseBoolean(p.getProperty("entityLod", "true"));
            smoothEntityLight = Boolean.parseBoolean(p.getProperty("smoothEntityLight", "true"));
            roundLight = Boolean.parseBoolean(p.getProperty("roundLight", "true"));
            dynamicLights = clamp(Integer.parseInt(p.getProperty("dynamicLights", "3")), 0, 3);
            coloredLight = Boolean.parseBoolean(p.getProperty("coloredLight", "true"));
            lodPanorama = Boolean.parseBoolean(p.getProperty("lodPanorama", "false"));
            panoramaStart = Integer.parseInt(p.getProperty("panoramaStart", "512"));
            saturation = clamp(Integer.parseInt(p.getProperty("saturation", "100")), 0, 200);
            contrast = clamp(Integer.parseInt(p.getProperty("contrast", "100")), 50, 150);
            whiteness = clamp(Integer.parseInt(p.getProperty("whiteness", "50")), 0, 100);
        } catch (Exception e) {
            Quasium.LOGGER.warn("Could not read Quasium config, using defaults", e);
        }
        apply();
    }

    public static void save() {
        apply();
        Properties p = new Properties();
        p.setProperty("faceCulling", Boolean.toString(faceCulling));
        p.setProperty("entityLod", Boolean.toString(entityLod));
        p.setProperty("smoothEntityLight", Boolean.toString(smoothEntityLight));
        p.setProperty("roundLight", Boolean.toString(roundLight));
        p.setProperty("dynamicLights", Integer.toString(dynamicLights));
        p.setProperty("coloredLight", Boolean.toString(coloredLight));
        p.setProperty("lodPanorama", Boolean.toString(lodPanorama));
        p.setProperty("panoramaStart", Integer.toString(panoramaStart));
        p.setProperty("saturation", Integer.toString(saturation));
        p.setProperty("contrast", Integer.toString(contrast));
        p.setProperty("whiteness", Integer.toString(whiteness));
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "Quasium settings");
        } catch (Exception e) {
            Quasium.LOGGER.warn("Could not save Quasium config", e);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
