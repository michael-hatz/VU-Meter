package net.schmalenstroer.vumeter.viz

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.audio.AudioEngine
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Notenanzeige: Energie der 12 Tonklassen (Chroma), erkannter Dur-/Moll-Akkord
 * und eine leuchtende Klaviatur. Deutsche Notennamen (B = B♭, H = B).
 */
class ChromaView : Visualizer("Noten & Akkorde") {
    private val binA: IntArray
    private val binB: IntArray
    private val wA: FloatArray
    private val wB: FloatArray
    private val kLo: Int
    private val kHi: Int
    private val raw = FloatArray(12)
    private val chroma = FloatArray(12)
    private val contrast = FloatArray(12)
    private var chordIdx = -1
    private var candIdx = -1
    private var candT = 0f
    private var chordText = "–"
    private val bars = Array(12) { RectF() }
    private val whiteKeys = Array(7) { RectF() }
    private val blackKeys = Array(5) { RectF() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER; color = Color.WHITE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.rgb(170, 175, 185); typeface = Typeface.DEFAULT_BOLD }
    private val colors = IntArray(12)

    init {
        val binHz = AudioEngine.SAMPLE_RATE.toFloat() / AudioEngine.FFT_SIZE
        kLo = (110f / binHz).toInt()
        kHi = (4200f / binHz).toInt()
        val n = kHi - kLo + 1
        binA = IntArray(n); binB = IntArray(n); wA = FloatArray(n); wB = FloatArray(n)
        for (i in 0 until n) {
            val f = (kLo + i) * binHz
            val midi = 69f + 12f * (ln(f / 440f) / ln(2f))
            val pc = ((midi % 12f) + 12f) % 12f
            val a = floor(pc).toInt() % 12
            val frac = pc - floor(pc)
            binA[i] = a; binB[i] = (a + 1) % 12
            wA[i] = 1f - frac; wB[i] = frac
        }
        // Farben nach Quintenzirkel, damit verwandte Töne ähnlich aussehen
        for (pcIdx in 0 until 12) {
            val pos = (pcIdx * 7) % 12
            colors[pcIdx] = Color.HSVToColor(floatArrayOf(pos * 30f, 0.75f, 1f))
        }
    }

    override fun onResize(w: Int, h: Int) {
        val left = w * 0.08f
        val right = w * 0.92f
        val bw = (right - left) / 12f
        for (i in 0 until 12) bars[i].set(left + i * bw + bw * 0.12f, h * 0.24f, left + (i + 1) * bw - bw * 0.12f, h * 0.60f)
        val kTop = h * 0.70f
        val kBot = h * 0.95f
        val kw = (right - left) / 7f
        for (i in 0 until 7) whiteKeys[i].set(left + i * kw + 2, kTop, left + (i + 1) * kw - 2, kBot)
        val blackPos = floatArrayOf(1f, 2f, 4f, 5f, 6f)
        for (i in 0 until 5) {
            val x = left + blackPos[i] * kw
            blackKeys[i].set(x - kw * 0.3f, kTop, x + kw * 0.3f, kTop + (kBot - kTop) * 0.6f)
        }
        big.textSize = h * 0.12f
        label.textSize = h * 0.035f
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        c.drawColor(Color.rgb(14, 12, 18))
        val pw = a.frame.power
        raw.fill(0f)
        for (i in binA.indices) {
            val m = sqrt(pw[kLo + i])
            raw[binA[i]] += m * wA[i]
            raw[binB[i]] += m * wB[i]
        }
        var mx = 1e-9f
        for (v in raw) if (v > mx) mx = v
        val loud = a.frame.rmsDb > a.levelRef - 25f
        val k = 1f - exp(-dt / 0.25f)
        for (i in 0 until 12) chroma[i] += ((if (loud) raw[i] / mx else 0f) - chroma[i]) * k

        detectChord(dt, loud)

        c.drawText(chordText, width / 2f, height * 0.15f, big)
        for (i in 0 until 12) {
            val r = bars[i]
            p.color = Color.rgb(34, 32, 42)
            c.drawRect(r, p)
            p.color = colors[i]
            c.drawRect(r.left, r.bottom - r.height() * chroma[i], r.right, r.bottom, p)
            c.drawText(NAMES[i], r.centerX(), r.bottom + label.textSize * 1.3f, label)
        }
        // Klaviatur
        val white = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        val black = intArrayOf(1, 3, 6, 8, 10)
        for (i in 0 until 7) keyDraw(c, whiteKeys[i], white[i], Color.rgb(225, 222, 215))
        for (i in 0 until 5) keyDraw(c, blackKeys[i], black[i], Color.rgb(20, 20, 24))
    }

    private fun keyDraw(c: Canvas, r: RectF, pc: Int, base: Int) {
        p.color = base
        c.drawRoundRect(r, 6f, 6f, p)
        val v = chroma[pc]
        if (v > 0.2f) {
            p.color = colors[pc]
            p.alpha = ((v - 0.2f) / 0.8f * 230).toInt().coerceIn(0, 230)
            c.drawRoundRect(r, 6f, 6f, p)
            p.alpha = 255
        }
    }

    private fun detectChord(dt: Float, loud: Boolean) {
        // Grundrauschen (z. B. Hi-Hats) abziehen, damit nur hervortretende Töne zählen
        var mean = 0f
        for (v in chroma) mean += v
        mean /= 12f
        for (i in 0 until 12) contrast[i] = max(0f, chroma[i] - mean)
        var best = -1
        var bestScore = 0f
        var norm = 0f
        for (v in contrast) norm += v * v
        norm = sqrt(norm) + 1e-9f
        for (root in 0 until 12) for (minor in 0..1) {
            val third = (root + if (minor == 1) 3 else 4) % 12
            val fifth = (root + 7) % 12
            val score = (contrast[root] + contrast[third] + contrast[fifth]) / (norm * sqrt(3f))
            if (score > bestScore) { bestScore = score; best = root * 2 + minor }
        }
        val idx = if (loud && bestScore > 0.72f) best else -1
        if (idx != candIdx) { candIdx = idx; candT = 0f } else candT += dt
        if (candT > 0.4f && candIdx != chordIdx) {
            chordIdx = candIdx
            chordText = if (chordIdx < 0) "–" else {
                val root = chordIdx / 2
                if (chordIdx % 2 == 0) "${MAJOR[root]}-Dur" else "${MINOR[root]}-Moll"
            }
        }
    }

    companion object {
        private val NAMES = arrayOf("C", "Cis", "D", "Dis", "E", "F", "Fis", "G", "Gis", "A", "B", "H")
        private val MAJOR = arrayOf("C", "Des", "D", "Es", "E", "F", "Fis", "G", "As", "A", "B", "H")
        private val MINOR = arrayOf("c", "cis", "d", "es", "e", "f", "fis", "g", "gis", "a", "b", "h")
    }
}
