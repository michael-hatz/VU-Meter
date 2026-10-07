package net.schmalenstroer.vumeter.viz

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.BandMapper
import net.schmalenstroer.vumeter.audio.AudioEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Kreisförmiges, gespiegeltes Spektrum mit rotierendem Regenbogen-Verlauf,
 * pulsierendem Bass-Kern und Wellenform-Ring. Alle Strahlen in einem drawLines-Aufruf.
 */
class RadialSpectrum : Visualizer("Radial") {
    private val mapper = BandMapper(N, 40f, 12000f)
    private val vals = FloatArray(N)
    private val smooth = FloatArray(N)
    private val lines = FloatArray(N * 2 * 4)
    private val ring = FloatArray(WP * 4)
    private val cosT = FloatArray(N * 2)
    private val sinT = FloatArray(N * 2)
    private val wcos = FloatArray(WP)
    private val wsin = FloatArray(WP)
    private val matrix = Matrix()
    private var cx = 0f
    private var cy = 0f
    private var base = 0f

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255) }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sweep: SweepGradient? = null

    init {
        for (i in 0 until N * 2) {
            val a = 2.0 * PI * i / (N * 2) - PI / 2
            cosT[i] = cos(a).toFloat(); sinT[i] = sin(a).toFloat()
        }
        for (j in 0 until WP) {
            val a = 2.0 * PI * j / WP - PI / 2
            wcos[j] = cos(a).toFloat(); wsin[j] = sin(a).toFloat()
        }
    }

    override fun onResize(w: Int, h: Int) {
        cx = w / 2f
        cy = h / 2f
        base = min(w, h).toFloat()
        val colors = intArrayOf(
            Color.rgb(255, 0, 90), Color.rgb(255, 140, 0), Color.rgb(255, 230, 0), Color.rgb(0, 230, 120),
            Color.rgb(0, 180, 255), Color.rgb(140, 60, 255), Color.rgb(255, 0, 90),
        )
        sweep = SweepGradient(cx, cy, colors, null)
        barPaint.shader = sweep
        glowPaint.shader = RadialGradient(cx, cy, base * 0.3f,
            Color.argb(255, 255, 80, 160), Color.argb(0, 80, 0, 160), Shader.TileMode.CLAMP)
        ringPaint.strokeWidth = max(2f, base * 0.004f)
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        a.bands(mapper, vals)
        var bass = 0f
        for (i in 0 until N) {
            smooth[i] = if (vals[i] > smooth[i]) vals[i] else max(vals[i], smooth[i] - 1.8f * dt)
            if (i < 6) bass += smooth[i]
        }
        bass /= 6f

        val r0 = base * (0.15f + 0.05f * bass)
        val len = base * 0.26f
        val gap = base * 0.02f

        // Bass-Leuchten im Zentrum
        glowPaint.alpha = (bass * bass * 200).toInt().coerceIn(0, 200)
        c.drawCircle(cx, cy, base * 0.3f, glowPaint)

        // Gespiegelt: rechte Hälfte tief→hoch im Uhrzeigersinn, linke Hälfte gespiegelt
        val total = N * 2
        var k = 0
        for (i in 0 until total) {
            val b = if (i < N) i else total - 1 - i
            val ri = r0 + gap
            val ro = ri + max(base * 0.004f, smooth[b] * len)
            lines[k++] = cx + cosT[i] * ri
            lines[k++] = cy + sinT[i] * ri
            lines[k++] = cx + cosT[i] * ro
            lines[k++] = cy + sinT[i] * ro
        }
        matrix.setRotate(a.time * 25f, cx, cy)
        sweep?.setLocalMatrix(matrix)
        barPaint.strokeWidth = (2f * PI.toFloat() * (r0 + gap) / total) * 0.6f
        c.drawLines(lines, barPaint)

        // Wellenform als Ring
        val wave = a.frame.wave
        val step = AudioEngine.WAVE_SIZE / WP
        val rw = r0 * 0.78f
        val inv = 1f / a.waveRef
        var px = 0f
        var py = 0f
        var fx = 0f
        var fy = 0f
        k = 0
        for (j in 0 until WP) {
            val v = (wave[j * step] * inv).coerceIn(-1f, 1f)
            val rr = rw * (1f + 0.28f * v)
            val x = cx + wcos[j] * rr
            val y = cy + wsin[j] * rr
            if (j == 0) { fx = x; fy = y } else {
                ring[k++] = px; ring[k++] = py; ring[k++] = x; ring[k++] = y
            }
            px = x; py = y
        }
        ring[k++] = px; ring[k++] = py; ring[k++] = fx; ring[k] = fy
        c.drawLines(ring, ringPaint)
    }

    companion object {
        private const val N = 64
        private const val WP = 128
    }
}
