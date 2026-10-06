package com.decimation.client.firstperson;

/** Frame-local rig pose. Angles are radians; shoulder travel is in model pixels. */
public record FirstPersonRigPose(
    float shoulderPitch,
    float shoulderForward,
    float cameraSafetyPitch
) { }
