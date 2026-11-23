#version 330
#extension GL_ARB_separate_shader_objects : require

// Quasium colour grading. Parameters come from a 1x1 texture (see ColorGrade.java), 128/255 = neutral:
// R saturation, G contrast, B whiteness.

uniform sampler2D InSampler;
uniform sampler2D ParamsSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec4 color = texture(InSampler, texCoord);
    vec3 p = (texelFetch(ParamsSampler, ivec2(0, 0), 0).rgb * 255.0 - 128.0) / 127.0; // -1..1 each, 0 = neutral

    vec3 c = color.rgb;

    // Saturation: 0 = grey, 1 = unchanged, 2 = double.
    float luma = dot(c, LUMA);
    c = mix(vec3(luma), c, 1.0 + p.r);

    // Contrast around mid grey: 0.5x .. 1.5x.
    c = (c - 0.5) * (1.0 + 0.5 * p.g) + 0.5;
    c = clamp(c, 0.0, 1.0);

    // Whiteness: how easily bright colours reach white. A curve through 0 and 1 (blacks and full white stay put):
    // f(x) = x / (x + a (1 - x)), a = 1 neutral, a < 1 pushes towards white sooner, a > 1 holds highlights back.
    float a = exp2(-1.5 * p.b);
    c = c / (c + a * (1.0 - c) + 1e-6);

    fragColor = vec4(clamp(c, 0.0, 1.0), color.a);
}
