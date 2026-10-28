package com.example.spawnerfinder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ControlPanel extends Screen {

    private static int tab = 0; // 0 = spawner, 1 = villager

    public ControlPanel() {
        super(Component.literal("Control Panel"));
    }

    private Settings.Module mod() {
        return tab == 0 ? Settings.spawner : Settings.villager;
    }

    private static Component onOff(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    private void toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(onOff(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(onOff(name, get.getAsBoolean()));
        }).bounds(x, y, w, 20).build());
    }

    @Override
    protected void init() {
        int x = 20, w = 170, gap = 24;

        // tabs
        addRenderableWidget(Button.builder(Component.literal((tab == 0 ? "> " : "") + "Spawner"), b -> {
            tab = 0;
            rebuildWidgets();
        }).bounds(x, 8, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal((tab == 1 ? "> " : "") + "Villager"), b -> {
            tab = 1;
            rebuildWidgets();
        }).bounds(x + 88, 8, 82, 20).build());

        // master switch
        toggle(x, 34, w, "Master", () -> Settings.master, v -> Settings.master = v);

        // per-feature settings
        final Settings.Module m = mod();
        int y = 66;
        toggle(x, y, w, "Enabled", () -> m.enabled, v -> m.enabled = v);
        toggle(x, y + gap, w, "Boxes", () -> m.boxes, v -> m.boxes = v);
        toggle(x, y + gap * 2, w, "Tracers", () -> m.tracers, v -> m.tracers = v);
        toggle(x, y + gap * 3, w, "Labels", () -> m.labels, v -> m.labels = v);
        toggle(x, y + gap * 4, w, "HUD list", () -> m.hud, v -> m.hud = v);

        addRenderableWidget(Button.builder(Component.literal("Filter: " + m.filter), b -> {
            List<String> opts = new ArrayList<>();
            opts.add("ALL");
            opts.addAll(tab == 0 ? ClientEvents.spawnerTypes() : ClientEvents.villagerTypes(minecraft));
            int i = opts.indexOf(m.filter);
            m.filter = opts.get((i + 1) % opts.size());
            b.setMessage(Component.literal("Filter: " + m.filter));
        }).bounds(x, y + gap * 5, w, 20).build());

        addRenderableWidget(new RangeSlider(x, y + gap * 6, w, 20, m));

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x, y + gap * 8, w, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);

        int lx = 210;
        g.drawString(font, tab == 0 ? "Nearby spawners:" : "Nearby villagers:", lx, 12, 0xFFFF55);

        List<Target> list = tab == 0
                ? ClientEvents.spawnerTargets(minecraft)
                : ClientEvents.villagerTargets(minecraft, pt);
        if (list.isEmpty()) {
            g.drawString(font, "none found in range", lx, 30, 0xAAAAAA);
            return;
        }
        for (int i = 0; i < Math.min(16, list.size()); i++) {
            Target t = list.get(i);
            g.drawString(font, t.label(), lx, 30 + i * 12, Colors.rgb(t.type()));
        }
    }

    @Override
    public void removed() {
        Settings.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Slider for the detection range: 16 to 512 blocks, step 16. */
    private static class RangeSlider extends AbstractSliderButton {
        private final Settings.Module m;

        RangeSlider(int x, int y, int w, int h, Settings.Module m) {
            super(x, y, w, h, Component.empty(), (m.range - 16) / 496.0);
            this.m = m;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Range: " + m.range));
        }

        @Override
        protected void applyValue() {
            m.range = 16 + (int) Math.round(this.value * 31) * 16;
        }
    }
}
