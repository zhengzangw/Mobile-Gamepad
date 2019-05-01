package com.zhengzangw.gesturedetect.utils;

import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.Locale;

public class TimestampAxisFormatter extends ValueFormatter {
    @Override
    public String getFormattedValue(float value) {
        return formatValue(value);
    }

    // convert microseconds to seconds
    private static String formatValue(float value) {
        return String.format(Locale.getDefault(), "%.3f", value / 1000f);
    }
}
