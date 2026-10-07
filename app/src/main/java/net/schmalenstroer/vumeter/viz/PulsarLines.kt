package net.schmalenstroer.vumeter.viz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.BandMapper
import kotlin.math.max
import kotlin.random.Random

/**
 * Gestaffelte Spektrumlinien wie auf dem bekannten Pulsar-Plattencover.
 * Neue Linien entstehen vorne (unten) und verdecken die älteren. Inkrementell gerendert:
 * pro neuer Linie wird die Bitmap um eine Zeile verschoben und nur die neue Linie gezeichnet.
 */
class PulsarLines : Visualizer("Pulsar-Linien") {
    private val mapper = BandMapper(BANDS, 50f, 10000f)
    private val vals = FloatArray(BANDS)
    private val acc = FloatArray(BANDS)
    private var bmpA: Bitmap? = null
    private var bmpB: Bitmap? = null
    private var left = 0f
    private var top = 0f
    private var spacing = 0f
    private var aw = 0
    private var ah = 0
    private var sinceCommit = 0f
    private val path = Path()
    private val rnd = Random(7)
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeJoin = Paint.Join.ROUND }
    private val draw = Paint().apply { color = Color.rgb(240, 240, 236); isFilterBitmap = false }

    override fun onResize(w: Int, h: Int) {
        aw = (if (w >= h) w * 0.56f else w * 0.86f).toInt()
        ah = (h * 0.9f).toInt()
        left = (w - aw) / 2f
        top = (h - ah) / 2f
        spacing = ah / (LINES + HEADROOM_LINES).toFloat()
        stroke.strokeWidth = max(1.5f, spacing * 0.16f)
        bmpA = Bitmap.createBitmap(aw, ah, Bitmap.Config.ALPHA_8)
        bmpB = Bitmap.createBitmap(aw, ah, Bitmap.Config.ALPHA_8)
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun commit() {
        val a = bmpA ?: return
        val b = bmpB ?: return
        val c = Canvas(b)
        b.eraseColor(Color.TRANSPARENT)
        c.drawBitmap(a, 0f, -spacing, null)
        // Fläche unter der neuen Linie ausstanzen (verdeckt ältere Linien), dann Linie zeichnen
        path.reset()
        for (j in 0 until POINTS) {
            val x = j / (POINTS - 1f) * aw
            if (j == 0) path.moveTo(x, ys[j]) else path.lineTo(x, ys[j])
        }
        c.drawPath(path, stroke)
        path.lineTo(aw.toFloat(), ah.toFloat())
        path.lineTo(0f, ah.toFloat())
        path.close()
        c.drawPath(path, clear)
        // Strich nach dem Ausstanzen erneut, damit er vollständig sichtbar bleibt
        path.reset()
        for (j in 0 until POINTS) {
            val x = j / (POINTS - 1f) * aw
            if (j == 0) path.moveTo(x, ys[j]) else path.lineTo(x, ys[j])
        }
        c.drawPath(path, stroke)
        bmpA = b
        bmpB = a
        acc.fill(0f)
    }

    private val ys = FloatArray(POINTS)

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        c.drawColor(Color.BLACK)
        a.bands(mapper, vals)
        for (i in 0 until BANDS) acc[i] = max(acc[i], vals[i])
        sinceCommit += dt
        if (sinceCommit >= COMMIT_SEC) {
            sinceCommit = 0f
            prepareYs()
            commit()
        }
        val bmp = bmpA ?: return
        val frac = (sinceCommit / COMMIT_SEC).coerceIn(0f, 1f)
        c.drawBitmap(bmp, left, top - frac * spacing, draw)
    }

    /** Berechnet die y-Werte der neuen Linie einmal und legt sie für Fläche und Strich ab. */
    private fun prepareYs() {
        val base = ah - spacing * 1.5f
        val amp = spacing * 8f
        for (j in 0 until POINTS) {
            val u = j / (POINTS - 1f)
            val env = smoothstep(0.1f, 0.36f, u) * (1f - smoothstep(0.64f, 0.9f, u))
            val bf = ((u - 0.1f) / 0.8f).coerceIn(0f, 1f) * (BANDS - 1)
            val bi = bf.toInt().coerceAtMost(BANDS - 2)
            val fr = bf - bi
            val v = acc[bi] * (1f - fr) + acc[bi + 1] * fr
            val noise = rnd.nextFloat()
            ys[j] = base - env * (v * v * amp + noise * spacing * 0.6f) - (1f - env) * noise * spacing * 0.12f
        }
    }

    companion object {
        private const val BANDS = 48
        private const val POINTS = 110
        private const val LINES = 56
        private const val HEADROOM_LINES = 8
        private const val COMMIT_SEC = 0.09f
    }
}
