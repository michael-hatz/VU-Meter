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
import kotlin.math.pow
import kotlin.math.sin

/**
 * Zwei analoge Zeigerinstrumente: links ein klassisches VU-Meter (RMS, 300 ms
 * Zeigerträgheit), rechts ein Spitzenpegelmesser (PPM). Skala und Abdeckung
 * werden einmal pro Größe in Bitmaps gerendert, pro Bild wird nur der Zeiger gezeichnet.
 */
class AnalogVu : Visualizer("Analoge VU-Meter") {

    private class Needle(val omega: Float, val zeta: Float) {
        var pos = 0f
        var vel = 0f

        fun step(target: Float, dt: Float) {
            var t = dt
            while (t > 0f) {
                val h = min(t, 0.004f)
                val acc = omega * omega * (target - pos) - 2f * zeta * omega * vel
                vel += acc * h
                pos += vel * h
                t -= h
            }
            if (pos < -0.06f) { pos = -0.06f; vel = max(vel, 0f) }
            if (pos > 1.08f) { pos = 1.08f; vel = min(vel, 0f) }
        }
    }

    private class Scale(
        val title: String,
        val subtitle: String,
        val pos: (Float) -> Float,
        val major: List<Pair<Float, String>>,
        val minor: List<Float>,
        val redFrom: Float,
    )

    private inner class Meter(val scale: Scale, val needle: Needle) {
        val rect = RectF()
        val face = RectF()
        var back: Bitmap? = null
        var front: Bitmap? = null
        var pivotX = 0f
        var pivotY = 0f
        var radius = 0f
        var lamp = 0f
        var lampX = 0f
        var lampY = 0f
        var lampR = 0f
    }

