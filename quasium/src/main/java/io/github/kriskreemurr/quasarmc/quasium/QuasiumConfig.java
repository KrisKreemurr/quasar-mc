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

    /** Quasar Horizons: draw far LODs from a rarely-refreshed 360° panorama instead of every frame. */
    public static volatile boolean lodPanorama = false;
    /** Quasar Horizons: distance (blocks) where the panorama takes over from live LOD drawing. */
    public static volatile int panoramaStart = 512;
    public static final int[] PANORAMA_STARTS = {256, 512, 768, 1024, 1536, 2048};

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
            lodPanorama = Boolean.parseBoolean(p.getProperty("lodPanorama", "false"));
            panoramaStart = Integer.parseInt(p.getProperty("panoramaStart", "512"));
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
        p.setProperty("lodPanorama", Boolean.toString(lodPanorama));
        p.setProperty("panoramaStart", Integer.toString(panoramaStart));
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "Quasium settings");
        } catch (Exception e) {
            Quasium.LOGGER.warn("Could not save Quasium config", e);
        }
    }
}
