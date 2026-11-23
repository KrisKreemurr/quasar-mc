package io.github.kriskreemurr.quasarmc.quasium.client;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Integer slider for Quasium's options page (min..max, shown as a percentage). */
public class QuasiumSlider extends AbstractSliderButton {
    private final String key;
    private final int min, max;
    private final IntSupplier getter;
    private final IntConsumer setter;

    public QuasiumSlider(int width, String key, int min, int max, IntSupplier getter, IntConsumer setter) {
        super(0, 0, width, 20, Component.empty(), (getter.getAsInt() - min) / (double) (max - min));
        this.key = key;
        this.min = min;
        this.max = max;
        this.getter = getter;
        this.setter = setter;
        updateMessage();
    }

    private int current() {
        return (int) Math.round(min + value * (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(CommonComponents.optionNameValue(Component.translatable(key), Component.literal(current() + "%")));
    }

    @Override
    protected void applyValue() {
        int v = current();
        if (v != getter.getAsInt()) setter.accept(v);
    }
}
