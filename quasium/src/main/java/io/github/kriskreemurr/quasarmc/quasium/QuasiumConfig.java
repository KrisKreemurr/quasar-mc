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

    private QuasiumConfig() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("quasarmc_quasium.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            save();
            return;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f)) {
            p.load(r);
            faceCulling = Boolean.parseBoolean(p.getProperty("faceCulling", "true"));
        } catch (Exception e) {
            Quasium.LOGGER.warn("Could not read Quasium config, using defaults", e);
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("faceCulling", Boolean.toString(faceCulling));
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "Quasium settings");
        } catch (Exception e) {
            Quasium.LOGGER.warn("Could not save Quasium config", e);
        }
    }
}
