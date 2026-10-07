package net.schmalenstroer.vumeter.viz

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import net.schmalenstroer.vumeter.Analysis
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/**
 * Nixie-Uhr: Uhrzeit in sechs Ziffernröhren, Glimmpunkte pulsieren im Takt,
 * darunter zwei Balkenröhren (Typ IN-13) für RMS- und Spitzenpegel.
 */
class NixieClock : Visualizer("Nixie-Uhr") {
    private var tubeBg: Bitmap? = null
    private val digits = arrayOfNulls<Bitmap>(10)
    private val tubeX = FloatArray(6)
    private var tubeY = 0f
    private var tubeW = 0f
    private var tubeH = 0f
    private val colonX = FloatArray(2)
    private val bars = arrayOf(RectF(), RectF())
    private val cur = IntArray(6) { -1 }
    private val prev = IntArray(6)
    private val fade = FloatArray(6) { 1f }
    private val cal = Calendar.getInstance()
    private var pulse = 0f
    private var lastBeat = 0L
    private val barLevel = floatArrayOf(0f, 0f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val neon = Paint(Paint.ANTI_ALIAS_FLAG)
    private val neonGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(140, 120, 100); typeface = Typeface.DEFAULT_BOLD
    }

    override fun onResize(w: Int, h: Int) {
        tubeW = min(w * 0.12f, h * 0.3f)
        tubeH = tubeW * 1.75f
        val gap = tubeW * 0.12f
        val colonW = tubeW * 0.45f
        val total = 6 * tubeW + 3 * gap + 2 * colonW
        var x = (w - total) / 2
        tubeY = h * 0.42f - tubeH / 2
        for (i in 0 until 6) {
            tubeX[i] = x
            x += tubeW
            if (i == 1 || i == 3) {
                colonX[i / 2] = x + colonW / 2
                x += colonW
            } else if (i < 5) x += gap
        }
        val bw = total
        val bh = tubeW * 0.22f
        val bx = (w - bw) / 2
        bars[0].set(bx, h * 0.80f, bx + bw, h * 0.80f + bh)
        bars[1].set(bx, h * 0.80f + bh * 1.9f, bx + bw, h * 0.80f + bh * 2.9f)
        label.textSize = bh * 0.7f
        build()
    }