    private val vuScale = Scale(
        "VU", "RMS",
        pos = { db -> (10f.pow(db / 20f) - LIN_M20) / (LIN_P3 - LIN_M20) },
        major = listOf(-20f to "20", -10f to "10", -7f to "7", -5f to "5", -3f to "3", -2f to "2",
            -1f to "1", 0f to "0", 1f to "1", 2f to "2", 3f to "3"),
        minor = listOf(-15f, -6f, -4f, -0.5f, 0.5f, 1.5f, 2.5f),
        redFrom = 0f,
    )
    private val ppmScale = Scale(
        "dB", "PEAK",
        pos = { db -> (db + 40f) / 46f },
        major = listOf(-40f to "40", -30f to "30", -20f to "20", -10f to "10", -5f to "5",
            0f to "0", 3f to "+3", 6f to "+6"),
        minor = listOf(-35f, -25f, -15f, -8f, -2f, 1.5f, 4.5f),
        redFrom = 0f,
    )
    private val meters = arrayOf(Meter(vuScale, Needle(13f, 0.8f)), Meter(ppmScale, Needle(42f, 0.9f)))
    private var ppmEnv = -60f

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 22, 20); strokeCap = Paint.Cap.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 0, 0); strokeCap = Paint.Cap.ROUND
    }
    private val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onResize(w: Int, h: Int) {
        val landscape = w >= h
        val cellW = if (landscape) w / 2f else w.toFloat()
        val cellH = if (landscape) h.toFloat() else h / 2f
        for ((i, m) in meters.withIndex()) {
            val cx = if (landscape) cellW * i + cellW / 2 else cellW / 2
            val cy = if (landscape) cellH / 2 else cellH * i + cellH / 2
            var mw = cellW * 0.92f
            var mh = mw / 1.55f
            if (mh > cellH * 0.9f) { mh = cellH * 0.9f; mw = mh * 1.55f }
            m.rect.set(cx - mw / 2, cy - mh / 2, cx + mw / 2, cy + mh / 2)
            buildMeter(m)
        }
    }

    private fun angleOf(pos: Float) = Math.toRadians((-SWEEP + pos * 2 * SWEEP).toDouble())

    private fun buildMeter(m: Meter) {
        val mw = m.rect.width()
        val mh = m.rect.height()
        val bezel = mh * 0.06f
        m.face.set(bezel, bezel, mw - bezel, mh - bezel)
        val f = m.face
        val fh = f.height()
        m.pivotX = f.centerX()
        m.pivotY = f.bottom + fh * 0.25f
        m.radius = fh * 0.95f
        m.lampR = fh * 0.035f
        m.lampX = f.right - fh * 0.12f
        m.lampY = f.top + fh * 0.12f

        val back = Bitmap.createBitmap(mw.toInt(), mh.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(back)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Gehäuse
        p.shader = LinearGradient(0f, 0f, 0f, mh, Color.rgb(58, 52, 46), Color.rgb(20, 18, 16), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, mw, mh), mh * 0.06f, mh * 0.06f, p)
        // Hinterleuchtete Skala
        p.shader = RadialGradient(f.centerX(), f.top + fh * 0.45f, f.width() * 0.75f,
            Color.rgb(255, 238, 190), Color.rgb(222, 186, 110), Shader.TileMode.CLAMP)
        c.drawRect(f, p)
        p.shader = null

        val s = m.scale
        val cx = m.pivotX
        val cy = m.pivotY
        val r = m.radius
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        val redPos = s.pos(s.redFrom)
        val startDeg = -SWEEP - 90f

        // Skalenbogen: schwarz bis 0, rot danach (dicker)
        p.style = Paint.Style.STROKE
        p.strokeWidth = fh * 0.008f
        p.color = Color.rgb(30, 25, 20)
        c.drawArc(oval, startDeg, redPos * 2 * SWEEP, false, p)
        p.color = Color.rgb(200, 30, 25)
        p.strokeWidth = fh * 0.035f
        val rr = r + fh * 0.0175f
        c.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), startDeg + redPos * 2 * SWEEP,
            (1f - redPos) * 2 * SWEEP, false, p)

        // Teilstriche
        p.strokeWidth = fh * 0.008f
        fun tick(db: Float, len: Float) {
            val a = angleOf(s.pos(db))
            p.color = if (db >= s.redFrom) Color.rgb(200, 30, 25) else Color.rgb(30, 25, 20)
            c.drawLine(cx + r * sin(a).toFloat(), cy - r * cos(a).toFloat(),
                cx + (r + len) * sin(a).toFloat(), cy - (r + len) * cos(a).toFloat(), p)
        }
        for (db in s.minor) tick(db, fh * 0.035f)
        for ((db, _) in s.major) tick(db, fh * 0.065f)

        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        p.textSize = fh * 0.075f
        val lr = r + fh * 0.12f
        for ((db, label) in s.major) {
            val a = angleOf(s.pos(db))
            p.color = if (db >= s.redFrom) Color.rgb(190, 25, 20) else Color.rgb(30, 25, 20)
            val tx = cx + lr * sin(a).toFloat()
            val ty = cy - lr * cos(a).toFloat() + p.textSize * 0.35f
            c.drawText(label, tx, ty, p)
        }
        // Minus/Plus-Hinweise
        p.textSize = fh * 0.09f
        p.color = Color.rgb(30, 25, 20)
        c.drawText("–", f.left + f.width() * 0.07f, f.top + fh * 0.62f, p)
        p.color = Color.rgb(190, 25, 20)
        c.drawText("+", f.right - f.width() * 0.07f, f.top + fh * 0.62f, p)

        // Instrumentenbezeichnung
        p.color = Color.rgb(40, 32, 25)
        p.textSize = fh * 0.16f
        c.drawText(s.title, cx, f.top + fh * 0.72f, p)
        p.textSize = fh * 0.05f
        p.typeface = Typeface.DEFAULT_BOLD
        p.color = Color.rgb(120, 40, 30)
        c.drawText("PEAK", m.lampX, m.lampY + m.lampR + fh * 0.07f, p)
        m.back = back

        // Vorderseite: Abdeckung über dem Zeigerlager + Glasreflex
        val front = Bitmap.createBitmap(mw.toInt(), mh.toInt(), Bitmap.Config.ARGB_8888)
        val fc = Canvas(front)
        val coverTop = f.top + fh * 0.80f
        p.shader = LinearGradient(0f, coverTop, 0f, f.bottom, Color.rgb(48, 44, 40), Color.rgb(16, 15, 14), Shader.TileMode.CLAMP)
        fc.drawRect(f.left, coverTop, f.right, f.bottom, p)
        p.shader = null
        p.color = Color.argb(90, 255, 255, 255)
        fc.drawRect(f.left, coverTop, f.right, coverTop + max(1f, fh * 0.006f), p)
        p.color = Color.rgb(170, 160, 145)
        p.textSize = fh * 0.055f
        p.typeface = Typeface.DEFAULT_BOLD
        fc.drawText(s.subtitle, cx, coverTop + (f.bottom - coverTop) * 0.65f, p)
        p.shader = LinearGradient(0f, f.top, 0f, f.top + fh * 0.5f,
            Color.argb(55, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        fc.drawRect(f.left, f.top, f.right, f.top + fh * 0.5f, p)
        p.shader = null
        m.front = front
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        c.drawColor(Color.rgb(18, 15, 12))

        val vuDb = a.frame.rmsDb - a.levelRef
        val vu = meters[0]
        vu.needle.step(vuScale.pos(vuDb.coerceAtMost(6f)), dt)
        vu.lamp = if (vuDb > 2f) 0.4f else vu.lamp - dt

        // PPM: sofortiger Anstieg, Rücklauf ~12 dB/s (20 dB in 1,7 s)
        ppmEnv = max(a.frame.peakDb - a.peakRef, ppmEnv - 11.8f * dt).coerceAtLeast(-50f)
        val ppm = meters[1]
        ppm.needle.step(ppmScale.pos(ppmEnv.coerceAtMost(8f)), dt)
        ppm.lamp = if (ppmEnv > 3f) 0.4f else ppm.lamp - dt

        for (m in meters) {
            val back = m.back ?: continue
            val front = m.front ?: continue
            val ox = m.rect.left
            val oy = m.rect.top
            c.drawBitmap(back, ox, oy, null)

            val fh = m.face.height()
            val ang = angleOf(m.needle.pos)
            val sx = sin(ang).toFloat()
            val cy = cos(ang).toFloat()
            val px = ox + m.pivotX
            val py = oy + m.pivotY
            val r0 = m.radius * 0.30f
            val r1 = m.radius * 1.06f
            c.save()
            c.clipRect(ox + m.face.left, oy + m.face.top, ox + m.face.right, oy + m.face.bottom)
            val sh = fh * 0.02f
            shadowPaint.strokeWidth = fh * 0.016f
            c.drawLine(px + r0 * sx + sh, py - r0 * cy + sh, px + r1 * sx + sh, py - r1 * cy + sh, shadowPaint)
            needlePaint.strokeWidth = fh * 0.011f
            c.drawLine(px + r0 * sx, py - r0 * cy, px + r1 * sx, py - r1 * cy, needlePaint)
            c.restore()

            lampPaint.color = if (m.lamp > 0f) Color.rgb(255, 50, 30) else Color.rgb(90, 25, 20)
            c.drawCircle(ox + m.lampX, oy + m.lampY, m.lampR, lampPaint)
            if (m.lamp > 0f) {
                lampPaint.color = Color.argb(70, 255, 60, 30)
                c.drawCircle(ox + m.lampX, oy + m.lampY, m.lampR * 2f, lampPaint)
            }
            c.drawBitmap(front, ox, oy, null)
        }
    }

    companion object {
        private const val SWEEP = 41f
        private val LIN_M20 = 10f.pow(-20f / 20f)
        private val LIN_P3 = 10f.pow(3f / 20f)
    }
}
