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
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * "Magisches Auge" alter Röhrenradios: runde Anzeige (Typ EM34, zwei Schattenkeile
 * unterschiedlicher Empfindlichkeit) und darunter/daneben ein Leuchtband (Typ EM84).
 */
class MagicEye : Visualizer("Magisches Auge") {
    private var eyeBase: Bitmap? = null
    private var eyeCap: Bitmap? = null
    private var tube: Bitmap? = null
    private var ex = 0f
    private var ey = 0f
    private var er = 0f
    private val tubeRect = RectF()
    private val stripRect = RectF()
    private val oval = RectF()

    private var levelA = 0f
    private var levelB = 0f
    private var levelBar = 0f

    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240, 4, 14, 6) }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 190, 255, 190); strokeCap = Paint.Cap.ROUND }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barGlow = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onResize(w: Int, h: Int) {
        val landscape = w >= h
        if (landscape) {
            er = min(h * 0.33f, w * 0.2f)
            ex = w * 0.3f
            ey = h * 0.5f
            val tw = w * 0.36f
            val th = er * 0.5f
            tubeRect.set(w * 0.58f, ey - th / 2, w * 0.58f + tw, ey + th / 2)
        } else {
            er = min(w * 0.33f, h * 0.2f)
            ex = w * 0.5f
            ey = h * 0.33f
            val tw = w * 0.72f
            val th = er * 0.5f
            tubeRect.set((w - tw) / 2, h * 0.72f - th / 2, (w + tw) / 2, h * 0.72f + th / 2)
        }
        buildEye()
        buildTube()
    }

    private fun buildEye() {
        val size = (er * 2.7f).toInt()
        val c0 = size / 2f
        val base = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(base)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Glaskolben
        p.shader = RadialGradient(c0, c0, er * 1.33f, intArrayOf(Color.rgb(28, 32, 30), Color.rgb(10, 12, 11), Color.argb(0, 0, 0, 0)),
            floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(c0, c0, er * 1.33f, p)
        // Leuchtschirm
        p.shader = RadialGradient(c0, c0, er, intArrayOf(Color.rgb(190, 255, 185), Color.rgb(80, 230, 110), Color.rgb(25, 140, 60), Color.rgb(10, 70, 30)),
            floatArrayOf(0.3f, 0.5f, 0.85f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(c0, c0, er, p)
        p.shader = null
        // Feine radiale Struktur
        p.color = Color.argb(28, 0, 40, 10)
        p.strokeWidth = er * 0.008f
        for (i in 0 until 180) {
            val a = i * Math.PI * 2 / 180
            c.drawLine(c0 + (er * 0.33f * cos(a)).toFloat(), c0 + (er * 0.33f * sin(a)).toFloat(),
                c0 + (er * cos(a)).toFloat(), c0 + (er * sin(a)).toFloat(), p)
        }
        eyeBase = base

        val cap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cc = Canvas(cap)
        p.shader = RadialGradient(c0 - er * 0.06f, c0 - er * 0.06f, er * 0.34f,
            Color.rgb(48, 48, 46), Color.rgb(6, 6, 6), Shader.TileMode.CLAMP)
        cc.drawCircle(c0, c0, er * 0.32f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = er * 0.015f
        p.color = Color.argb(90, 160, 160, 150)
        cc.drawCircle(c0, c0, er * 0.32f, p)
        p.style = Paint.Style.FILL
        // Glasreflex
        p.shader = LinearGradient(0f, c0 - er, 0f, c0 - er * 0.4f, Color.argb(45, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        cc.drawOval(RectF(c0 - er * 0.55f, c0 - er * 0.95f, c0 + er * 0.35f, c0 - er * 0.45f), p)
        p.shader = null
        eyeCap = cap
    }

    private fun buildTube() {
        val w = tubeRect.width().toInt()
        val h = tubeRect.height().toInt()
        if (w <= 0 || h <= 0) return
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(40, 44, 42), Color.rgb(12, 14, 13), Color.rgb(30, 34, 32)), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, p)
        p.shader = null
        stripRect.set(h * 0.6f, h * 0.3f, w - h * 0.6f, h * 0.7f)
        p.color = Color.rgb(8, 40, 18)
        c.drawRect(stripRect, p)
        p.color = Color.argb(70, 255, 255, 255)
        c.drawRoundRect(RectF(h * 0.4f, h * 0.08f, w - h * 0.4f, h * 0.2f), h * 0.06f, h * 0.06f, p)
        p.color = Color.rgb(110, 120, 115)
        p.textSize = h * 0.16f
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        c.drawText("EM 84", w / 2f, h * 0.93f, p)
        tube = b
        stripRect.offset(tubeRect.left, tubeRect.top)
    }

    private fun smooth(cur: Float, target: Float, dt: Float) =
        if (target > cur) cur + (target - cur) * min(1f, dt * 20f) else cur + (target - cur) * min(1f, dt * 3f)

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        val base = eyeBase ?: return
        val cap = eyeCap ?: return
        c.drawColor(Color.rgb(6, 8, 7))
        val rms = a.frame.rmsDb - a.levelRef
        val pk = a.frame.peakDb - a.peakRef
        levelA = smooth(levelA, ((rms + 30f) / 30f).coerceIn(0f, 1.1f), dt)
        levelB = smooth(levelB, ((pk + 22f) / 25f).coerceIn(0f, 1.1f), dt)
        levelBar = smooth(levelBar, ((rms + 28f) / 30f).coerceIn(0f, 1f), dt)

        val half = base.width / 2f
        c.drawBitmap(base, ex - half, ey - half, null)
        // Zwei Schattenkeile: oben empfindlich (RMS), unten weniger empfindlich (Spitze)
        drawWedge(c, -90f, 46f * (1f - levelA.coerceAtMost(1f)).pow(1.2f) + 1.5f)
        drawWedge(c, 90f, 46f * (1f - levelB.coerceAtMost(1f)).pow(1.2f) + 1.5f)
        c.drawBitmap(cap, ex - half, ey - half, null)

        val t = tube ?: return
        c.drawBitmap(t, tubeRect.left, tubeRect.top, null)
        val len = stripRect.width() / 2f * levelBar
        val g = (60 + 80 * levelBar).toInt()
        bar.color = Color.rgb(g, 255, 110)
        barGlow.color = Color.argb(60, 90, 255, 140)
        val gh = stripRect.height() * 0.6f
        c.drawRect(stripRect.left, stripRect.top - gh, stripRect.left + len, stripRect.bottom + gh, barGlow)
        c.drawRect(stripRect.right - len, stripRect.top - gh, stripRect.right, stripRect.bottom + gh, barGlow)
        c.drawRect(stripRect.left, stripRect.top, stripRect.left + len, stripRect.bottom, bar)
        c.drawRect(stripRect.right - len, stripRect.top, stripRect.right, stripRect.bottom, bar)
    }

    private fun drawWedge(c: Canvas, centerDeg: Float, halfDeg: Float) {
        val r = er * 1.01f
        oval.set(ex - r, ey - r, ex + r, ey + r)
        c.drawArc(oval, centerDeg - halfDeg, halfDeg * 2f, true, shadow)
        // Hellere Kanten des Schattens
        edge.strokeWidth = er * 0.025f
        for (s in floatArrayOf(-1f, 1f)) {
            val ang = Math.toRadians((centerDeg + s * halfDeg).toDouble())
            val cs = cos(ang).toFloat()
            val sn = sin(ang).toFloat()
            c.drawLine(ex + cs * er * 0.34f, ey + sn * er * 0.34f, ex + cs * er * 0.98f, ey + sn * er * 0.98f, edge)
        }
    }
}
