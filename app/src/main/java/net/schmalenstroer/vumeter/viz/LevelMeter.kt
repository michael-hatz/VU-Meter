package net.schmalenstroer.vumeter.viz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import net.schmalenstroer.vumeter.Analysis
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Horizontale LED-Pegelanzeige (RMS und Spitze) wie an einem Mischpult. */
class LevelMeter : Visualizer("Pegelmeter") {
    private var off: Bitmap? = null
    private var on: Bitmap? = null
    private val rows = arrayOf(RectF(), RectF())
    private var left = 0f
    private var pitch = 0f
    private val src = Rect()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 210, 205)
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
    }

    private val disp = floatArrayOf(MIN_DB, MIN_DB)
    private val holdDb = floatArrayOf(MIN_DB, MIN_DB)
    private val holdT = floatArrayOf(0f, 0f)
    private val shownDb = floatArrayOf(Float.NaN, Float.NaN)
    private val labels = arrayOf("", "")

    override fun onResize(w: Int, h: Int) {
        val mx = w * 0.06f
        left = mx
        val right = w - mx
        pitch = (right - left) / SEG
        val barH = h * 0.13f
        rows[0].set(left, h * 0.30f - barH / 2, right, h * 0.30f + barH / 2)
        rows[1].set(left, h * 0.66f - barH / 2, right, h * 0.66f + barH / 2)
        text.textSize = h * 0.05f

        val offB = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val onB = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val co = Canvas(offB)
        val cn = Canvas(onB)
        co.drawColor(Color.rgb(12, 12, 14))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = RectF()
        val gap = pitch * 0.25f
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(140, 145, 150)
            textSize = h * 0.032f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val title = Paint(lp).apply { textAlign = Paint.Align.LEFT; textSize = h * 0.045f; color = Color.rgb(190, 195, 200) }
        val names = arrayOf("VU  (RMS)", "PEAK")
        for ((ri, row) in rows.withIndex()) {
            for (i in 0 until SEG) {
                val x0 = left + i * pitch
                r.set(x0 + gap / 2, row.top, x0 + pitch - gap / 2, row.bottom)
                p.color = segColor(i)
                p.alpha = 36
                co.drawRect(r, p)
                p.alpha = 255
                cn.drawRect(r, p)
            }
            co.drawText(names[ri], left, row.top - h * 0.03f, title)
            for (db in SCALE) {
                val x = left + (db - MIN_DB) / (MAX_DB - MIN_DB) * (right - left)
                p.color = Color.rgb(140, 145, 150)
                co.drawRect(x - 1, row.bottom + h * 0.01f, x + 1, row.bottom + h * 0.03f, p)
                co.drawText(if (db > 0) "+$db" else "$db", x, row.bottom + h * 0.07f, lp)
            }
        }
        off = offB
        on = onB
    }

    private fun segColor(i: Int): Int {
        val db = MIN_DB + i + 0.5f
        return when {
            db < -9f -> Color.rgb(43, 224, 74)
            db < 0f -> Color.rgb(245, 208, 42)
            else -> Color.rgb(255, 59, 47)
        }
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        val offB = off ?: return
        val onB = on ?: return
        val rmsDb = a.frame.rmsDb - a.peakRef
        val pkDb = a.frame.peakDb - a.peakRef
        // RMS: schnell steigend, 15 dB/s fallend; Spitze: sofort, 20 dB/s fallend
        disp[0] = if (rmsDb > disp[0]) disp[0] + (rmsDb - disp[0]) * minOf(1f, dt * 25f) else max(rmsDb, disp[0] - 15f * dt)
        disp[1] = if (pkDb > disp[1]) pkDb else max(pkDb, disp[1] - 20f * dt)

        c.drawBitmap(offB, 0f, 0f, null)
        for (r in 0..1) {
            val d = disp[r].coerceIn(MIN_DB, MAX_DB)
            if (d >= holdDb[r]) {
                holdDb[r] = d; holdT[r] = 1.5f
            } else {
                holdT[r] -= dt
                if (holdT[r] <= 0f) holdDb[r] = max(d, holdDb[r] - 10f * dt)
            }
            val row = rows[r]
            val n = (d - MIN_DB).toInt().coerceIn(0, SEG)
            if (n > 0) {
                src.set(left.toInt(), row.top.toInt(), (left + n * pitch).toInt(), row.bottom.toInt() + 1)
                c.drawBitmap(onB, src, src, null)
            }
            val hi = (holdDb[r] - MIN_DB).toInt().coerceIn(0, SEG - 1)
            if (hi >= n) {
                src.set((left + hi * pitch).toInt(), row.top.toInt(), (left + (hi + 1) * pitch).toInt() + 1, row.bottom.toInt() + 1)
                c.drawBitmap(onB, src, src, null)
            }
            // Zahlenwert nur neu formatieren, wenn er sich sichtbar ändert
            val shown = (holdDb[r] * 2f).roundToInt() / 2f
            if (shown != shownDb[r]) {
                shownDb[r] = shown
                labels[r] = if (shown <= MIN_DB) "-∞ dB" else String.format(Locale.US, "%+.1f dB", shown)
            }
            c.drawText(labels[r], row.right, row.top - height * 0.03f, text)
        }
    }

    companion object {
        private const val MIN_DB = -48f
        private const val MAX_DB = 6f
        private const val SEG = 54
        private val SCALE = intArrayOf(-48, -36, -24, -18, -12, -9, -6, -3, 0, 3, 6)
    }
}
