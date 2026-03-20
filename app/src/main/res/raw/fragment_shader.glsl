#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;
uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;
uniform int uFilterType;
void main() {
    vec4 tc = texture2D(sTexture, vTextureCoord);
    vec3 c = tc.rgb;
    if (uFilterType == 1) {
        float g = dot(c, vec3(0.299, 0.587, 0.114));
        c = vec3(g);
    } else if (uFilterType == 2) {
        float r = dot(c, vec3(0.393, 0.769, 0.189));
        float g = dot(c, vec3(0.349, 0.686, 0.168));
        float b = dot(c, vec3(0.272, 0.534, 0.131));
        c = vec3(r, g, b);
    } else if (uFilterType == 3) {
        float g = dot(c, vec3(0.299, 0.587, 0.114));
        c = mix(vec3(g), c, 1.6);
        c = (c - 0.5) * 1.15 + 0.5;
    } else if (uFilterType == 4) {
        c.r *= 0.85;
        c.b = min(c.b * 1.2 + 0.05, 1.0);
    } else if (uFilterType == 5) {
        c.r = min(c.r * 1.2 + 0.05, 1.0);
        c.b *= 0.85;
    } else if (uFilterType == 6) {
        c = c * 0.8 + 0.1;
        float g = dot(c, vec3(0.299, 0.587, 0.114));
        c = mix(vec3(g), c, 0.85);
    } else if (uFilterType == 7) {
        float g = dot(c, vec3(0.299, 0.587, 0.114));
        c = vec3(clamp((g - 0.5) * 1.5 + 0.5, 0.0, 1.0));
    }
    c += uBrightness;
    c = (c - 0.5) * uContrast + 0.5;
    float gv = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(gv), c, uSaturation);
    gl_FragColor = vec4(clamp(c, 0.0, 1.0), tc.a);
}
