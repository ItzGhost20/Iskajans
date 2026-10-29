package com.example.spawnerfinder;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class Settings {

    /** Per-feature configuration (spawner finder / villager finder). */
    public static class Module {
        public boolean enabled = true;
        public boolean boxes = true;
        public boolean tracers = true;
        public boolean labels = true;
        public boolean hud = true;
        public String filter = "ALL";
        public int range = 128;
    }

    /** AutoDig configuration. */
    public static class Dig {
        public boolean enabled = false;      // feature switch, OFF by default
        public int mode = 0;                 // 0 = shortest path, 1 = fastest (time based)
        public boolean continuous = true;    // go on to the next spawner after one is mined
        public boolean bestTool = false;     // false = pickaxe only, true = any digging tool
        public boolean protectTools = true;  // do not use tools about to break
        public boolean showPath = true;
        public String filter = "ALL";
        public int rotSpeed = 35;            // degrees per tick
        public int maxRadius = 96;           // blocks
        public int minHealth = 8;            // stop below this (half hearts)
    }

    public static boolean master = true;
    public static boolean alertSound = true;
    public static int fillAlpha = 20;   // percent
    public static int lineWidth = 3;    // px
    public static int labelScale = 100; // percent

    public static final Module spawner = new Module();
    public static final Module villager = new Module();
    public static final Dig dig = new Dig();
    /** Custom colors per type (RGB), overrides the defaults. */
    public static final Map<String, Integer> colors = new TreeMap<>();

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
        master = bool(p, "master", master);
        alertSound = bool(p, "alertSound", alertSound);
        fillAlpha = num(p, "fillAlpha", fillAlpha, 0, 80);
        lineWidth = num(p, "lineWidth", lineWidth, 1, 8);
        labelScale = num(p, "labelScale", labelScale, 50, 300);
        readModule(p, "spawner", spawner);
        readModule(p, "villager", villager);

        dig.enabled = bool(p, "dig.enabled", dig.enabled);
        dig.mode = num(p, "dig.mode", dig.mode, 0, 1);
        dig.continuous = bool(p, "dig.continuous", dig.continuous);
        dig.bestTool = bool(p, "dig.bestTool", dig.bestTool);
        dig.protectTools = bool(p, "dig.protectTools", dig.protectTools);
        dig.showPath = bool(p, "dig.showPath", dig.showPath);
        dig.filter = p.getProperty("dig.filter", dig.filter);
        dig.rotSpeed = num(p, "dig.rotSpeed", dig.rotSpeed, 5, 90);
        dig.maxRadius = num(p, "dig.maxRadius", dig.maxRadius, 16, 256);
        dig.minHealth = num(p, "dig.minHealth", dig.minHealth, 1, 19);

        colors.clear();
        for (String k : p.stringPropertyNames()) {
            if (k.startsWith("color.")) {
                try {
                    colors.put(k.substring(6), Integer.parseInt(p.getProperty(k), 16) & 0xFFFFFF);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("master", String.valueOf(master));
        p.setProperty("alertSound", String.valueOf(alertSound));
        p.setProperty("fillAlpha", String.valueOf(fillAlpha));
        p.setProperty("lineWidth", String.valueOf(lineWidth));
        p.setProperty("labelScale", String.valueOf(labelScale));
        writeModule(p, "spawner", spawner);
        writeModule(p, "villager", villager);

        p.setProperty("dig.enabled", String.valueOf(dig.enabled));
        p.setProperty("dig.mode", String.valueOf(dig.mode));
        p.setProperty("dig.continuous", String.valueOf(dig.continuous));
        p.setProperty("dig.bestTool", String.valueOf(dig.bestTool));
        p.setProperty("dig.protectTools", String.valueOf(dig.protectTools));
        p.setProperty("dig.showPath", String.valueOf(dig.showPath));
        p.setProperty("dig.filter", dig.filter);
        p.setProperty("dig.rotSpeed", String.valueOf(dig.rotSpeed));
        p.setProperty("dig.maxRadius", String.valueOf(dig.maxRadius));
        p.setProperty("dig.minHealth", String.valueOf(dig.minHealth));

        for (Map.Entry<String, Integer> e : colors.entrySet()) {
            p.setProperty("color." + e.getKey(), String.format("%06X", e.getValue() & 0xFFFFFF));
        }
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "Spawner Finder client config");
        } catch (IOException ignored) {
        }
    }

    private static boolean bool(Properties p, String k, boolean def) {
        return Boolean.parseBoolean(p.getProperty(k, String.valueOf(def)));
    }

    private static int num(Properties p, String k, int def, int min, int max) {
        int v = def;
        try {
            v = Integer.parseInt(p.getProperty(k, String.valueOf(def)));
        } catch (NumberFormatException ignored) {
        }
        return Math.max(min, Math.min(max, v));
    }

    private static void readModule(Properties p, String k, Module m) {
        m.enabled = bool(p, k + ".enabled", m.enabled);
        m.boxes = bool(p, k + ".boxes", m.boxes);
        m.tracers = bool(p, k + ".tracers", m.tracers);
        m.labels = bool(p, k + ".labels", m.labels);
        m.hud = bool(p, k + ".hud", m.hud);
        m.filter = p.getProperty(k + ".filter", m.filter);
        m.range = num(p, k + ".range", m.range, 16, 512);
    }

    private static void writeModule(Properties p, String k, Module m) {
        p.setProperty(k + ".enabled", String.valueOf(m.enabled));
        p.setProperty(k + ".boxes", String.valueOf(m.boxes));
        p.setProperty(k + ".tracers", String.valueOf(m.tracers));
        p.setProperty(k + ".labels", String.valueOf(m.labels));
        p.setProperty(k + ".hud", String.valueOf(m.hud));
        p.setProperty(k + ".filter", m.filter);
        p.setProperty(k + ".range", String.valueOf(m.range));
    }
}
