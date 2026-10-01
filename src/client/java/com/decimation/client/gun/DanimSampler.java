package com.decimation.client.gun;

import com.decimation.client.content.DanimAnimation;
import java.util.List;

/** Interpolates sparse DANIM keys at fractional render frames. */
public final class DanimSampler {
    private DanimSampler() { }

    public static Transform sample(DanimAnimation animation, String object, float requestedFrame) {
        List<DanimAnimation.Keyframe> keys = animation.tracks().get(object);
        if (keys == null || keys.isEmpty()) return Transform.IDENTITY;

        float frame = Math.max(0, Math.min(requestedFrame, animation.length()));
        DanimAnimation.Keyframe first = keys.get(0);
        if (frame < first.frame()) {
            int spacing = keys.size() > 1 ? keys.get(1).frame() - first.frame() : 1;
            int start = Math.max(0, first.frame() - Math.max(1, spacing));
            return interpolate(Transform.IDENTITY, transform(first), progress(start, first.frame(), frame));
        }
        for (int index = 1; index < keys.size(); index++) {
            DanimAnimation.Keyframe next = keys.get(index);
            if (frame <= next.frame()) {
                DanimAnimation.Keyframe previous = keys.get(index - 1);
                return interpolate(transform(previous), transform(next),
                    progress(previous.frame(), next.frame(), frame));
            }
        }

        DanimAnimation.Keyframe last = keys.get(keys.size() - 1);
        if (animation.isStatic() || last.frame() >= animation.length()) return transform(last);
        int spacing = keys.size() > 1 ? last.frame() - keys.get(keys.size() - 2).frame() : 1;
        int end = Math.min(animation.length(), last.frame() + Math.max(1, spacing));
        return interpolate(transform(last), Transform.IDENTITY, progress(last.frame(), end, frame));
    }

    private static Transform transform(DanimAnimation.Keyframe key) {
        return new Transform(key.position(), key.rotation());
    }

    private static float progress(int from, int to, float frame) {
        return to == from ? 1.0f : Math.max(0, Math.min(1, (frame - from) / (to - from)));
    }

    private static Transform interpolate(Transform from, Transform to, float amount) {
        float[] position = new float[3];
        float[] rotation = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            position[axis] = from.position()[axis]
                + (to.position()[axis] - from.position()[axis]) * amount;
            float difference = (to.rotation()[axis] - from.rotation()[axis] + 540.0f) % 360.0f - 180.0f;
            rotation[axis] = from.rotation()[axis] + difference * amount;
        }
        return new Transform(position, rotation);
    }

    public record Transform(float[] position, float[] rotation) {
        public static final Transform IDENTITY = new Transform(new float[] {0, 0, 0}, new float[] {0, 0, 0});
    }
}