    private fun build() {
        val w = tubeW.toInt()
        val h = tubeH.toInt()
        val bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bg)
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
        // Glaskolben
        q.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
            intArrayOf(Color.rgb(40, 34, 30), Color.rgb(14, 11, 10), Color.rgb(32, 27, 24)), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, w * 0.45f, w * 0.45f, q)
        q.shader = null
        // Anodengitter
        q.color = Color.argb(40, 160, 140, 110)
        q.strokeWidth = 1f
        val step = w / 14f
        var gx = step
        while (gx < w) { c.drawLine(gx, h * 0.12f, gx, h * 0.88f, q); gx += step }
        var gy = h * 0.12f
        while (gy < h * 0.88f) { c.drawLine(w * 0.08f, gy, w * 0.92f, gy, q); gy += step }
        // Unbeleuchtete Kathoden (alle Ziffern hintereinander)
        val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            textSize = h * 0.62f
            textAlign = Paint.Align.CENTER
        }
        val baseY = h * 0.72f
        digitPaint.style = Paint.Style.STROKE
        digitPaint.strokeWidth = 1f
        digitPaint.color = Color.argb(45, 150, 110, 80)
        for (d in 0..9) c.drawText("$d", w / 2f, baseY, digitPaint)
        // Glanzlicht
        q.color = Color.argb(35, 255, 255, 255)
        c.drawRoundRect(RectF(w * 0.12f, h * 0.05f, w * 0.3f, h * 0.7f), w * 0.1f, w * 0.1f, q)
        tubeBg = bg

        digitPaint.style = Paint.Style.FILL
        for (d in 0..9) {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val dc = Canvas(b)
            digitPaint.maskFilter = BlurMaskFilter(w * 0.08f, BlurMaskFilter.Blur.NORMAL)
            digitPaint.color = Color.argb(200, 255, 100, 20)
            digitPaint.style = Paint.Style.FILL_AND_STROKE
            digitPaint.strokeWidth = w * 0.04f
            dc.drawText("$d", w / 2f, baseY, digitPaint)
            digitPaint.maskFilter = null
            digitPaint.style = Paint.Style.FILL_AND_STROKE
            digitPaint.strokeWidth = w * 0.012f
            digitPaint.color = Color.rgb(255, 196, 120)
            dc.drawText("$d", w / 2f, baseY, digitPaint)
            digits[d] = b
        }
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        val bg = tubeBg ?: return
        c.drawColor(Color.rgb(12, 9, 8))
        cal.timeInMillis = System.currentTimeMillis()
        val hh = cal.get(Calendar.HOUR_OF_DAY)
        val mm = cal.get(Calendar.MINUTE)
        val ss = cal.get(Calendar.SECOND)
        val now = intArrayOfTime(hh, mm, ss)
        for (i in 0 until 6) {
            if (now[i] != cur[i]) {
                prev[i] = if (cur[i] < 0) now[i] else cur[i]
                cur[i] = now[i]
                fade[i] = 0f
            }
            fade[i] = min(1f, fade[i] + dt / 0.18f)
            c.drawBitmap(bg, tubeX[i], tubeY, null)
            if (fade[i] < 1f) {
                p.alpha = ((1f - fade[i]) * 255).toInt()
                c.drawBitmap(digits[prev[i]]!!, tubeX[i], tubeY, p)
            }
            p.alpha = (fade[i] * 255).toInt()
            c.drawBitmap(digits[cur[i]]!!, tubeX[i], tubeY, p)
        }

        // Glimmpunkte: im Takt, ohne erkannten Takt im Sekundenrhythmus
        val f = a.frame
        if (f.bpmConfidence > 0.15f && f.bpm > 0f) {
            if (f.beatCount != lastBeat) { lastBeat = f.beatCount; pulse = 1f }
            pulse = max(0.25f, pulse - dt * 3f)
        } else {
            pulse = if (cal.get(Calendar.MILLISECOND) < 500) 1f else 0.3f
        }
        for (x in colonX) for (dy in floatArrayOf(-0.18f, 0.18f)) {
            val cy = tubeY + tubeH * (0.5f + dy)
            val r = tubeW * 0.07f
            dotPaint.shader = RadialGradient(x, cy, r * 3f, Color.argb((pulse * 160).toInt(), 255, 120, 40), Color.argb(0, 255, 120, 40), Shader.TileMode.CLAMP)
            c.drawCircle(x, cy, r * 3f, dotPaint)
            dotPaint.shader = null
            dotPaint.color = Color.rgb(90 + (165 * pulse).toInt(), 50 + (120 * pulse).toInt(), 30)
            c.drawCircle(x, cy, r, dotPaint)
        }

        // IN-13-Balkenröhren
        val rms = ((f.rmsDb - a.peakRef + 40f) / 44f).coerceIn(0f, 1f)
        val pk = ((f.peakDb - a.peakRef + 40f) / 44f).coerceIn(0f, 1f)
        barLevel[0] = if (rms > barLevel[0]) barLevel[0] + (rms - barLevel[0]) * min(1f, dt * 25f) else max(rms, barLevel[0] - dt * 0.6f)
        barLevel[1] = if (pk > barLevel[1]) pk else max(pk, barLevel[1] - dt * 0.8f)
        for ((i, r) in bars.withIndex()) {
            p.alpha = 255
            p.color = Color.rgb(26, 22, 20)
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, p)
            val inset = r.height() * 0.3f
            val len = (r.width() - 2 * inset) * (0.03f + 0.97f * barLevel[i])
            neonGlow.color = Color.argb(80, 255, 80, 60)
            c.drawRect(r.left + inset, r.top, r.left + inset + len, r.bottom, neonGlow)
            neon.color = Color.rgb(255, 130, 90)
            c.drawRect(r.left + inset, r.top + inset, r.left + inset + len, r.bottom - inset, neon)
            c.drawText(if (i == 0) "VU" else "PEAK", r.left - label.textSize * 3.2f, r.bottom - inset * 0.4f, label)
        }
    }

    private val timeDigits = IntArray(6)
    private fun intArrayOfTime(h: Int, m: Int, s: Int): IntArray {
        timeDigits[0] = h / 10; timeDigits[1] = h % 10
        timeDigits[2] = m / 10; timeDigits[3] = m % 10
        timeDigits[4] = s / 10; timeDigits[5] = s % 10
        return timeDigits
    }
}
