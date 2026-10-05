package com.decimation.module.gun;

import com.decimation.module.gun.data.WeaponAudio;
import com.decimation.module.gun.data.WeaponCatalog;
import com.decimation.module.gun.data.WeaponSound;
import com.decimation.module.gun.data.WeaponSoundCue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Verify source-derived timing and actual generated positional audio resources. */
public final class WeaponAudioTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        ClassLoader loader = WeaponAudioTest.class.getClassLoader();
        WeaponCatalog catalog = WeaponCatalog.load(loader);
        WeaponAudio famas = catalog.get("decimation:famas").audio();
        WeaponAudio honeybadger = catalog.get("decimation:honeybadger").audio();
        check(famas.reloadCues().equals(List.of(new WeaponSoundCue(5, WeaponSound.MAG_OUT),
            new WeaponSoundCue(40, WeaponSound.MAG_IN), new WeaponSoundCue(44, WeaponSound.RACK))), "FAMAS source cue timing");
        check(honeybadger.reloadCues().equals(famas.reloadCues()), "Honey Badger source cue timing");
        check(catalog.get("decimation:famas_custom").audio().reloadCues().equals(List.of(
            new WeaponSoundCue(9, WeaponSound.MAG_OUT), new WeaponSoundCue(28, WeaponSound.MAG_IN),
            new WeaponSoundCue(37, WeaponSound.RACK))), "Custom FAMAS source cue timing");
        check(catalog.get("decimation:crossbow").audio().reloadCues().equals(
            List.of(new WeaponSoundCue(40, WeaponSound.INSERT_SHELL))), "Crossbow source cue timing");

        for (var weapon : catalog.definitions().values()) {
            for (boolean needsRack : new boolean[] {false, true}) {
                ReloadAudioTimeline timeline = new ReloadAudioTimeline(weapon.audio(), 100, needsRack);
                check(timeline.nextDue(99) == null, "nothing before reload starts");
                for (int tick = 0; tick <= weapon.reloadTicks() + 1; tick++) {
                    for (var cue : weapon.audio().reloadCues()) {
                        int due = cue.tick() + (cue.sound() == WeaponSound.RACK && needsRack ? ReloadAudioTimeline.RACK_DELAY_TICKS : 0);
                        if (due == tick && (cue.sound() != WeaponSound.RACK || needsRack)) {
                            check(timeline.nextDue(100 + tick) == cue.sound(), "cue emitted at exact server tick");
                        }
                    }
                    check(timeline.nextDue(100 + tick) == null, "no early or duplicate cue");
                }
            }
            ReloadAudioTimeline cancelled = new ReloadAudioTimeline(weapon.audio(), 100, true);
            cancelled.cancel();
            check(cancelled.nextDue(100 + weapon.reloadTicks()) == null, "switch/death cancellation drops pending cues");
        }
        ReloadAudioTimeline catchUp = new ReloadAudioTimeline(famas, 100, true);
        for (var cue : famas.reloadCues()) check(catchUp.nextDue(160) == cue.sound(), "overdue cues drain once in order");
        check(catchUp.nextDue(160) == null, "catch-up exhausted");
        var delayedRack = new ReloadAudioTimeline(famas, 100, true);
        check(delayedRack.nextDue(105) == WeaponSound.MAG_OUT, "mag out retains its source tick");
        check(delayedRack.nextDue(140) == WeaponSound.MAG_IN, "mag in retains its source tick");
        check(delayedRack.nextDue(144) == null, "rack waits one tick after its source cue");
        check(delayedRack.nextDue(145) == WeaponSound.RACK && delayedRack.nextDue(145) == null, "delayed rack plays once after 50 ms");
        var cancelledRack = new ReloadAudioTimeline(famas, 100, true);
        cancelledRack.nextDue(105);cancelledRack.nextDue(140);cancelledRack.nextDue(144);cancelledRack.cancel();
        check(cancelledRack.nextDue(145) == null, "cancellation during rack delay suppresses pending sound");
        WeaponAudio boundaryAudio = new WeaponAudio(WeaponSound.FIRE, 32, Map.of(WeaponSound.FIRE, "decimation:test"),
            List.of(new WeaponSoundCue(57, WeaponSound.MAG_IN), new WeaponSoundCue(0, WeaponSound.MAG_OUT)));
        ReloadAudioTimeline boundary = new ReloadAudioTimeline(boundaryAudio, 100, false);
        check(boundary.nextDue(100) == WeaponSound.MAG_OUT, "tick zero emits immediately; source cues sorted");
        check(boundary.nextDue(156) == null, "end cue waits for deadline");
        check(boundary.nextDue(157) == WeaponSound.MAG_IN, "reload end-tick cue is due before completion");

        check(famas.shotForDistance(31.999) == WeaponSound.FIRE, "near listener receives primary");
        check(famas.shotForDistance(32) == WeaponSound.FIRE_DISTANT, "threshold listener receives distant");
        check(famas.shotForDistance(128) == WeaponSound.FIRE_DISTANT, "far listener receives distant");
        check(honeybadger.shotForDistance(0) == WeaponSound.FIRE_SUPPRESSED
            && honeybadger.shotForDistance(100) == WeaponSound.FIRE_SUPPRESSED, "suppressed weapon never emits distant blast");
        check(boundaryAudio.shotForDistance(100) == WeaponSound.FIRE, "missing distant sample falls back to primary");
        check(famas.maximumShotRange() == 128 && honeybadger.maximumShotRange() == 32
            && boundaryAudio.maximumShotRange() == 64, "broadcast ranges follow available cues");
        check(WeaponAudio.volume(WeaponSound.FIRE) == 4 && WeaponAudio.volume(WeaponSound.FIRE_SUPPRESSED) == 2
            && WeaponAudio.volume(WeaponSound.FIRE_DISTANT) == 8 && WeaponAudio.volume(WeaponSound.MAG_OUT) == 1,
            "legacy sound volume/attenuation preserved");

        JsonObject events;
        try (var stream = loader.getResourceAsStream("assets/decimation/sounds.json")) {
            check(stream != null, "generated sounds.json exists");
            events = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Set<String> soundEvents = new HashSet<>(), soundFiles = new HashSet<>();
        for (var weapon : catalog.definitions().values()) {
            for (String id : weapon.audio().sounds().values()) {
                soundEvents.add(id);
                String[] key = id.split(":", 2);
                check(key[0].equals("decimation") && events.has(key[1]), "registered cue resolves to generated event");
                var samples = events.getAsJsonObject(key[1]).getAsJsonArray("sounds");
                check(!samples.isEmpty(), "event has samples");
                for (var sample : samples) {
                    String name = sample.isJsonPrimitive() ? sample.getAsString() : sample.getAsJsonObject().get("name").getAsString();
                    String[] resource = name.split(":", 2);
                    soundFiles.add("assets/" + resource[0] + "/sounds/" + resource[1] + ".ogg");
                }
            }
        }
        for (String file : soundFiles) {
            byte[] header;
            try (var stream = loader.getResourceAsStream(file)) {
                check(stream != null, "sound sample exists: " + file);
                header = stream.readNBytes(4096);
            }
            byte[] signature = {1, 'v', 'o', 'r', 'b', 'i', 's'};
            int marker = -1;
            outer: for (int i = 0; i + 16 <= header.length; i++) {
                for (int j = 0; j < signature.length; j++) if (header[i + j] != signature[j]) continue outer;
                marker = i;break;
            }
            check(marker >= 0 && header[marker + 11] == 1, "positional sample is mono Vorbis: " + file);
        }
        check(soundEvents.size() == 27 && soundFiles.size() == 28, "all four weapons and shared cues covered");
        System.out.println("Weapon audio checks passed: " + checks + " assertions; 27 events, 28 mono samples, timing/cancellation/ranges.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
