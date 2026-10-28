package com.example.spawnerfinder;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Settings {

    /** Per-feature configuration. */
    public static class Module {
        public boolean enabled = true;
        public boolean boxes = true;
        public boolean tracers = true;
        public boolean labels = true;
        public boolean hud = true;
        public String filter = "ALL";
        public int range = 128;
    }

    public static boolean master = true;
    public static final Module spawner = new Module();
    public static final Module villager = new Module();

    static {
        villager.range = 64;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("spawnerfinder.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f)) {
            p.load(r);
        } catch (IOException e) {
            return;
        }
        master = Boolean.parseBoolean(p.getProperty("master", "true"));
        read(p, "spawner", spawner);
        read(p, "villager", villager);
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("master", String.valueOf(master));
        write(p, "spawner", spawner);
        write(p, "villager", villager);
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "Spawner Finder client config");
        } catch (IOException ignored) {
        }
    }

    private static void read(Properties p, String k, Module m) {
        m.enabled = Boolean.parseBoolean(p.getProperty(k + ".enabled", String.valueOf(m.enabled)));
        m.boxes = Boolean.parseBoolean(p.getProperty(k + ".boxes", String.valueOf(m.boxes)));
        m.tracers = Boolean.parseBoolean(p.getProperty(k + ".tracers", String.valueOf(m.tracers)));
        m.labels = Boolean.parseBoolean(p.getProperty(k + ".labels", String.valueOf(m.labels)));
        m.hud = Boolean.parseBoolean(p.getProperty(k + ".hud", String.valueOf(m.hud)));
        m.filter = p.getProperty(k + ".filter", m.filter);
        try {
            m.range = Integer.parseInt(p.getProperty(k + ".range", String.valueOf(m.range)));
        } catch (NumberFormatException ignored) {
        }
        m.range = Math.max(16, Math.min(512, m.range));
    }

    private static void write(Properties p, String k, Module m) {
        p.setProperty(k + ".enabled", String.valueOf(m.enabled));
        p.setProperty(k + ".boxes", String.valueOf(m.boxes));
        p.setProperty(k + ".tracers", String.valueOf(m.tracers));
        p.setProperty(k + ".labels", String.valueOf(m.labels));
        p.setProperty(k + ".hud", String.valueOf(m.hud));
        p.setProperty(k + ".filter", m.filter);
        p.setProperty(k + ".range", String.valueOf(m.range));
    }
}
