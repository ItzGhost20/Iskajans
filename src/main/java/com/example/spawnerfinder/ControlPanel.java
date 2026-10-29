package com.example.spawnerfinder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class ControlPanel extends Screen {

    private static final String[] TABS = {"Spawner", "Villager", "Colors", "AutoDig", "General"};
    private static final String[][] PRESETS = {
            {"Red", "FF3030"}, {"Green", "30FF30"}, {"Blue", "3080FF"}, {"Yellow", "FFE030"},
            {"Cyan", "30FFFF"}, {"Magenta", "FF40FF"}, {"Orange", "FF9020"}, {"White", "FFFFFF"}};

    private static int tab = 0;
    private static String colorType = "zombie";

    private int slot;

    public ControlPanel() {
        super(Component.literal("Control Panel"));
    }

    // ------------------------------------------------------------ layout helpers

    private int sx() {
        return 20 + (slot % 2) * 126;
    }

    private int sy() {
        return 40 + (slot / 2) * 22;
    }

    private static Component onOff(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    private void tog(String name, BooleanSupplier get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(onOff(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(onOff(name, get.getAsBoolean()));
        }).bounds(sx(), sy(), 120, 20).build());
        slot++;
    }

    private void btn(String label, Consumer<Button> action) {
        addRenderableWidget(Button.builder(Component.literal(label), action::accept)
                .bounds(sx(), sy(), 120, 20).build());
        slot++;
    }

    private void slider(String label, int min, int max, int step, int cur, IntConsumer set) {
        addRenderableWidget(new IntSlider(sx(), sy(), 120, 20, label, min, max, step, cur, set));
        slot++;
    }

    private List<String> knownTypes() {
        TreeSet<String> set = new TreeSet<>(Colors.DEFAULT_TYPES);
        set.addAll(ClientEvents.spawnerTypes());
        set.addAll(ClientEvents.villagerTypes(minecraft));
        set.addAll(Settings.colors.keySet());
        return new ArrayList<>(set);
    }

    private static String cycle(String current, List<String> types) {
        List<String> opts = new ArrayList<>();
        opts.add("ALL");
        opts.addAll(types);
        int i = opts.indexOf(current);
        return opts.get((i + 1) % opts.size());
    }

    // ------------------------------------------------------------ build

    @Override
    protected void init() {
        slot = 0;

        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            addRenderableWidget(Button.builder(Component.literal((tab == i ? "> " : "") + TABS[i]), b -> {
                tab = t;
                rebuildWidgets();
            }).bounds(20 + i * 66, 8, 64, 20).build());
        }

        switch (tab) {
            case 0 -> moduleTab(Settings.spawner, true);
            case 1 -> moduleTab(Settings.villager, false);
            case 2 -> colorTab();
            case 3 -> digTab();
            default -> generalTab();
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(20, this.height - 28, 120, 20).build());
    }

    private void moduleTab(Settings.Module m, boolean isSpawner) {
        tog("Enabled", () -> m.enabled, v -> m.enabled = v);
        tog("Boxes", () -> m.boxes, v -> m.boxes = v);
        tog("Tracers", () -> m.tracers, v -> m.tracers = v);
        tog("Labels", () -> m.labels, v -> m.labels = v);
        tog("HUD list", () -> m.hud, v -> m.hud = v);
        btn("Filter: " + m.filter, b -> {
            m.filter = cycle(m.filter, isSpawner ? ClientEvents.spawnerTypes() : ClientEvents.villagerTypes(minecraft));
            b.setMessage(Component.literal("Filter: " + m.filter));
        });
        slider("Range", 16, 512, 16, m.range, v -> m.range = v);
        if (isSpawner) {
            btn("Copy nearest coords", b -> ClientEvents.copyNearest(minecraft));
        }
    }

    private void colorTab() {
        btn("Type: " + colorType, b -> {
            List<String> t = knownTypes();
            int i = t.indexOf(colorType);
            colorType = t.get((i + 1) % t.size());
            rebuildWidgets();
        });
        int cur = Colors.rgb(colorType);
        slider("Red", 0, 255, 1, (cur >> 16) & 255, v -> setChannel(16, v));
        slider("Green", 0, 255, 1, (cur >> 8) & 255, v -> setChannel(8, v));
        slider("Blue", 0, 255, 1, cur & 255, v -> setChannel(0, v));
        btn("Reset this color", b -> {
            Settings.colors.remove(colorType);
            rebuildWidgets();
        });
        btn("Reset ALL colors", b -> {
            Settings.colors.clear();
            rebuildWidgets();
        });
        for (String[] p : PRESETS) {
            btn(p[0], b -> {
                Settings.colors.put(colorType, Integer.parseInt(p[1], 16));
                rebuildWidgets();
            });
        }
    }

    private static void setChannel(int shift, int v) {
        int cur = Colors.rgb(colorType);
        cur = (cur & ~(255 << shift)) | (v << shift);
        Settings.colors.put(colorType, cur & 0xFFFFFF);
    }

    private void digTab() {
        Settings.Dig d = Settings.dig;
        tog("AutoDig", () -> d.enabled, v -> {
            d.enabled = v;
            if (!v && AutoDig.active) AutoDig.stop(minecraft, "switched off");
        });
        btn(AutoDig.active ? "Stop now" : "Start now", b -> {
            AutoDig.toggle(minecraft);
            b.setMessage(Component.literal(AutoDig.active ? "Stop now" : "Start now"));
        });
        btn("Route: " + (d.mode == 0 ? "SHORTEST" : "FASTEST"), b -> {
            d.mode = d.mode == 0 ? 1 : 0;
            b.setMessage(Component.literal("Route: " + (d.mode == 0 ? "SHORTEST" : "FASTEST")));
        });
        tog("Continuous", () -> d.continuous, v -> d.continuous = v);
        tog("Any dig tool", () -> d.bestTool, v -> d.bestTool = v);
        tog("Protect tools", () -> d.protectTools, v -> d.protectTools = v);
        tog("Show path", () -> d.showPath, v -> d.showPath = v);
        btn("Target: " + d.filter, b -> {
            d.filter = cycle(d.filter, ClientEvents.spawnerTypes());
            b.setMessage(Component.literal("Target: " + d.filter));
        });
        slider("Turn speed", 5, 90, 5, d.rotSpeed, v -> d.rotSpeed = v);
        slider("Max radius", 16, 256, 16, d.maxRadius, v -> d.maxRadius = v);
        slider("Min health", 1, 19, 1, d.minHealth, v -> d.minHealth = v);
    }

    private void generalTab() {
        tog("Master", () -> Settings.master, v -> Settings.master = v);
        tog("Alert sound", () -> Settings.alertSound, v -> Settings.alertSound = v);
        slider("Fill opacity", 0, 80, 5, Settings.fillAlpha, v -> Settings.fillAlpha = v);
        slider("Line width", 1, 8, 1, Settings.lineWidth, v -> Settings.lineWidth = v);
        slider("Label size", 50, 300, 10, Settings.labelScale, v -> Settings.labelScale = v);
        btn("Copy nearest coords", b -> ClientEvents.copyNearest(minecraft));
    }

    // ------------------------------------------------------------ right side

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);

        int lx = 290;
        switch (tab) {
            case 0 -> list(g, lx, "Nearby spawners:", ClientEvents.spawnerTargets(minecraft));
            case 1 -> list(g, lx, "Nearby villagers:", ClientEvents.villagerTargets(minecraft, pt));
            case 2 -> colorInfo(g, lx);
            case 3 -> digInfo(g, lx);
            default -> stats(g, lx);
        }
    }

    private void list(GuiGraphics g, int lx, String title, List<Target> list) {
        g.drawString(font, title, lx, 40, 0xFFFF55);
        if (list.isEmpty()) {
            g.drawString(font, "none found in range", lx, 54, 0xAAAAAA);
            return;
        }
        for (int i = 0; i < Math.min(14, list.size()); i++) {
            Target t = list.get(i);
            g.drawString(font, t.label(), lx, 54 + i * 11, Colors.rgb(t.type()));
        }
    }

    private void colorInfo(GuiGraphics g, int lx) {
        g.drawString(font, "Editing: " + colorType, lx, 40, 0xFFFF55);
        g.fill(lx, 54, lx + 60, 78, 0xFF000000 | Colors.rgb(colorType));
        g.drawString(font, String.format("#%06X", Colors.rgb(colorType)), lx + 66, 62, 0xFFFFFF);
        List<String> types = knownTypes();
        for (int i = 0; i < Math.min(12, types.size()); i++) {
            String t = types.get(i);
            g.drawString(font, t, lx, 88 + i * 11, Colors.rgb(t));
        }
    }

    private void digInfo(GuiGraphics g, int lx) {
        g.drawString(font, "AutoDig", lx, 40, 0xFFFF55);
        g.drawString(font, "Status: " + AutoDig.status, lx, 54, AutoDig.active ? 0x55FF55 : 0xAAAAAA);
        g.drawString(font, "Spawner: silk touch pick", lx, 70, 0xFFFFFF);
        g.drawString(font, "Other blocks: normal pick", lx, 81, 0xFFFFFF);
        g.drawString(font, "Put both in the hotbar.", lx, 92, 0xAAAAAA);
        g.drawString(font, "Key G = start / stop", lx, 108, 0xFFFFFF);
        g.drawString(font, "Press a move key = stop", lx, 119, 0xFFFFFF);
        g.drawString(font, "Stops on damage / low HP", lx, 130, 0xFFFFFF);
        g.drawString(font, "Never mines chests etc.", lx, 141, 0xFFFFFF);
    }

    private void stats(GuiGraphics g, int lx) {
        g.drawString(font, "Found now:", lx, 40, 0xFFFF55);
        TreeMap<String, Integer> counts = new TreeMap<>();
        for (ClientEvents.Found f : ClientEvents.foundList()) counts.merge(f.type(), 1, Integer::sum);
        int y = 54;
        if (counts.isEmpty()) g.drawString(font, "no spawners loaded", lx, y, 0xAAAAAA);
        for (var en : counts.entrySet()) {
            g.drawString(font, en.getKey() + " x" + en.getValue(), lx, y, Colors.rgb(en.getKey()));
            y += 11;
        }
        y += 6;
        g.drawString(font, "Villagers: " + ClientEvents.villagerTargets(minecraft, 1f).size(), lx, y, 0xFFFFFF);
    }

    @Override
    public void removed() {
        Settings.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
