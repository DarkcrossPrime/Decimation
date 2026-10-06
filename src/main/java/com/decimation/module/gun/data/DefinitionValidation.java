package com.decimation.module.gun.data;

import java.util.regex.Pattern;

/** Validates authoring IDs independently of Minecraft's registry/bootstrap state. */
final class DefinitionValidation {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    private DefinitionValidation() { }

    static void identifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid " + field + ": " + value);
        }
        path(value.substring(value.indexOf(':') + 1), field);
    }

    static void path(String value, String field) {
        if (value == null || !PATH.matcher(value).matches() || value.startsWith("/")) {
            throw new IllegalArgumentException("invalid " + field + ": " + value);
        }
        for (String part : value.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("invalid path segment in " + field + ": " + value);
            }
        }
    }

    static void finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("weapon numbers must be finite");
        }
    }
}
