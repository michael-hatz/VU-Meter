package net.schmalenstroer.vumeter.viz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.BandMapper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Segmentiertes LED-Spektrum wie bei Hi-Fi-Equalizern.
 * Unbeleuchtete und beleuchtete Segmente sind vorgerendert; pro Band wird nur ein
 * Ausschnitt der "leuchtenden" Bitmap kopiert.
 */
class LedSpectrum : Visualizer("LED-Spektrum") {
    private val mapper = BandMapper(BANDS)
    private val vals = FloatArray(BANDS)
    private val level = FloatArray(BANDS)
    private val peak = FloatArray(BANDS)
    private val hold = FloatArray(BANDS)

    private var off: Bitmap? = null
    private var on: Bitmap? = null
    private val colX0 = IntArray(BANDS)
    private val colX1 = IntArray(BANDS)
    private var segs = 24
    private var bottom = 0f
    private var pitch = 0f
    private val src = Rect()
    private val paint = Paint()

    override fun onResize(w: Int, h: Int) {
        val mx = w * 0.04f
        val top = h * 0.06f
        bottom = h * 0.88f
        val areaW = w - 2 * mx
        segs = ((bottom - top) / (min(w, h) * 0.028f)).toInt().coerceIn(16, 40)
        pitch = (bottom - top) / segs
        val colW = areaW / BANDS
        val gapX = colW * 0.18f
        val gapY = pitch * 0.28f

        val offB = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val onB = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val co = Canvas(offB)
        val cn = Canvas(onB)
        co.drawColor(Color.rgb(8, 10, 9))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = RectF()
        val rad = min(pitch, colW) * 0.12f
        for (b in 0 until BANDS) {
            val x0 = mx + b * colW + gapX / 2
            val x1 = x0 + colW - gapX
            colX0[b] = x0.toInt() - 1
            colX1[b] = x1.toInt() + 1
            for (j in 0 until segs) {
                val y1 = bottom - j * pitch
                val y0 = y1 - pitch + gapY
                r.set(x0, y0, x1, y1)
                val col = segColor(j)
                p.color = col
                p.alpha = 34
                co.drawRoundRect(r, rad, rad, p)
                p.alpha = 255
                cn.drawRoundRect(r, rad, rad, p)
            }
        }
        // Frequenzbeschriftung
        p.color = Color.rgb(120, 130, 125)
        p.textSize = h * 0.035f
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        for ((f, label) in LABELS) {
            var best = 0
            for (b in 0 until BANDS) if (abs(mapper.centerHz[b] - f) < abs(mapper.centerHz[best] - f)) best = b
            co.drawText(label, (colX0[best] + colX1[best]) / 2f, h * 0.95f, p)
        }
        off = offB
        on = onB
    }

    private fun segColor(j: Int): Int {
        val f = (j + 0.5f) / segs
        return when {
            f < 0.6f -> Color.rgb(43, 224, 74)
            f < 0.85f -> Color.rgb(245, 208, 42)
            else -> Color.rgb(255, 59, 47)
        }
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        val offB = off ?: return
        val onB = on ?: return
        a.bands(mapper, vals)
        for (i in 0 until BANDS) {
            level[i] = if (vals[i] > level[i]) vals[i] else max(vals[i], level[i] - 1.6f * dt)
            if (level[i] >= peak[i]) {
                peak[i] = level[i]; hold[i] = 0.8f
            } else if (hold[i] > 0f) {
                hold[i] -= dt
            } else {
                peak[i] = max(level[i], peak[i] - 0.7f * dt)
            }
        }
        c.drawBitmap(offB, 0f, 0f, null)
        for (i in 0 until BANDS) {
            val n = (level[i] * segs + 0.5f).toInt().coerceIn(0, segs)
            if (n > 0) {
                src.set(colX0[i], (bottom - n * pitch).toInt(), colX1[i], bottom.toInt() + 1)
                c.drawBitmap(onB, src, src, paint)
            }
            val pk = (peak[i] * segs + 0.5f).toInt().coerceIn(0, segs) - 1
            if (pk >= n) {
                src.set(colX0[i], (bottom - (pk + 1) * pitch).toInt(), colX1[i], (bottom - pk * pitch).toInt() + 1)
                c.drawBitmap(onB, src, src, paint)
            }
        }
    }

    companion object {
        private const val BANDS = 32
        private val LABELS = listOf(
            63f to "63", 125f to "125", 250f to "250", 500f to "500", 1000f to "1k",
            2000f to "2k", 4000f to "4k", 8000f to "8k", 14000f to "16k",
        )
    }
}
