#version 150

uniform sampler2D RigColor;
uniform sampler2D RigDepth;
uniform sampler2D BodyDepth;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 rig = texture(RigColor, texCoord);
    if (rig.a <= 0.0) discard;
    // The rig already passed the world depth test. Its color takes priority over
    // the local torso, while both surfaces retain depth for later world passes.
    fragColor = rig;
    gl_FragDepth = min(texture(RigDepth, texCoord).r, texture(BodyDepth, texCoord).r);
}
