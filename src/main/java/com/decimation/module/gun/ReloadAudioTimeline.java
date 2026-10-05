package com.decimation.module.gun;

import com.decimation.module.gun.data.WeaponAudio;
import com.decimation.module.gun.data.WeaponSound;
import com.decimation.module.gun.data.WeaponSoundCue;
import java.util.List;

/** One cursor per active reload. It does not parse animations or schedule client timers. */
public final class ReloadAudioTimeline {
    public static final int RACK_DELAY_TICKS = 1;
    private final List<WeaponSoundCue> cues;
    private final long startedAt;
    private final boolean needsRack;
    private int index;
    private boolean cancelled;

    public ReloadAudioTimeline(WeaponAudio audio, long startedAt, boolean needsRack) {
        this.cues = audio.reloadCues();
        this.startedAt = startedAt;
        this.needsRack = needsRack;
    }

    public WeaponSound nextDue(long tick) {
        if (cancelled || tick < startedAt) return null;
        long elapsed = tick - startedAt;
        while (index < cues.size()) {
            WeaponSoundCue cue = cues.get(index);
            int due = cue.tick() + (cue.sound() == WeaponSound.RACK && needsRack ? RACK_DELAY_TICKS : 0);
            if (due > elapsed) return null;
            index++;
            if (cue.sound() != WeaponSound.RACK || needsRack) return cue.sound();
        }
        return null;
    }

    public void cancel() { cancelled = true; }
}
