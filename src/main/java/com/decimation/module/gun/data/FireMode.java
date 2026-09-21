package com.decimation.module.gun.data;

public enum FireMode {
    SEMI,
    BURST,
    AUTOMATIC;

    public static FireMode parse(String value) {
        return valueOf(value.toUpperCase());
    }
}
