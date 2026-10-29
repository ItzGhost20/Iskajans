package com.example.spawnerfinder;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** Generic integer slider: label, min, max, step. */
public class IntSlider extends AbstractSliderButton {
    private final String label;
    private final int min, max, step;
    private final IntConsumer setter;

    public IntSlider(int x, int y, int w, int h, String label, int min, int max, int step,
                     int current, IntConsumer setter) {
        super(x, y, w, h, Component.empty(), (Math.max(min, Math.min(max, current)) - min) / (double) (max - min));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.setter = setter;
        updateMessage();
    }

    private int intValue() {
        int steps = Math.max(1, (max - min) / step);
        return min + (int) Math.round(this.value * steps) * step;
    }

    @Override
    protected void updateMessage() {
        if (label == null) return;
        setMessage(Component.literal(label + ": " + intValue()));
    }

    @Override
    protected void applyValue() {
        setter.accept(intValue());
    }
}
