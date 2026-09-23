package com.decimation.client.firstperson;

import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Frame-local state shared by the player and feature-renderer mixins.
 *
 * <p>The state is deliberately scoped around one synchronous entity render. It is never a
 * replacement for player gameplay state and must not escape the render thread.</p>
 */
public final class FirstPersonRenderState {
    private static ClientPlayerEntity player;

    private FirstPersonRenderState() { }

    static void begin(ClientPlayerEntity localPlayer) {
        if (player != null) {
            throw new IllegalStateException("Nested first-person body render");
        }
        player = localPlayer;
    }

    static void end() {
        player = null;
    }

    public static boolean isRenderingBody() {
        return player != null;
    }

}
