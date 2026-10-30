package com.example.spawnerfinder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class ControlPanel extends Screen {

    private static final String[] TABS = {"Spawner", "Villager", "Colors", "AutoDig", "General"};
    private static final int[] PRESETS = {
            0xFF3030, 0xFF9020, 0xFFE030, 0x30FF30, 0x30FFFF, 0x3080FF, 0xA050FF, 0xFF40FF, 0xFFFFFF};

    private static int tab = 0;
    private static String colorType = "zombie";

    // layout (computed in init)
    private int px, py, pw, ph;
    private int colW, col0, col1, cy, rx, rw;
    private int slot;

    private static final int ROW_H = 18;
    private static final int GAP = 3;

    public ControlPanel() {
        super(Component.literal("Control Panel"));
    }

    // ------------------------------------------------------------ layout helpers

    private int colX() {
        return (slot % 2 == 0) ? col0 : col1;
    }

    private int rowY() {
        return cy + (slot / 2) * (ROW_H + GAP);
    }

    private void tog(String name, BooleanSupplier get, Consumer<Boolean> set) {
        addRenderableWidget(new Ui.Toggle(colX(), rowY(), colW, ROW_H, name, get, set));
        slot++;
    }

    private void btn(Supplier<String> label, boolean accent, Runnable action) {
        addRenderableWidget(new Ui.Btn(colX(), rowY(), colW, ROW_H, label, accent, action));
        slot++;
    }

    private void slide(String label, int min, int max, int step, int cur, IntConsumer set) {
        addRenderableWidget(new Ui.Slide(colX(), rowY(), colW, ROW_H, label, min, max, step, cur, set));
        slot++;
    }

    /** Continue on a fresh row so the next widget spans both columns. */
    private void fullRow() {
        if (slot % 2 == 1) slot++;
    }

    private int fullW() {
        return colW * 2 + GAP * 2;
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
        pw = Math.min(width - 12, 450);
        ph = Math.min(height - 8, 268);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        int leftW = (int) (pw * 0.64);
        colW = (leftW - 8 - GAP) / 2;
        col0 = px + 8;
        col1 = col0 + colW + GAP;
        cy = py + 56;
        rx = px + leftW + 4;
        rw = px + pw - 8 - rx;

        // title bar: master switch + close
        addRenderableWidget(new Ui.Toggle(px + pw - 8 - 18 - 4 - 96, py + 4, 96, 16, "Master",
                () -> Settings.master, v -> Settings.master = v));
        addRenderableWidget(new Ui.Btn(px + pw - 8 - 18, py + 4, 18, 16, () -> "X", false, this::onClose));

        // tabs
        int tw = (pw - 16) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            addRenderableWidget(new Ui.Tab(px + 8 + i * tw, py + 27, tw - 2, 20, TABS[i], tab == i, () -> {
                tab = t;
                rebuildWidgets();
            }));
        }

        switch (tab) {
            case 0 -> moduleTab(Settings.spawner, true);
            case 1 -> moduleTab(Settings.villager, false);
            case 2 -> colorTab();
            case 3 -> digTab();
            default -> generalTab();
        }
    }

    private void moduleTab(Settings.Module m, boolean isSpawner) {
        tog("Enabled", () -> m.enabled, v -> m.enabled = v);
        tog("Boxes", () -> m.boxes, v -> m.boxes = v);
        tog("Tracers", () -> m.tracers, v -> m.tracers = v);
        tog("Labels", () -> m.labels, v -> m.labels = v);
        tog("HUD list", () -> m.hud, v -> m.hud = v);
        btn(() -> "Filter: " + m.filter, false, () ->
                m.filter = cycle(m.filter, isSpawner ? ClientEvents.spawnerTypes() : ClientEvents.villagerTypes(minecraft)));
        slide("Range", 16, 512, 16, m.range, v -> m.range = v);
        if (isSpawner) {
            btn(() -> "Copy nearest coords", false, () -> ClientEvents.copyNearest(minecraft));
        }
    }

    private void colorTab() {
        fullRow();
        addRenderableWidget(new Ui.Btn(col0, rowY(), fullW(), ROW_H,
                () -> "Type: " + colorType + "   (click for next)", false, () -> {
            List<String> t = knownTypes();
            int i = t.indexOf(colorType);
            colorType = t.get((i + 1) % t.size());
            rebuildWidgets();
        }));
        slot += 2;

        int cur = Colors.rgb(colorType);
        fullRow();
        addRenderableWidget(new Ui.Slide(col0, rowY(), fullW(), ROW_H, "Red", 0, 255, 1, (cur >> 16) & 255,
                v -> setChannel(16, v)));
        slot += 2;
        addRenderableWidget(new Ui.Slide(col0, rowY(), fullW(), ROW_H, "Green", 0, 255, 1, (cur >> 8) & 255,
                v -> setChannel(8, v)));
        slot += 2;
        addRenderableWidget(new Ui.Slide(col0, rowY(), fullW(), ROW_H, "Blue", 0, 255, 1, cur & 255,
                v -> setChannel(0, v)));
        slot += 2;

        addRenderableWidget(new Ui.Swatches(col0, rowY(), fullW(), 16, PRESETS, c -> {
            Settings.colors.put(colorType, c & 0xFFFFFF);
            rebuildWidgets();
        }));
        slot += 2;

        btn(() -> "Reset this color", false, () -> {
            Settings.colors.remove(colorType);
            rebuildWidgets();
        });
        btn(() -> "Reset ALL colors", false, () -> {
            Settings.colors.clear();
            rebuildWidgets();
        });
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
        btn(() -> AutoDig.active ? "STOP" : "START", true, () -> AutoDig.toggle(minecraft));
        btn(() -> "Route: " + (d.mode == 0 ? "SHORTEST" : "FASTEST"), false, () -> d.mode = d.mode == 0 ? 1 : 0);
        btn(() -> "Target: " + d.filter, false, () -> d.filter = cycle(d.filter, ClientEvents.spawnerTypes()));
        tog("Mine all spawners", () -> d.continuous, v -> d.continuous = v);
        tog("Any dig tool", () -> d.bestTool, v -> d.bestTool = v);
        tog("Protect tools", () -> d.protectTools, v -> d.protectTools = v);
        tog("Show path", () -> d.showPath, v -> d.showPath = v);
        tog("Stop on damage", () -> d.stopOnDamage, v -> d.stopOnDamage = v);
        tog("Stop if I move", () -> d.stopOnMove, v -> d.stopOnMove = v);
        slide("Turn speed", 5, 90, 5, d.rotSpeed, v -> d.rotSpeed = v);
        slide("Max radius", 16, 512, 16, d.maxRadius, v -> d.maxRadius = v);
        slide("Min health", 1, 19, 1, d.minHealth, v -> d.minHealth = v);
    }

    private void generalTab() {
        tog("Alert sound", () -> Settings.alertSound, v -> Settings.alertSound = v);
        btn(() -> "Copy nearest coords", false, () -> ClientEvents.copyNearest(minecraft));
        slide("Fill opacity", 0, 80, 5, Settings.fillAlpha, v -> Settings.fillAlpha = v);
        slide("Line width", 1, 8, 1, Settings.lineWidth, v -> Settings.lineWidth = v);
        slide("Label size", 50, 300, 10, Settings.labelScale, v -> Settings.labelScale = v);
    }

    // ------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);

        // window
        Ui.box(g, px, py, pw, ph, Ui.BG, Ui.BORDER);
        Ui.rect(g, px + 1, py + 1, pw - 2, 24, Ui.TITLE);
        g.drawString(font, "SPAWNER FINDER", px + 10, py + 9, Ui.ACCENT, false);
        g.drawString(font, "v5", px + 10 + font.width("SPAWNER FINDER") + 5, py + 9, Ui.MUTED, false);
        g.fill(px + 8, py + 49, px + pw - 8, py + 50, Ui.BORDER);

        // info card on the right
        int cardY = cy - 4;
        int cardH = py + ph - 22 - cardY;
        Ui.box(g, rx, cardY, rw, cardH, Ui.CARD, Ui.BORDER);

        super.render(g, mx, my, pt);

        int tx = rx + 6;
        int ty = cardY + 6;
        int maxW = rw - 12;
        switch (tab) {
            case 0 -> list(g, tx, ty, maxW, "NEARBY SPAWNERS", ClientEvents.spawnerTargets(minecraft), cardH);
            case 1 -> list(g, tx, ty, maxW, "NEARBY VILLAGERS", ClientEvents.villagerTargets(minecraft, pt), cardH);
            case 2 -> colorInfo(g, tx, ty, maxW, cardH);
            case 3 -> digInfo(g, tx, ty, maxW);
            default -> stats(g, tx, ty, maxW);
        }

        // footer
        g.drawString(font, "Right Shift: panel   K: master   G: AutoDig", px + 10, py + ph - 14, Ui.MUTED, false);
    }

    private String fit(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private void list(GuiGraphics g, int x, int y, int maxW, String title, List<Target> list, int cardH) {
        g.drawString(font, title, x, y, Ui.ACCENT, false);
        if (list.isEmpty()) {
            g.drawString(font, "none in range", x, y + 14, Ui.MUTED, false);
            return;
        }
        int rows = Math.max(1, (cardH - 24) / 11);
        for (int i = 0; i < Math.min(rows, list.size()); i++) {
            Target t = list.get(i);
            g.drawString(font, fit(t.label(), maxW), x, y + 14 + i * 11, 0xFF000000 | Colors.rgb(t.type()), false);
        }
        if (list.size() > rows) {
            g.drawString(font, "+" + (list.size() - rows) + " more", x, y + 14 + rows * 11, Ui.MUTED, false);
        }
    }

    private void colorInfo(GuiGraphics g, int x, int y, int maxW, int cardH) {
        g.drawString(font, "COLOR", x, y, Ui.ACCENT, false);
        g.drawString(font, fit(colorType, maxW), x, y + 12, Ui.TEXT, false);
        int rgb = Colors.rgb(colorType);
        Ui.box(g, x, y + 26, maxW, 22, 0xFF000000 | rgb, Ui.BORDER);
        g.drawString(font, String.format("#%06X", rgb), x, y + 52, Ui.MUTED, false);
        List<String> types = knownTypes();
        int rows = Math.max(1, (cardH - 74) / 11);
        for (int i = 0; i < Math.min(rows, types.size()); i++) {
            String t = types.get(i);
            g.drawString(font, fit(t, maxW), x, y + 66 + i * 11, 0xFF000000 | Colors.rgb(t), false);
        }
    }

    private void digInfo(GuiGraphics g, int x, int y, int maxW) {
        g.drawString(font, "AUTODIG", x, y, Ui.ACCENT, false);
        boolean recent = !AutoDig.active && !AutoDig.lastStop.isEmpty()
                && System.currentTimeMillis() - AutoDig.lastStopTime < 60000;
        int sc = AutoDig.active ? Ui.GOOD : (recent ? (AutoDig.finished ? Ui.GOOD : Ui.DANGER) : Ui.MUTED);
        String st = AutoDig.active ? AutoDig.status
                : (recent ? (AutoDig.finished ? AutoDig.lastStop : "stopped: " + AutoDig.lastStop) : "idle");
        int yy = y + 14;
        for (String line : wrap(st, maxW)) {
            g.drawString(font, line, x, yy, sc, false);
            yy += 10;
        }
        yy += 4;
        g.drawString(font, "Mined: " + AutoDig.mined, x, yy, Ui.TEXT, false);
        g.drawString(font, "Left: " + AutoDig.remaining, x, yy + 10, Ui.TEXT, false);
        g.drawString(font, "Skipped: " + (AutoDig.failed + AutoDig.unreachable), x, yy + 20, Ui.TEXT, false);
        yy += 36;
        g.drawString(font, "Hotbar needs:", x, yy, Ui.MUTED, false);
        g.drawString(font, "- normal pickaxe", x, yy + 10, Ui.MUTED, false);
        g.drawString(font, "- silk touch pickaxe", x, yy + 20, Ui.MUTED, false);
    }

    private List<String> wrap(String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : s.split(" ")) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (font.width(test) > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(w);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.size() > 4) out = new ArrayList<>(out.subList(0, 4));
        return out;
    }

    private void stats(GuiGraphics g, int x, int y, int maxW) {
        g.drawString(font, "FOUND NOW", x, y, Ui.ACCENT, false);
        TreeMap<String, Integer> counts = new TreeMap<>();
        for (ClientEvents.Found f : ClientEvents.foundList()) counts.merge(f.type(), 1, Integer::sum);
        int yy = y + 14;
        if (counts.isEmpty()) g.drawString(font, "no spawners loaded", x, yy, Ui.MUTED, false);
        for (var en : counts.entrySet()) {
            g.drawString(font, fit(en.getKey() + " x" + en.getValue(), maxW), x, yy,
                    0xFF000000 | Colors.rgb(en.getKey()), false);
            yy += 11;
        }
        yy += 6;
        g.drawString(font, "Villagers: " + ClientEvents.villagerTargets(minecraft, 1f).size(), x, yy, Ui.TEXT, false);
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
