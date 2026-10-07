package net.schmalenstroer.vumeter.viz

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.Settings
import net.schmalenstroer.vumeter.audio.AudioEngine
import java.util.Locale
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Schallpegelmesser mit A-Bewertung und Zeitbewertung "Fast" (125 ms).
 * Richtwert: Das Mikrofon ist nicht geeicht, die Kalibrierung ist einstellbar.
 */
class SoundLevel(private val s: Settings) : Visualizer("Schallpegel dB(A)") {
    private val aWeight = FloatArray(AudioEngine.FFT_SIZE / 2)
    private var msFast = 1e-9f
    private val history = FloatArray(HIST)
    private var histPos = 0
    private var histAcc = 0f
    private var histT = 0f
    private val bucketsMs = FloatArray(60)
    private val bucketsMax = FloatArray(60) { -1f }
    private var bucketPos = 0
    private var bucketT = 0f
    private var bucketSum = 0f
    private var bucketN = 0
    private var shownValue = Float.NaN
    private var shownText = ""
    private var statsText = ""
    private var statsT = 0f

    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.RIGHT }
    private val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(170, 175, 180); typeface = Typeface.DEFAULT_BOLD }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(150, 155, 160) }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val graph = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val grid = Paint().apply { color = Color.rgb(40, 44, 48) }
    private val limit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 80, 60); strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f) }
    private val barRect = RectF()
    private val graphRect = RectF()
    private val lines = FloatArray(HIST * 4)

    init {
        val binHz = AudioEngine.SAMPLE_RATE.toFloat() / AudioEngine.FFT_SIZE
        for (k in 1 until aWeight.size) {
            val f = k * binHz
            val f2 = f * f
            val ra = (12194f * 12194f * f2 * f2) /
                ((f2 + 20.6f * 20.6f) * sqrt((f2 + 107.7f * 107.7f) * (f2 + 737.9f * 737.9f)) * (f2 + 12194f * 12194f))
            val db = 20f * log10(ra) + 2f
            aWeight[k] = 10f.pow(db / 10f)
        }
    }

    override fun onResize(w: Int, h: Int) {
        big.textSize = h * 0.24f
        unit.textSize = h * 0.06f
        small.textSize = h * 0.035f
        barRect.set(w * 0.06f, h * 0.40f, w * 0.94f, h * 0.46f)
        graphRect.set(w * 0.06f, h * 0.58f, w * 0.94f, h * 0.90f)
        graph.strokeWidth = max(2f, h * 0.004f)
    }

    private fun toDb(ms: Float) = 10f * log10(ms + 1e-12f) + s.splOffset

    private fun yOf(db: Float) = graphRect.bottom - ((db - MIN) / (MAX - MIN)).coerceIn(0f, 1f) * graphRect.height()

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        c.drawColor(Color.rgb(14, 16, 18))
        // A-bewertete mittlere Leistung (Hann: Summe der Bins = 3 × Effektivwert²)
        val p = a.frame.power
        var sum = 0f
        for (k in 1 until p.size) sum += p[k] * aWeight[k]
        val ms = sum / 3f
        msFast += (ms - msFast) * (1f - exp(-dt / 0.125f))
        val db = toDb(msFast)

        // Verlauf (Wert alle 0,25 s)
        histAcc = max(histAcc, db)
        histT += dt
        if (histT >= 0.25f) {
            histT = 0f
            history[histPos] = histAcc
            histPos = (histPos + 1) % HIST
            histAcc = 0f
        }
        // Statistik der letzten 60 s (Sekunden-Buckets)
        bucketSum += msFast
        bucketN++
        bucketsMax[bucketPos] = max(bucketsMax[bucketPos], db)
        bucketT += dt
        if (bucketT >= 1f) {
            bucketsMs[bucketPos] = bucketSum / bucketN
            bucketPos = (bucketPos + 1) % 60
            bucketsMax[bucketPos] = -1f
            bucketSum = 0f; bucketN = 0; bucketT = 0f
        }
        statsT -= dt
        if (statsT <= 0f) {
            statsT = 0.5f
            var mx = 0f
            var e = 0f
            var n = 0
            for (i in 0 until 60) {
                if (bucketsMax[i] > mx) mx = bucketsMax[i]
                if (bucketsMs[i] > 0f) { e += bucketsMs[i]; n++ }
            }
            val leq = if (n > 0) toDb(e / n) else db
            statsText = String.format(Locale.GERMANY, "Max 60 s: %.1f dB    Leq 60 s: %.1f dB", mx, leq)
        }

        val color = when {
            db < 70f -> Color.rgb(80, 220, 120)
            db < 85f -> Color.rgb(240, 200, 60)
            else -> Color.rgb(255, 90, 60)
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val rounded = (db * 2f).toInt() / 2f
        if (rounded != shownValue) {
            shownValue = rounded
            shownText = String.format(Locale.GERMANY, "%.1f", db.coerceAtLeast(0f))
        }
        big.color = color
        c.drawText(shownText, w * 0.62f, h * 0.30f, big)
        c.drawText("dB(A)", w * 0.64f, h * 0.30f, unit)
        c.drawText(statsText, barRect.left, h * 0.10f, small)

        // Balken 30–120 dB
        barPaint.color = Color.rgb(36, 40, 44)
        c.drawRect(barRect, barPaint)
        val frac = ((db - MIN) / (MAX - MIN)).coerceIn(0f, 1f)
        barPaint.color = color
        c.drawRect(barRect.left, barRect.top, barRect.left + barRect.width() * frac, barRect.bottom, barPaint)
        small.textAlign = Paint.Align.CENTER
        var t = MIN
        while (t <= MAX) {
            val x = barRect.left + (t - MIN) / (MAX - MIN) * barRect.width()
            c.drawRect(x - 1, barRect.bottom, x + 1, barRect.bottom + h * 0.015f, small)
            c.drawText("${t.toInt()}", x, barRect.bottom + h * 0.05f, small)
            t += 10f
        }
        small.textAlign = Paint.Align.LEFT

        // Verlaufsgraph
        var g = MIN
        while (g <= MAX) { val y = yOf(g); c.drawLine(graphRect.left, y, graphRect.right, y, grid); g += 10f }
        val y85 = yOf(85f)
        c.drawLine(graphRect.left, y85, graphRect.right, y85, limit)
        var k = 0
        val step = graphRect.width() / (HIST - 1)
        for (i in 0 until HIST - 1) {
            val i0 = (histPos + i) % HIST
            val i1 = (histPos + i + 1) % HIST
            if (history[i0] <= 0f || history[i1] <= 0f) continue
            lines[k++] = graphRect.left + i * step; lines[k++] = yOf(history[i0])
            lines[k++] = graphRect.left + (i + 1) * step; lines[k++] = yOf(history[i1])
        }
        graph.color = Color.rgb(110, 190, 255)
        if (k > 0) c.drawLines(lines, 0, k, graph)
        c.drawText("letzte 2 Minuten", graphRect.left, graphRect.top - h * 0.015f, small)
        c.drawText("Richtwert – Mikrofon nicht geeicht (Kalibrierung in den Einstellungen)", graphRect.left, h * 0.96f, small)
    }

    companion object {
        private const val HIST = 480
        private const val MIN = 30f
        private const val MAX = 120f
    }
}
