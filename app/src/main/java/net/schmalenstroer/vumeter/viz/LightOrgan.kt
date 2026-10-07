package net.schmalenstroer.vumeter.viz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.audio.AudioEngine
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Lichtorgel wie in den 70ern: vier farbige Lampen für Bass, Mitten und Höhen mit
 * eigener automatischer Empfindlichkeit pro Kanal. Bewusst ohne Stroboskop-Effekt.
 */
class LightOrgan : Visualizer("Lichtorgel") {
    private val lo = IntArray(CH)
    private val hi = IntArray(CH)
    private val ref = FloatArray(CH) { -50f }
    private val level = FloatArray(CH)
    private val lampOff = arrayOfNulls<Bitmap>(CH)
    private val lampOn = arrayOfNulls<Bitmap>(CH)
    private val cx = FloatArray(CH)
    private var cy = 0f
    private var lampR = 0f
    private val glowShader = arrayOfNulls<RadialGradient>(CH)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(235, 230, 220); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }

    init {
        val binHz = AudioEngine.SAMPLE_RATE.toFloat() / AudioEngine.FFT_SIZE
        for (i in 0 until CH) {
            lo[i] = max(1, (EDGES[i] / binHz).toInt())
            hi[i] = (EDGES[i + 1] / binHz).toInt()
        }
    }

    override fun onResize(w: Int, h: Int) {
        lampR = min(w / (CH * 2.4f), h * 0.26f)
        cy = h * 0.48f
        val gap = w / CH.toFloat()
        for (i in 0 until CH) {
            cx[i] = gap * (i + 0.5f)
            glowShader[i] = RadialGradient(cx[i], cy, h * 0.95f,
                Color.argb(150, Color.red(COLORS[i]), Color.green(COLORS[i]), Color.blue(COLORS[i])),
                Color.argb(0, 0, 0, 0), Shader.TileMode.CLAMP)
            lampOff[i] = lamp(COLORS[i], false)
            lampOn[i] = lamp(COLORS[i], true)
        }
        label.textSize = h * 0.04f
    }

    private fun lamp(color: Int, lit: Boolean): Bitmap {
        val s = (lampR * 2.6f).toInt()
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val m = s / 2f
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        if (lit) {
            q.shader = RadialGradient(m, m, lampR * 1.3f,
                intArrayOf(Color.WHITE, color, Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(m, m, lampR * 1.3f, q)
        } else {
            // Reflektor und farbiges Glas
            q.shader = RadialGradient(m, m, lampR, Color.rgb(60, 60, 58), Color.rgb(18, 18, 18), Shader.TileMode.CLAMP)
            c.drawCircle(m, m, lampR * 1.08f, q)
            q.shader = RadialGradient(m - lampR * 0.2f, m - lampR * 0.2f, lampR,
                Color.argb(255, Color.red(color) / 3, Color.green(color) / 3, Color.blue(color) / 3),
                Color.argb(255, Color.red(color) / 8, Color.green(color) / 8, Color.blue(color) / 8), Shader.TileMode.CLAMP)
            c.drawCircle(m, m, lampR, q)
            q.shader = null
            q.style = Paint.Style.STROKE
            q.strokeWidth = lampR * 0.02f
            q.color = Color.argb(50, 255, 255, 255)
            var r = lampR * 0.25f
            while (r < lampR) { c.drawCircle(m, m, r, q); r += lampR * 0.18f }
        }
        return b
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        c.drawColor(Color.rgb(8, 7, 7))
        val pw = a.frame.power
        for (i in 0 until CH) {
            var e = 0f
            for (k in lo[i]..hi[i]) e += pw[k]
            val db = 10f * log10(e + 1e-12f)
            ref[i] = if (db > ref[i]) db else max(-70f, ref[i] - 3f * dt)
            val v = ((db - (ref[i] - 15f)) / 15f).coerceIn(0f, 1f).pow(1.6f)
            level[i] = if (v > level[i]) v else max(v, level[i] - 5f * dt)
        }
        for (i in 0 until CH) {
            glow.shader = glowShader[i]
            glow.alpha = (level[i] * 255).toInt()
            c.drawCircle(cx[i], cy, height * 0.95f, glow)
        }
        for (i in 0 until CH) {
            val off = lampOff[i] ?: continue
            val on = lampOn[i] ?: continue
            val o = off.width / 2f
            c.drawBitmap(off, cx[i] - o, cy - o, null)
            p.alpha = (level[i] * 255).toInt()
            c.drawBitmap(on, cx[i] - o, cy - o, p)
            c.drawText(NAMES[i], cx[i], cy + lampR * 1.5f + label.textSize, label)
        }
    }

    companion object {
        private const val CH = 4
        private val EDGES = floatArrayOf(30f, 180f, 900f, 3500f, 14000f)
        private val COLORS = intArrayOf(Color.rgb(255, 40, 30), Color.rgb(255, 190, 20), Color.rgb(40, 230, 70), Color.rgb(40, 110, 255))
        private val NAMES = arrayOf("BASS", "MITTEN TIEF", "MITTEN HOCH", "HÖHEN")
    }
}
