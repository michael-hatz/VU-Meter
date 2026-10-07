package net.schmalenstroer.vumeter.viz

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.BandMapper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Vakuum-Fluoreszenz-Anzeige im Stil von Hi-Fi-Geräten der 80er (Spektrum + Pegelzeile). */
class VfdDisplay : Visualizer("VFD-Display") {
    private val mapper = BandMapper(BANDS)
    private val vals = FloatArray(BANDS)
    private val level = FloatArray(BANDS)
    private val peak = FloatArray(BANDS)
    private val hold = FloatArray(BANDS)
    private var off: Bitmap? = null
    private var on: Bitmap? = null
    private val colX0 = IntArray(BANDS)
    private val colX1 = IntArray(BANDS)
    private var bottom = 0f
    private var pitch = 0f
    private var gapY = 0f
    private val levelRow = RectF()
    private var levelPitch = 0f
    private var levelDisp = -60f
    private var levelHold = -60f
    private var levelHoldT = 0f
    private val src = Rect()

    override fun onResize(w: Int, h: Int) {
        val mx = w * 0.07f
        val top = h * 0.17f
        bottom = h * 0.70f
        pitch = (bottom - top) / SEGS
        gapY = pitch * 0.38f
        val colW = (w - 2 * mx) / BANDS
        levelRow.set(mx, h * 0.83f, w - mx, h * 0.88f)
        levelPitch = levelRow.width() / LEVEL_SEGS

        val offB = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val onB = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val co = Canvas(offB)
        val cn = Canvas(onB)
        // Getöntes Glas
        val bg = Paint()
        bg.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(6, 14, 16), Color.rgb(2, 5, 6), Shader.TileMode.CLAMP)
        co.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(pitch * 0.5f, BlurMaskFilter.Blur.NORMAL) }
        val sharp = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = RectF()
        fun seg(rect: RectF, color: Int) {
            sharp.color = color
            sharp.alpha = 20
            co.drawRect(rect, sharp)
            glow.color = color
            glow.alpha = 150
            cn.drawRect(rect, glow)
            sharp.alpha = 255
            cn.drawRect(rect, sharp)
        }
        for (b in 0 until BANDS) {
            val x0 = mx + b * colW + colW * 0.12f
            val x1 = x0 + colW * 0.76f
            colX0[b] = x0.toInt() - 2
            colX1[b] = x1.toInt() + 2
            for (j in 0 until SEGS) {
                val y1 = bottom - j * pitch
                r.set(x0, y1 - pitch + gapY, x1, y1)
                seg(r, if (j >= SEGS - 3) HOT else TEAL)
            }
        }
        for (i in 0 until LEVEL_SEGS) {
            val x0 = levelRow.left + i * levelPitch
            r.set(x0 + levelPitch * 0.2f, levelRow.top, x0 + levelPitch * 0.8f, levelRow.bottom)
            seg(r, if (MIN_DB + i >= 0f) HOT else TEAL)
        }

        // Beschriftungen in gedämpfter VFD-Farbe
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEAL; alpha = 150; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = h * 0.033f; letterSpacing = 0.15f
        }
        co.drawText("SPECTRUM ANALYZER", mx, h * 0.09f, txt)
        txt.textAlign = Paint.Align.RIGHT
        co.drawText("PEAK HOLD   AUTO", w - mx, h * 0.09f, txt)
        txt.textAlign = Paint.Align.CENTER
        txt.textSize = h * 0.028f
        for ((f, label) in LABELS) {
            var best = 0
            for (b in 0 until BANDS) if (abs(mapper.centerHz[b] - f) < abs(mapper.centerHz[best] - f)) best = b
            co.drawText(label, (colX0[best] + colX1[best]) / 2f, h * 0.76f, txt)
        }
        txt.textAlign = Paint.Align.LEFT
        co.drawText("LEVEL", levelRow.left, levelRow.top - h * 0.02f, txt)
        txt.textAlign = Paint.Align.CENTER
        for (db in intArrayOf(-40, -30, -20, -10, -5, 0, 5)) {
            val x = levelRow.left + (db - MIN_DB + 0.5f) * levelPitch
            co.drawText(if (db > 0) "+$db" else "$db", x, levelRow.bottom + h * 0.045f, txt)
        }
        off = offB
        on = onB
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        val offB = off ?: return
        val onB = on ?: return
        a.bands(mapper, vals)
        c.drawBitmap(offB, 0f, 0f, null)
        for (i in 0 until BANDS) {
            level[i] = if (vals[i] > level[i]) vals[i] else max(vals[i], level[i] - 1.4f * dt)
            if (level[i] >= peak[i]) { peak[i] = level[i]; hold[i] = 1.0f }
            else if (hold[i] > 0f) hold[i] -= dt
            else peak[i] = max(level[i], peak[i] - 0.6f * dt)
            val n = (level[i] * SEGS + 0.5f).toInt().coerceIn(0, SEGS)
            if (n > 0) {
                src.set(colX0[i], (bottom - n * pitch + gapY - pitch * 0.4f).toInt(), colX1[i], (bottom + pitch * 0.4f).toInt())
                c.drawBitmap(onB, src, src, null)
            }
            val pk = (peak[i] * SEGS + 0.5f).toInt().coerceIn(0, SEGS) - 1
            if (pk >= n) {
                val y1 = bottom - pk * pitch
                src.set(colX0[i], (y1 - pitch + gapY - pitch * 0.4f).toInt(), colX1[i], (y1 + pitch * 0.3f).toInt())
                c.drawBitmap(onB, src, src, null)
            }
        }
        val db = a.frame.peakDb - a.peakRef
        levelDisp = if (db > levelDisp) db else max(db, levelDisp - 18f * dt)
        if (levelDisp >= levelHold) { levelHold = levelDisp; levelHoldT = 1.5f }
        else { levelHoldT -= dt; if (levelHoldT <= 0f) levelHold = max(levelDisp, levelHold - 10f * dt) }
        val n = (levelDisp - MIN_DB).toInt().coerceIn(0, LEVEL_SEGS)
        val ext = (levelRow.height() * 0.6f).toInt()
        if (n > 0) {
            src.set(levelRow.left.toInt(), levelRow.top.toInt() - ext, (levelRow.left + n * levelPitch).toInt(), levelRow.bottom.toInt() + ext)
            c.drawBitmap(onB, src, src, null)
        }
        val hi = (levelHold - MIN_DB).toInt().coerceIn(0, LEVEL_SEGS - 1)
        if (hi >= n) {
            src.set((levelRow.left + hi * levelPitch).toInt(), levelRow.top.toInt() - ext,
                (levelRow.left + (hi + 1) * levelPitch).toInt(), levelRow.bottom.toInt() + ext)
            c.drawBitmap(onB, src, src, null)
        }
    }

    companion object {
        private const val BANDS = 20
        private const val SEGS = 14
        private const val MIN_DB = -40f
        private const val LEVEL_SEGS = 46
        private val TEAL = Color.rgb(70, 245, 220)
        private val HOT = Color.rgb(255, 110, 60)
        private val LABELS = listOf(
            63f to "63", 250f to "250", 1000f to "1k", 4000f to "4k", 14000f to "16k",
        )
    }
}
