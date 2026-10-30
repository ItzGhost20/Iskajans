package com.example.spawnerfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Small flat widget kit for the control panel (dark card look, teal accent). */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xFF0E1216;
    public static final int TITLE = 0xFF0A0D10;
    public static final int CARD = 0xFF141A1F;
    public static final int BORDER = 0xFF2A343D;
    public static final int ROW = 0xFF1A2128;
    public static final int ROW_HOVER = 0xFF242E37;
    public static final int ACCENT = 0xFF2ED3A0;
    public static final int ACCENT_DIM = 0xFF1A7A5F;
    public static final int TEXT = 0xFFE6EDF3;
    public static final int MUTED = 0xFF8794A1;
    public static final int OFF = 0xFF47525C;
    public static final int DANGER = 0xFFE5534B;
    public static final int GOOD = 0xFF55FF8A;

    /** Filled rectangle with the four corner pixels cut off (opaque colors only). */
    public static void rect(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x + 1, y, x + w - 1, y + h, c);
        g.fill(x, y + 1, x + w, y + h - 1, c);
    }

    /** Rounded rectangle with a 1px border. */
    public static void box(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        rect(g, x, y, w, h, border);
        rect(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    public abstract static class W extends AbstractWidget {
        protected W(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty());
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
        }

        protected static int textY(int y, int h) {
            return y + (h - 8) / 2;
        }
    }

    /** Row with a label on the left and an ON/OFF switch on the right. */
    public static class Toggle extends W {
        private final String label;
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;

        public Toggle(int x, int y, int w, int h, String label, BooleanSupplier get, Consumer<Boolean> set) {
            super(x, y, w, h);
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            Minecraft mc = Minecraft.getInstance();
            boolean on = get.getAsBoolean();
            rect(g, getX(), getY(), width, height, isHoveredOrFocused() ? ROW_HOVER : ROW);
            g.drawString(mc.font, label, getX() + 6, textY(getY(), height), on ? TEXT : MUTED, false);
            int sw = 22, sh = 10;
            int sx = getX() + width - sw - 6;
            int sy = getY() + (height - sh) / 2;
            rect(g, sx, sy, sw, sh, on ? ACCENT : OFF);
            int kx = on ? sx + sw - sh + 1 : sx + 1;
            g.fill(kx, sy + 1, kx + sh - 2, sy + sh - 1, 0xFFFFFFFF);
        }

        @Override
        public void onClick(double mx, double my) {
            set.accept(!get.getAsBoolean());
        }
    }

    /** Flat button with a (dynamic) centered label. */
    public static class Btn extends W {
        private final Supplier<String> label;
        private final Runnable action;
        private final boolean accent;

        public Btn(int x, int y, int w, int h, Supplier<String> label, boolean accent, Runnable action) {
            super(x, y, w, h);
            this.label = label;
            this.accent = accent;
            this.action = action;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            Minecraft mc = Minecraft.getInstance();
            boolean hov = isHoveredOrFocused();
            int bg = accent ? (hov ? ACCENT : ACCENT_DIM) : (hov ? ROW_HOVER : ROW);
            rect(g, getX(), getY(), width, height, bg);
            g.drawCenteredString(mc.font, label.get(), getX() + width / 2, textY(getY(), height), TEXT);
        }

        @Override
        public void onClick(double mx, double my) {
            action.run();
        }
    }

    /** Tab header. */
    public static class Tab extends W {
        private final String label;
        private final boolean active;
        private final Runnable action;

        public Tab(int x, int y, int w, int h, String label, boolean active, Runnable action) {
            super(x, y, w, h);
            this.label = label;
            this.active = active;
            this.action = action;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            Minecraft mc = Minecraft.getInstance();
            if (active) rect(g, getX(), getY(), width, height, CARD);
            else if (isHoveredOrFocused()) rect(g, getX(), getY(), width, height, ROW);
            g.drawCenteredString(mc.font, label, getX() + width / 2, textY(getY(), height - 2),
                    active ? ACCENT : MUTED);
            if (active) g.fill(getX() + 4, getY() + height - 2, getX() + width - 4, getY() + height, ACCENT);
        }

        @Override
        public void onClick(double mx, double my) {
            action.run();
        }
    }

    /** Slider for whole numbers: label left, value right, filled bar behind. */
    public static class Slide extends W {
        private final String label;
        private final int min, max, step;
        private final IntConsumer set;
        private int value;

        public Slide(int x, int y, int w, int h, String label, int min, int max, int step, int cur, IntConsumer set) {
            super(x, y, w, h);
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.set = set;
            this.value = Mth.clamp(cur, min, max);
        }

        private void fromMouse(double mx) {
            double f = Mth.clamp((mx - getX() - 2) / (double) (width - 4), 0.0, 1.0);
            int steps = Math.max(1, (max - min) / step);
            int v = min + (int) Math.round(f * steps) * step;
            if (v != value) {
                value = v;
                set.accept(v);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            Minecraft mc = Minecraft.getInstance();
            rect(g, getX(), getY(), width, height, isHoveredOrFocused() ? ROW_HOVER : ROW);
            double f = (value - min) / (double) Math.max(1, max - min);
            int fillW = (int) ((width - 4) * f);
            if (fillW > 0) rect(g, getX() + 2, getY() + height - 5, fillW, 3, ACCENT);
            g.fill(getX() + 2, getY() + height - 3, getX() + width - 2, getY() + height - 2, BORDER);
            g.drawString(mc.font, label, getX() + 6, getY() + 3, TEXT, false);
            String v = String.valueOf(value);
            g.drawString(mc.font, v, getX() + width - 6 - mc.font.width(v), getY() + 3, ACCENT, false);
        }

        @Override
        public void onClick(double mx, double my) {
            fromMouse(mx);
        }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) {
            fromMouse(mx);
        }
    }

    /** Row of clickable color squares. */
    public static class Swatches extends W {
        private final int[] colors;
        private final IntConsumer pick;

        public Swatches(int x, int y, int w, int h, int[] colors, IntConsumer pick) {
            super(x, y, w, h);
            this.colors = colors;
            this.pick = pick;
        }

        private int cell() {
            int gap = 3;
            return Math.min(height, (width - gap * (colors.length - 1)) / colors.length);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int cell = cell();
            int gap = 3;
            for (int i = 0; i < colors.length; i++) {
                int cx = getX() + i * (cell + gap);
                boolean hov = mx >= cx && mx < cx + cell && my >= getY() && my < getY() + height;
                rect(g, cx, getY(), cell, cell, hov ? 0xFFFFFFFF : BORDER);
                rect(g, cx + 1, getY() + 1, cell - 2, cell - 2, 0xFF000000 | colors[i]);
            }
        }

        @Override
        public void onClick(double mx, double my) {
            int cell = cell();
            int gap = 3;
            int i = (int) ((mx - getX()) / (cell + gap));
            if (i >= 0 && i < colors.length && (mx - getX()) - i * (cell + gap) < cell) pick.accept(colors[i]);
        }
    }
}
