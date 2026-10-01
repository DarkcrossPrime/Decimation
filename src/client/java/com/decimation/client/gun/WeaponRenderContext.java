package com.decimation.client.gun;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** The owner of the held weapon currently being drawn by the player item feature. */
public final class WeaponRenderContext {
    private static final Deque<UUID> OWNERS = new ArrayDeque<>();

    private WeaponRenderContext() { }

    public static void begin(UUID player) {
        OWNERS.push(player);
    }

    public static void end() {
        OWNERS.pop();
    }

    public static UUID owner() {
        return OWNERS.peek();
    }
}
