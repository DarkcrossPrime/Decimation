package com.decimation.client.gun;

/** Tick-owned visual motion. Gameplay accuracy and shot cadence remain server-owned. */
public final class WeaponMotion {
    private float aim, previousAim, sprint, previousSprint;
    private float pitch, previousPitch, yaw, previousYaw;
    private float runningFire, previousRunningFire;
    private final com.decimation.module.gun.WeaponCarry carry = new com.decimation.module.gun.WeaponCarry();
    private final WeaponAmbientMotion ambient = new WeaponAmbientMotion();
    private float previousCarry;

    public void tick(boolean aiming, boolean sprinting, int adsTicks) {
        tick(aiming, sprinting, false, adsTicks);
    }
    public void tick(boolean aiming, boolean sprinting, boolean trigger, int adsTicks) {
        tick(aiming, sprinting, trigger, adsTicks, false, false);
    }
    public void tick(boolean aiming, boolean sprinting, boolean trigger, int adsTicks, boolean relaxed, boolean reload) {
        previousCarry = carry.fraction();carry.tick(relaxed, trigger, aiming, reload);
        previousAim = aim;previousSprint = sprint;previousPitch = pitch;previousYaw = yaw;
        aim = Math.clamp(aim + (aiming && !sprinting && carry.ready() ? 1 : -1) / (float) Math.max(1, adsTicks), 0, 1);
        sprint = Math.clamp(sprint + (sprinting ? 0.25f : -0.25f), 0, 1);
        previousRunningFire = runningFire;
        runningFire = Math.clamp(runningFire + (sprinting && trigger ? .5f : -.25f), 0, 1);
        pitch *= 0.72f;yaw *= 0.65f;
    }

    public void fired(float pitchKick, float yawKick) {
        carry.reset();previousCarry = 0; // A server-confirmed shot is always in the ready position.
        pitch += pitchKick;yaw += yawKick;
        // Confirmed shots should show their kick immediately, then interpolate their recovery.
        previousPitch = pitch;previousYaw = yaw;
        if (sprint > 0) runningFire = previousRunningFire = 1;
    }

    public Snapshot sample(float partialTick) {
        float delta = Float.isFinite(partialTick) ? Math.clamp(partialTick, 0, 1) : 0;
        return new Snapshot(lerp(previousAim, aim, delta), lerp(previousSprint, sprint, delta),
            lerp(previousPitch, pitch, delta), lerp(previousYaw, yaw, delta), lerp(previousRunningFire, runningFire, delta), lerp(previousCarry, carry.fraction(), delta), WeaponAmbientMotion.Pose.NONE);
    }

    public void updateAmbient(float speed, float yawDelta, float pitchDelta) { ambient.tick(speed, yawDelta, pitchDelta); }
    public Snapshot sample(float partialTick, double time, float suppression) {
        var pose = sample(partialTick);
        float delta = Float.isFinite(partialTick) ? Math.clamp(partialTick, 0, 1) : 0;
        return new Snapshot(pose.aim(), pose.sprint(), pose.recoilPitch(), pose.recoilYaw(), pose.runningFire(), pose.relaxed(),
            ambient.sample(time, delta, pose.aim(), suppression, 1 - pose.relaxed()));
    }

    public void reset() { carry.reset();ambient.reset();previousCarry = 0; aim = previousAim = sprint = previousSprint = pitch = previousPitch = yaw = previousYaw = runningFire = previousRunningFire = 0; }
    private static float lerp(float a, float b, float delta) { return a + (b - a) * delta; }
    public record Snapshot(float aim, float sprint, float recoilPitch, float recoilYaw, float runningFire, float relaxed, WeaponAmbientMotion.Pose ambient) {
        public Snapshot(float aim, float sprint, float recoilPitch, float recoilYaw, float runningFire) {
            this(aim, sprint, recoilPitch, recoilYaw, runningFire, 0, WeaponAmbientMotion.Pose.NONE);
        }
        public Snapshot(float aim, float sprint, float recoilPitch, float recoilYaw) { this(aim, sprint, recoilPitch, recoilYaw, 0); }
        public float sprintCarry() { return sprint * (1 - runningFire); }
        public float carry() { return Math.max(sprintCarry(), relaxed); }
        public static final Snapshot REST = new Snapshot(0, 0, 0, 0);
    }
}
