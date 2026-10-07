package net.schmalenstroer.vumeter.viz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.audio.AudioEngine
import kotlin.math.abs
import kotlin.math.max

/**
 * Röhren-Oszilloskop mit grünem Phosphor: getriggerte Darstellung (steigende Flanke),
 * Nachleuchten der letzten Durchläufe und Raster wie bei einem Laborgerät.
 */
class PhosphorScope : Visualizer("Phosphor-Oszilloskop") {
    private var bg: Bitmap? = null
    private val screen = RectF()
    private val traces = Array(HISTORY) { FloatArray((POINTS - 1) * 4) }
    private var traceIdx = 0
    private var traceCount = 0
    private val prevWave = FloatArray(POINTS)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = Color.rgb(190, 255, 200) }

    override fun onResize(w: Int, h: Int) {
        var sw = w * 0.86f
        var sh = sw * 0.8f
        if (sh > h * 0.84f) { sh = h * 0.84f; sw = sh / 0.8f }
        screen.set((w - sw) / 2, (h - sh) / 2, (w + sw) / 2, (h + sh) / 2)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(24, 25, 24))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Röhrenschirm mit Vignette
        p.shader = RadialGradient(screen.centerX(), screen.centerY(), sw * 0.65f,
            Color.rgb(10, 28, 16), Color.rgb(2, 8, 4), Shader.TileMode.CLAMP)
        c.drawRoundRect(screen, sh * 0.05f, sh * 0.05f, p)
        p.shader = null
        // Raster 10 × 8 mit Unterteilung
        p.color = Color.argb(70, 120, 200, 140)
        p.strokeWidth = max(1f, sh * 0.002f)
        for (i in 0..10) { val x = screen.left + i * sw / 10; c.drawLine(x, screen.top, x, screen.bottom, p) }
        for (j in 0..8) { val y = screen.top + j * sh / 8; c.drawLine(screen.left, y, screen.right, y, p) }
        p.color = Color.argb(110, 120, 200, 140)
        val cx = screen.centerX()
        val cy = screen.centerY()
        for (i in 0 until 50) {
            val x = screen.left + i * sw / 50
            c.drawLine(x, cy - sh * 0.01f, x, cy + sh * 0.01f, p)
        }
        for (j in 0 until 40) {
            val y = screen.top + j * sh / 40
            c.drawLine(cx - sh * 0.01f, y, cx + sh * 0.01f, y, p)
        }
        // Beschriftung
        p.color = Color.rgb(150, 160, 150)
        p.typeface = Typeface.MONOSPACE
        p.textSize = h * 0.03f
        val msDiv = POINTS * STEP * 1000f / AudioEngine.SAMPLE_RATE / 10f
        c.drawText("CH1  AUTO", screen.left, screen.top - h * 0.015f, p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText(String.format(java.util.Locale.GERMANY, "%.2f ms/div   TRIG ↑", msDiv), screen.right, screen.top - h * 0.015f, p)
        bg = b
        glow.strokeWidth = sh * 0.012f
        core.strokeWidth = max(2f, sh * 0.004f)
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        val b = bg ?: return
        c.drawBitmap(b, 0f, 0f, null)
        val wave = a.frame.wave
        val span = POINTS * STEP
        // Trigger: steigender Nulldurchgang; unter mehreren Kandidaten den wählen, der dem
        // vorherigen Durchlauf am ähnlichsten ist → stehendes Bild auch bei komplexen Signalen
        var trig = 0
        var bestErr = Float.MAX_VALUE
        var found = 0
        val thr = a.waveRef * 0.05f
        var i0 = 8
        while (i0 < wave.size - span - 8 && found < 16) {
            if (wave[i0 - 1] <= 0f && wave[i0] > 0f && wave[i0 + 6] - wave[i0 - 6] > thr) {
                found++
                var err = 0f
                var j = 0
                while (j < POINTS) { err += abs(wave[i0 + j * STEP] - prevWave[j]); j += 4 }
                if (err < bestErr) { bestErr = err; trig = i0 }
                i0 += 8
            } else i0++
        }
        for (j in 0 until POINTS) prevWave[j] = wave[trig + j * STEP]
        val t = traces[traceIdx]
        val sw = screen.width()
        val half = screen.height() * 0.45f
        val scale = half / (a.waveRef * 1.1f)
        val cy = screen.centerY()
        var k = 0
        var px = 0f
        var py = 0f
        for (i in 0 until POINTS) {
            val v = wave[trig + i * STEP]
            val x = screen.left + i * sw / (POINTS - 1)
            val y = (cy - v * scale).coerceIn(screen.top, screen.bottom)
            if (i > 0) { t[k++] = px; t[k++] = py; t[k++] = x; t[k++] = y }
            px = x; py = y
        }
        traceCount = minOf(HISTORY, traceCount + 1)
        // Ältere Durchläufe schwächer (Nachleuchten), neuester hell mit Glüheffekt
        for (n in traceCount - 1 downTo 1) {
            val idx = (traceIdx - n + HISTORY) % HISTORY
            glow.color = Color.argb((90 / (n + 1)), 80, 255, 120)
            c.drawLines(traces[idx], glow)
        }
        glow.color = Color.argb(90, 80, 255, 120)
        c.drawLines(t, glow)
        c.drawLines(t, core)
        traceIdx = (traceIdx + 1) % HISTORY
    }

    companion object {
        private const val POINTS = 256
        private const val STEP = 2
        private const val HISTORY = 5
    }
}
