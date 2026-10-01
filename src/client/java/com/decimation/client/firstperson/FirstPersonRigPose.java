package com.decimation.client.firstperson;

/** Frame-local rig pose. Angles are radians; shoulder travel is in model pixels. */
public record FirstPersonRigPose(
    float cameraPitch,
    float torsoPitch,
    float shoulderPitch,
    float shoulderForward,
    float cameraSafetyForward,
    float cameraSafetyPitch,
    float adsProgress
) { }
