package net.schmalenstroer.vumeter.milk

import android.opengl.GLES20.*
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal object Gl {
    private const val TAG = "MilkGl"

    fun program(vs: String, fs: String): Int {
        val v = shader(GL_VERTEX_SHADER, vs)
        val f = shader(GL_FRAGMENT_SHADER, fs)
        val p = glCreateProgram()
        glAttachShader(p, v)
        glAttachShader(p, f)
        glLinkProgram(p)
        val ok = IntArray(1)
        glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = glGetProgramInfoLog(p)
            glDeleteProgram(p)
            throw IllegalStateException("Link: $log")
        }
        glDeleteShader(v)
        glDeleteShader(f)
        return p
    }

    private fun shader(type: Int, src: String): Int {
        val s = glCreateShader(type)
        glShaderSource(s, src)
        glCompileShader(s)
        val ok = IntArray(1)
        glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = glGetShaderInfoLog(s)
            Log.e(TAG, "Shader:\n$src\n$log")
            glDeleteShader(s)
            throw IllegalStateException("Shader: $log")
        }
        return s
    }

    fun floats(n: Int): FloatBuffer =
        ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun floats(vararg v: Float): FloatBuffer = floats(v.size).apply { put(v); position(0) }

    /** Präzision für Texturkoordinaten: highp, wo verfügbar (sonst Flimmern in der Rückkopplung). */
    const val UV_PRECISION = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
varying highp vec2 vUv;
#else
varying mediump vec2 vUv;
#endif
"""

    const val WARP_VS = """
attribute vec2 aPos;
uniform float uAspect;
uniform vec2 uCenter;
uniform float uZoom;
uniform float uZoomExp;
uniform float uRot;
uniform float uWarp;
uniform float uWarpPhase;
uniform float uWarpScaleInv;
uniform vec4 uF;
uniform vec2 uD;
uniform vec2 uS;
uniform vec3 uRad;
uniform vec3 uAng;
uniform float uK;
varying vec2 vUv;
void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    vec2 q = vec2(aPos.x * uAspect, aPos.y);
    float rad = length(q) / length(vec2(uAspect, 1.0));
    float ang = atan(q.y, q.x + 0.00001);
    float zoom = uZoom + uRad.x * sin(rad * uRad.y - uRad.z);
    float rot = (uRot + uAng.x * sin(ang * uAng.y + uAng.z)) * uK;
    float z2 = pow(max(zoom, 0.01), pow(uZoomExp, rad * 2.0 - 1.0) * uK);
    vec2 c = vec2((uCenter.x * 2.0 - 1.0) * uAspect, uCenter.y * 2.0 - 1.0);
    vec2 r = (q - c) / z2 / uS;
    float cs = cos(rot);
    float sn = sin(rot);
    r = vec2(r.x * cs - r.y * sn, r.x * sn + r.y * cs) + c;
    vec2 uv = vec2(r.x / uAspect, r.y) * 0.5 + 0.5;
    float w = uWarp * 0.0035 * uK;
    float x = aPos.x;
    float y = aPos.y;
    float t = uWarpPhase;
    float s = uWarpScaleInv;
    uv.x += w * sin(t * 0.333 + s * (x * uF.x - y * uF.w));
    uv.y += w * cos(t * 0.375 - s * (x * uF.z + y * uF.y));
    uv.x += w * cos(t * 0.753 - s * (x * uF.y - y * uF.z));
    uv.y += w * sin(t * 0.825 + s * (x * uF.x + y * uF.w));
    vUv = uv - uD * uK;
}
"""

    const val WARP_FS = """
precision mediump float;
$UV_PRECISION
uniform sampler2D uTex;
uniform float uDecay;
uniform float uFade;
void main() {
    vec3 c = texture2D(uTex, vUv).rgb;
    gl_FragColor = vec4(max(c * uDecay - uFade, 0.0), 1.0);
}
"""

    const val COMP_VS = """
attribute vec2 aPos;
uniform vec2 uEcho;
varying vec2 vUv;
varying vec2 vEcho;
void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    vUv = aPos * 0.5 + 0.5;
    vEcho = aPos * uEcho * 0.5 + 0.5;
}
"""

    const val COMP_FS = """
precision mediump float;
$UV_PRECISION
varying vec2 vEcho;
uniform sampler2D uTex;
uniform float uEchoAlpha;
uniform float uGamma;
void main() {
    vec3 c = texture2D(uTex, vUv).rgb;
    if (uEchoAlpha > 0.001) c = mix(c, texture2D(uTex, vEcho).rgb, uEchoAlpha);
    gl_FragColor = vec4(min(c * uGamma, vec3(1.0)), 1.0);
}
"""

    const val COLOR_VS = """
attribute vec2 aPos;
attribute vec4 aCol;
uniform float uPointSize;
varying vec4 vCol;
void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    gl_PointSize = uPointSize;
    vCol = aCol;
}
"""

    const val COLOR_FS = """
precision mediump float;
varying vec4 vCol;
void main() { gl_FragColor = vCol; }
"""

    const val TEX_VS = """
attribute vec2 aPos;
attribute vec2 aUv;
attribute vec4 aCol;
varying vec2 vUv;
varying vec4 vCol;
void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    vUv = aUv;
    vCol = aCol;
}
"""

    const val TEX_FS = """
precision mediump float;
$UV_PRECISION
varying vec4 vCol;
uniform sampler2D uTex;
void main() { gl_FragColor = texture2D(uTex, vUv) * vCol; }
"""
}
