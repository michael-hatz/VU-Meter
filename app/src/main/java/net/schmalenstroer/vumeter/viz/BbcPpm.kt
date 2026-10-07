package net.schmalenstroer.vumeter.viz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import net.schmalenstroer.vumeter.Analysis
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Britischer Spitzenpegelmesser (PPM) mit schwarzem Zifferblatt und Skala 1–7:
 * 4 dB zwischen den Marken 2–7, 6 dB zwischen 1 und 2. Anstieg ~10 ms, Rücklauf 24 dB in 2,8 s.
 */
class BbcPpm : Visualizer("PPM (BBC-Skala)") {
    private val rect = RectF()
    private var face: Bitmap? = null
    private var pivotX = 0f
    private var pivotY = 0f
    private var radius = 0f
    private val needle = Needle(60f, 0.9f)
    private var env = -40f
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 250, 245); strokeCap = Paint.Cap.ROUND }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50, 255, 255, 255); strokeCap = Paint.Cap.ROUND }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** dB der Skalenmarken 1..7 */
    private fun markDb(n: Int) = if (n == 1) -14f else (n - 4) * 4f
    private fun pos(db: Float) = (db + 14f) / 26f
    private fun angle(p: Float) = Math.toRadians((-SWEEP + p * 2 * SWEEP).toDouble())

    override fun onResize(w: Int, h: Int) {
        var mw = w * 0.86f
        var mh = mw / 1.3f
        if (mh > h * 0.88f) { mh = h * 0.88f; mw = mh * 1.3f }
        rect.set((w - mw) / 2, (h - mh) / 2, (w + mw) / 2, (h + mh) / 2)
        pivotX = rect.centerX()
        pivotY = rect.top + mh * 0.86f
        radius = mh * 0.62f

        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(28, 26, 24))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Gehäuse und Zifferblatt
        p.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, Color.rgb(70, 68, 64), Color.rgb(30, 29, 27), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, mh * 0.05f, mh * 0.05f, p)
        val inner = RectF(rect.left + mh * 0.05f, rect.top + mh * 0.05f, rect.right - mh * 0.05f, rect.bottom - mh * 0.05f)
        p.shader = RadialGradient(pivotX, pivotY, radius * 1.6f, Color.rgb(22, 22, 24), Color.rgb(6, 6, 7), Shader.TileMode.CLAMP)
        c.drawRoundRect(inner, mh * 0.03f, mh * 0.03f, p)
        p.shader = null

        val white = Color.rgb(245, 245, 240)
        p.color = white
        p.style = Paint.Style.STROKE
        p.strokeWidth = mh * 0.006f
        val arc = RectF(pivotX - radius, pivotY - radius, pivotX + radius, pivotY + radius)
        c.drawArc(arc, -90f - SWEEP, 2 * SWEEP, false, p)
        p.strokeWidth = mh * 0.012f
        for (n in 1..7) {
            val a = angle(pos(markDb(n)))
            val s = sin(a).toFloat()
            val co = cos(a).toFloat()
            c.drawLine(pivotX + s * radius, pivotY - co * radius, pivotX + s * (radius + mh * 0.07f), pivotY - co * (radius + mh * 0.07f), p)
        }
        // Zwischenmarken (2 dB)
        p.strokeWidth = mh * 0.005f
        var d = -6f
        while (d < 12f) {
            val a = angle(pos(d))
            val s = sin(a).toFloat()
            val co = cos(a).toFloat()
            c.drawLine(pivotX + s * radius, pivotY - co * radius, pivotX + s * (radius + mh * 0.035f), pivotY - co * (radius + mh * 0.035f), p)
            d += 4f
        }
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = mh * 0.11f
        val lr = radius + mh * 0.15f
        for (n in 1..7) {
            val a = angle(pos(markDb(n)))
            c.drawText("$n", pivotX + sin(a).toFloat() * lr, pivotY - cos(a).toFloat() * lr + p.textSize * 0.36f, p)
        }
        p.textSize = mh * 0.06f
        p.color = Color.rgb(170, 170, 165)
        c.drawText("PPM", pivotX, pivotY - radius * 0.42f, p)
        face = b
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        val f = face ?: return
        c.drawBitmap(f, 0f, 0f, null)
        // 0 dB relativ zur Spitzenreferenz = Marke 6 (+8 dB)
        val db = a.frame.peakDb - a.peakRef + 8f
        env = max(db, env - 24f / 2.8f * dt).coerceAtLeast(-30f)
        needle.step(pos(min(env, 16f)), dt)
        val ang = angle(needle.pos)
        val s = sin(ang).toFloat()
        val co = cos(ang).toFloat()
        val r1 = radius * 1.08f
        val mh = rect.height()
        glowPaint.strokeWidth = mh * 0.03f
        c.drawLine(pivotX, pivotY, pivotX + s * r1, pivotY - co * r1, glowPaint)
        needlePaint.strokeWidth = mh * 0.011f
        c.drawLine(pivotX, pivotY, pivotX + s * r1, pivotY - co * r1, needlePaint)
        capPaint.shader = null
        capPaint.color = Color.rgb(15, 15, 15)
        c.drawCircle(pivotX, pivotY, mh * 0.07f, capPaint)
        capPaint.color = Color.rgb(60, 60, 58)
        c.drawCircle(pivotX, pivotY, mh * 0.03f, capPaint)
    }

    companion object {
        private const val SWEEP = 50f
    }
}
