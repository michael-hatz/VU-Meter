package net.schmalenstroer.vumeter.viz

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import net.schmalenstroer.vumeter.Analysis
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Tempo-Anzeige: BPM, Tempobezeichnung, Taktschlag-Punkte und Tempoverlauf. */
class BpmView : Visualizer("BPM-Anzeige") {
    private var lastBeat = 0L
    private var beatIndex = 0
    private var flash = 0f
    private var shownBpm = -1
    private var bpmText = "–––"
    private var tempoText = ""
    private val history = FloatArray(HIST)
    private var histPos = 0
    private var histT = 0f
    private val lines = FloatArray(HIST * 4)
    private val graphRect = RectF()

    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val mid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(180, 185, 195); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(130, 135, 145) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val graph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 150, 70) }
    private val grid = Paint().apply { color = Color.rgb(40, 40, 48) }

    override fun onResize(w: Int, h: Int) {
        big.textSize = h * 0.26f
        mid.textSize = h * 0.05f
        small.textSize = h * 0.032f
        graphRect.set(w * 0.08f, h * 0.76f, w * 0.92f, h * 0.94f)
        graph.strokeWidth = max(2f, h * 0.004f)
    }

    private fun tempoName(bpm: Int) = when {
        bpm < 66 -> "Largo"
        bpm < 76 -> "Adagio"
        bpm < 108 -> "Andante"
        bpm < 120 -> "Moderato"
        bpm < 156 -> "Allegro"
        bpm < 176 -> "Vivace"
        else -> "Presto"
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        c.drawColor(Color.rgb(12, 12, 16))
        val f = a.frame
        val known = f.bpm > 0f && f.bpmConfidence > 0.12f
        if (f.beatCount != lastBeat) {
            lastBeat = f.beatCount
            beatIndex = (beatIndex + 1) % 4
            flash = 1f
        }
        flash = max(0f, flash - dt * 4f)

        val bpm = if (known) f.bpm.roundToInt() else -1
        if (bpm != shownBpm) {
            shownBpm = bpm
            bpmText = if (bpm > 0) "$bpm" else "–––"
            tempoText = if (bpm > 0) tempoName(bpm) else "Tempo wird erkannt …"
        }
        histT += dt
        if (histT >= 0.5f) {
            histT = 0f
            history[histPos] = if (known) f.bpm else 0f
            histPos = (histPos + 1) % HIST
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val cy = h * 0.36f
        // Pulsring
        val r = h * (0.25f + 0.03f * flash)
        ring.strokeWidth = h * (0.01f + 0.015f * flash)
        ring.color = Color.argb((60 + 195 * flash).toInt(), 255, 150, 70)
        c.drawCircle(cx, cy, r, ring)
        big.color = Color.rgb(255, (200 - 50 * flash).toInt(), (150 - 80 * flash).toInt())
        c.drawText(bpmText, cx, cy + big.textSize * 0.36f, big)
        c.drawText("BPM", cx, cy + r * 0.62f, mid)
        c.drawText(tempoText, cx, h * 0.70f, mid)

        // Vier Taktpunkte
        val dr = h * 0.022f
        for (i in 0 until 4) {
            val x = cx + (i - 1.5f) * dr * 4f
            val y = cy - r - dr * 2.5f
            dot.color = if (i == beatIndex && known) Color.argb((120 + 135 * max(flash, 0.3f)).toInt(), 255, 150, 70) else Color.rgb(50, 50, 60)
            c.drawCircle(x, y, dr, dot)
        }
        // Sicherheit
        val conf = min(1f, f.bpmConfidence / 0.5f)
        val bw = w * 0.2f
        dot.color = Color.rgb(40, 40, 50)
        c.drawRect(w * 0.08f, h * 0.06f, w * 0.08f + bw, h * 0.075f, dot)
        dot.color = Color.rgb(120, 200, 140)
        c.drawRect(w * 0.08f, h * 0.06f, w * 0.08f + bw * conf, h * 0.075f, dot)
        c.drawText("Erkennungssicherheit", w * 0.08f, h * 0.05f, small)

        // Verlauf 60–200 BPM, letzte 3 Minuten
        for (g in intArrayOf(80, 120, 160)) {
            val y = graphRect.bottom - (g - 60f) / 140f * graphRect.height()
            c.drawLine(graphRect.left, y, graphRect.right, y, grid)
            c.drawText("$g", graphRect.right + h * 0.01f, y + small.textSize * 0.35f, small)
        }
        var k = 0
        val step = graphRect.width() / (HIST - 1)
        for (i in 0 until HIST - 1) {
            val v0 = history[(histPos + i) % HIST]
            val v1 = history[(histPos + i + 1) % HIST]
            if (v0 <= 0f || v1 <= 0f) continue
            lines[k++] = graphRect.left + i * step
            lines[k++] = graphRect.bottom - ((v0 - 60f) / 140f).coerceIn(0f, 1f) * graphRect.height()
            lines[k++] = graphRect.left + (i + 1) * step
            lines[k++] = graphRect.bottom - ((v1 - 60f) / 140f).coerceIn(0f, 1f) * graphRect.height()
        }
        if (k > 0) c.drawLines(lines, 0, k, graph)
        c.drawText("Tempoverlauf (3 Minuten)", graphRect.left, graphRect.top - h * 0.015f, small)
    }

    companion object {
        private const val HIST = 360
    }
}
