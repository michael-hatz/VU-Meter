package net.schmalenstroer.vumeter.viz

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.BandMapper
import net.schmalenstroer.vumeter.Settings
import net.schmalenstroer.vumeter.audio.AudioEngine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Farben aus der Standard-viscolor.txt von Winamp 2.x (Base Skin). */
object WinampColors {
    val background = Color.rgb(0, 0, 0)
    val dots = Color.rgb(24, 33, 41)
    /** Index 0 = oben (rot) … 15 = unten (grün). */
    val spectrum = intArrayOf(
        Color.rgb(239, 49, 16), Color.rgb(206, 41, 16), Color.rgb(214, 90, 0), Color.rgb(214, 102, 0),
        Color.rgb(214, 115, 0), Color.rgb(198, 123, 8), Color.rgb(222, 165, 24), Color.rgb(214, 181, 33),
        Color.rgb(189, 222, 41), Color.rgb(148, 222, 33), Color.rgb(41, 206, 16), Color.rgb(50, 190, 16),
        Color.rgb(57, 181, 16), Color.rgb(49, 156, 8), Color.rgb(41, 148, 0), Color.rgb(24, 132, 8),
    )
    val scope = intArrayOf(
        Color.rgb(255, 255, 255), Color.rgb(214, 214, 222), Color.rgb(181, 189, 189),
        Color.rgb(160, 170, 175), Color.rgb(148, 156, 165),
    )
    val peak = Color.rgb(150, 150, 150)
}

/** Gemeinsames Raster: 76 Pixel breit wie im Original-Hauptfenster. */
abstract class WinampBase(name: String, protected val s: Settings) : Visualizer(name) {
    protected val surf = PixelSurface()
    protected val dst = Rect()
    protected var gw = GRID_W
    protected var gh = 16
    protected var bg = IntArray(0)
    private var lastOriginal: Boolean? = null
    private var lastW = 0
    private var lastH = 0

    /** Prüft, ob sich Größe oder Modus geändert hat, und baut das Raster neu. */
    protected fun layout(): Boolean {
        val orig = s.waOriginalSize
        if (orig == lastOriginal && width == lastW && height == lastH) return false
        lastOriginal = orig; lastW = width; lastH = height
        if (orig) {
            gh = 16
            val ps = min(width / GRID_W.toFloat(), height / 16f)
            val dw = (GRID_W * ps).toInt()
            val dh = (16 * ps).toInt()
            dst.set((width - dw) / 2, (height - dh) / 2, (width - dw) / 2 + dw, (height - dh) / 2 + dh)
        } else {
            gh = (GRID_W * height / width.toFloat()).roundToInt().coerceIn(16, 256)
            dst.set(0, 0, width, height)
        }
        surf.ensure(gw, gh)
        bg = IntArray(gw * gh) { i ->
            val x = i % gw
            val y = i / gw
            if (x % 2 == 1 && y % 2 == 1) WinampColors.dots else WinampColors.background
        }
        return true
    }

    companion object {
        const val GRID_W = 76
    }
}

class WinampSpectrum(s: Settings) : WinampBase("Winamp Classic – Spektrum", s) {
    private var thin: Boolean? = null
    private var mapper = BandMapper(19)
    private var vals = FloatArray(19)
    private var bar = FloatArray(19)
    private var peak = FloatArray(19)
    private var peakVel = FloatArray(19)
    private var rowColor = IntArray(0)

    private fun setup() {
        if (layout()) {
            rowColor = IntArray(gh) { WinampColors.spectrum[min(15, it * 16 / gh)] }
        }
        if (thin != s.waThin) {
            thin = s.waThin
            val n = if (s.waThin) 75 else 19
            mapper = BandMapper(n)
            vals = FloatArray(n); bar = FloatArray(n); peak = FloatArray(n); peakVel = FloatArray(n)
        }
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        setup()
        a.bands(mapper, vals)
        val hF = gh.toFloat()
        val fall = FALL[s.waFalloff.coerceIn(0, 4)] * hF * dt
        val pAcc = PEAK_ACC[s.waPeakFalloff.coerceIn(0, 4)] * hF
        for (i in vals.indices) {
            val t = vals[i] * hF
            bar[i] = if (t >= bar[i]) t else max(t, bar[i] - fall)
            if (bar[i] >= peak[i]) {
                peak[i] = bar[i]; peakVel[i] = 0f
            } else {
                peakVel[i] += pAcc * dt
                peak[i] = max(bar[i], peak[i] - peakVel[i] * dt)
            }
        }

        val px = surf.px
        System.arraycopy(bg, 0, px, 0, bg.size)
        val bw = if (thin == true) 1 else 3
        val stride = if (thin == true) 1 else 4
        val style = s.waColor
        val peaks = s.waPeaks
        for (i in vals.indices) {
            val h = (bar[i] + 0.5f).toInt().coerceIn(0, gh)
            val top = gh - h
            val x0 = i * stride
            for (y in top until gh) {
                val col = when (style) {
                    1 -> rowColor[y - top]   // Feuer: Verlauf hängt an der Balkenspitze
                    2 -> rowColor[top]       // Linie: ganzer Balken in der Farbe seiner Höhe
                    else -> rowColor[y]      // Normal: feste Farbe pro Zeile
                }
                val o = y * gw + x0
                for (x in 0 until bw) px[o + x] = col
            }
            if (peaks) {
                val ph = (peak[i] + 0.5f).toInt()
                if (ph > 0) {
                    val o = (gh - ph).coerceIn(0, gh - 1) * gw + x0
                    for (x in 0 until bw) px[o + x] = WinampColors.peak
                }
            }
        }
        surf.present(c, dst)
    }

    companion object {
        /** Balken-Abfall in Bildhöhen pro Sekunde. */
        private val FALL = floatArrayOf(0.35f, 0.7f, 1.2f, 2.0f, 3.2f)
        /** Beschleunigung der fallenden Spitzen in Bildhöhen/s². */
        private val PEAK_ACC = floatArrayOf(0.5f, 1.0f, 2.0f, 3.5f, 6.0f)
    }
}

class WinampScope(s: Settings) : WinampBase("Winamp Classic – Oszilloskop", s) {
    private var rowColor = IntArray(0)

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        if (layout()) {
            val half = gh / 2f
            rowColor = IntArray(gh) { y ->
                val d = abs(y + 0.5f - half) / half
                WinampColors.scope[min(4, (d * 5).toInt())]
            }
        }
        val px = surf.px
        System.arraycopy(bg, 0, px, 0, bg.size)
        val wave = a.frame.wave
        val mid = gh / 2f
        val scale = mid / a.waveRef
        val cols = gw - 1
        val step = AudioEngine.WAVE_SIZE / cols
        val style = s.waScope
        var prevY = -1
        val midI = gh / 2
        for (x in 0 until cols) {
            val v = wave[x * step]
            val y = (mid - v * scale).toInt().coerceIn(0, gh - 1)
            when (style) {
                0 -> px[y * gw + x] = rowColor[y]
                1 -> {
                    val y0 = if (prevY < 0) y else prevY
                    val from = min(y0, y)
                    val to = max(y0, y)
                    for (yy in from..to) px[yy * gw + x] = rowColor[yy]
                }
                else -> {
                    val from = min(midI, y)
                    val to = max(midI, y)
                    for (yy in from..to) px[yy * gw + x] = rowColor[yy]
                }
            }
            prevY = y
        }
        surf.present(c, dst)
    }
}
