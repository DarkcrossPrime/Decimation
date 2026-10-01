package com.decimation.client.content;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed animation tracks, built once when resources load rather than scanned for every face. */
public record DanimAnimation(int version, int length, boolean isStatic, int hand,
                             Map<String, List<Keyframe>> tracks) {
    public DanimAnimation {
        Map<String, List<Keyframe>> copy = new LinkedHashMap<>();
        tracks.forEach((name, frames) -> copy.put(name, List.copyOf(frames)));
        tracks = Map.copyOf(copy);
    }

    public record Keyframe(int frame, float[] position, float[] rotation) {
        public Keyframe {
            position = position.clone();
            rotation = rotation.clone();
        }
    }
}
