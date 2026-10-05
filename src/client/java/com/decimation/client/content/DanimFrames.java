package com.decimation.client.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Sparse source tracks compiled into immutable tick samples during model preparation. */
public record DanimFrames(int length, Map<String, Track> tracks, Track identity) {
    public DanimFrames { tracks = Map.copyOf(tracks); }
    public Track track(String name) {
        var exact = tracks.get(name);
        if (exact != null) return exact;
        // Recovered magazines/bolts are split into numbered objects but share one assembly track.
        if (name.startsWith("ammoModel") && name.length() > 9 && name.substring(9).chars().allMatch(Character::isDigit)) {
            return tracks.getOrDefault("ammoModel0", identity);
        }
        return identity;
    }
    public Track track(String name, String fallback) { return tracks.containsKey(name) ? tracks.get(name) : track(fallback); }

    public static DanimFrames empty() { return new DanimFrames(0, Map.of(), new Track(List.of(Motion.IDENTITY))); }

    public static DanimFrames load(Reader input) {
        JsonObject root = JsonParser.parseReader(input).getAsJsonObject();
        if (!"decimation:danim".equals(root.get("format").getAsString()) || integer(root.get("version")) != 1) {
            throw new IllegalArgumentException("unsupported DANIM format/version");
        }
        int length = integer(root.get("length"));
        if (length < 0 || length > 1200) throw new IllegalArgumentException("DANIM length must be 0..1200 ticks");
        boolean isStatic = root.has("static") && root.get("static").getAsBoolean();
        boolean modelYDown = root.has("source") && root.get("source").getAsString().endsWith(".anib");
        Map<String, List<Key>> keys = new HashMap<>();
        for (JsonElement value : root.getAsJsonArray("frames")) {
            JsonObject frame = value.getAsJsonObject();
            int tick = integer(frame.get("index"));
            if (tick < 0 || tick > length) throw new IllegalArgumentException("DANIM key outside animation");
            if (!frame.has("transforms")) continue;
            for (var entry : frame.getAsJsonObject("transforms").entrySet()) {
                JsonObject motion = entry.getValue().getAsJsonObject();
                keys.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(new Key(tick,
                    new Motion(component(motion, "position", 0), component(motion, "position", 1), component(motion, "position", 2),
                        component(motion, "rotation", 0), component(motion, "rotation", 1), component(motion, "rotation", 2))));
            }
        }
        Map<String, Track> compiled = new HashMap<>();
        keys.forEach((name, values) -> {
            values.sort(Comparator.comparingInt(Key::tick));
            for (int i = 1; i < values.size(); i++) if (values.get(i - 1).tick == values.get(i).tick) {
                throw new IllegalArgumentException("duplicate DANIM key for " + name);
            }
            List<Motion> samples = new ArrayList<>(length + 1);
            for (int tick = 0; tick <= length; tick++) samples.add(sample(values, tick, length, isStatic));
            compiled.put(name, new Track(samples, modelYDown));
        });
        return new DanimFrames(length, compiled, new Track(java.util.Collections.nCopies(length + 1, Motion.IDENTITY), modelYDown));
    }

    private static Motion sample(List<Key> keys, int tick, int length, boolean isStatic) {
        Key first = keys.getFirst();
        if (tick < first.tick) {
            int spacing = keys.size() > 1 ? keys.get(1).tick - first.tick : 1;
            return Motion.interpolate(Motion.IDENTITY, first.motion, progress(Math.max(0, first.tick - spacing), first.tick, tick));
        }
        for (int i = 1; i < keys.size(); i++) {
            Key next = keys.get(i), previous = keys.get(i - 1);
            if (tick <= next.tick) return Motion.interpolate(previous.motion, next.motion, progress(previous.tick, next.tick, tick));
        }
        Key last = keys.getLast();
        if (isStatic || last.tick >= length) return last.motion;
        int spacing = keys.size() > 1 ? last.tick - keys.get(keys.size() - 2).tick : 1;
        return Motion.interpolate(last.motion, Motion.IDENTITY, progress(last.tick, Math.min(length, last.tick + spacing), tick));
    }

    private static float progress(int from, int to, float value) { return to == from ? 1 : Math.clamp((value - from) / (to - from), 0, 1); }
    private static int integer(JsonElement value) { return value.getAsBigDecimal().intValueExact(); }
    private static float component(JsonObject value, String field, int index) {
        if (!value.has(field)) return 0;
        var vector = value.getAsJsonArray(field);
        if (vector.size() != 3) throw new IllegalArgumentException("DANIM vector must have three components");
        return vector.get(index).getAsFloat();
    }

    private record Key(int tick, Motion motion) { }

    public record Track(List<Motion> frames, boolean modelYDown) {
        public Track(List<Motion> frames) { this(frames, false); }
        public Track { frames = List.copyOf(frames);if (frames.isEmpty()) throw new IllegalArgumentException("empty animation track"); }
        public Motion sample(float frame) {
            float bounded = Float.isFinite(frame) ? Math.clamp(frame, 0, frames.size() - 1) : 0;
            int from = (int) bounded, to = Math.min(from + 1, frames.size() - 1);
            return Motion.interpolate(frames.get(from), frames.get(to), bounded - from);
        }
    }

    public record Motion(float x, float y, float z, float pitch, float yaw, float roll) {
        public static final Motion IDENTITY = new Motion(0, 0, 0, 0, 0, 0);
        public Motion {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || !Float.isFinite(pitch) || !Float.isFinite(yaw) || !Float.isFinite(roll)) {
                throw new IllegalArgumentException("non-finite DANIM transform");
            }
        }
        public static Motion interpolate(Motion a, Motion b, float delta) {
            if (delta <= 0 || a.equals(b)) return a;
            if (delta >= 1) return b;
            return new Motion(lerp(a.x, b.x, delta), lerp(a.y, b.y, delta), lerp(a.z, b.z, delta),
                angle(a.pitch, b.pitch, delta), angle(a.yaw, b.yaw, delta), angle(a.roll, b.roll, delta));
        }
        private static float lerp(float a, float b, float delta) { return a + (b - a) * delta; }
        private static float angle(float a, float b, float delta) { return a + (((b - a + 180) % 360 + 360) % 360 - 180) * delta; }
    }
}
