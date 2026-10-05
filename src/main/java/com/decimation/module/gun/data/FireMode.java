package com.decimation.module.gun.data;

import java.util.Locale;

public enum FireMode {
    SEMI,
    BURST,
    AUTOMATIC;

    public static FireMode parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
