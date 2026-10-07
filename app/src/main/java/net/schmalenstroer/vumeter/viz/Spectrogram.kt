package net.schmalenstroer.vumeter.viz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.BandMapper

/**
 * Laufendes Spektrogramm (Wasserfall). Ringpuffer-Bitmap: pro Bild wird nur eine
 * Spalte geschrieben, beim Zeichnen in zwei Teilen ausgegeben – kein Umkopieren.
 */
class Spectrogram : Visualizer("Spektrogramm") {
    private val mapper = BandMapper(ROWS)
    private val vals = FloatArray(ROWS)
    private val col = IntArray(ROWS)
    private val palette = buildPalette()
    private val bmp = Bitmap.createBitmap(COLS, ROWS, Bitmap.Config.ARGB_8888).apply { eraseColor(palette[0]) }
    private var writeX = 0
    private val src = Rect()
    private val dst = Rect()
    private val paint = Paint().apply { isFilterBitmap = true }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 230, 230, 230)
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }
    private val labelY = FloatArray(LABELS.size)

    override fun onResize(w: Int, h: Int) {
        label.textSize = h * 0.032f
        for ((i, l) in LABELS.withIndex()) {
            labelY[i] = h - mapper.positionOf(l.first) / ROWS * h
        }
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        if (a.frame.fresh) {
            a.bands(mapper, vals)
            for (r in 0 until ROWS) col[ROWS - 1 - r] = palette[(vals[r] * 255f).toInt()]
            bmp.setPixels(col, 0, 1, writeX, 0, 1, ROWS)
            writeX = (writeX + 1) % COLS
        }
        val w = width
        val h = height
        // Älterer Teil [writeX, COLS) links, neuerer Teil [0, writeX) rechts
        val split = ((COLS - writeX).toLong() * w / COLS).toInt()
        src.set(writeX, 0, COLS, ROWS)
        dst.set(0, 0, split, h)
        c.drawBitmap(bmp, src, dst, paint)
        if (writeX > 0) {
            src.set(0, 0, writeX, ROWS)
            dst.set(split, 0, w, h)
            c.drawBitmap(bmp, src, dst, paint)
        }
        val x = w * 0.012f
        for ((i, l) in LABELS.withIndex()) c.drawText(l.second, x, labelY[i] + label.textSize * 0.35f, label)
    }

    companion object {
        private const val ROWS = 160
        private const val COLS = 480
        private val LABELS = listOf(
            100f to "100 Hz", 250f to "250", 500f to "500", 1000f to "1 kHz",
            2000f to "2k", 4000f to "4k", 8000f to "8k",
        )

        /** "Inferno"-ähnlicher Farbverlauf. */
        private fun buildPalette(): IntArray {
            val stops = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.75f, 0.9f, 1f)
            val cols = intArrayOf(
                Color.rgb(0, 0, 4), Color.rgb(27, 12, 65), Color.rgb(106, 23, 110), Color.rgb(187, 55, 84),
                Color.rgb(241, 113, 31), Color.rgb(252, 194, 41), Color.rgb(252, 255, 164),
            )
            return IntArray(256) { i ->
                val t = i / 255f
                var k = 0
                while (k < stops.size - 2 && t > stops[k + 1]) k++
                val f = ((t - stops[k]) / (stops[k + 1] - stops[k])).coerceIn(0f, 1f)
                val a = cols[k]
                val b = cols[k + 1]
                Color.rgb(
                    (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
                    (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
                    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
                )
            }
        }
    }
}
