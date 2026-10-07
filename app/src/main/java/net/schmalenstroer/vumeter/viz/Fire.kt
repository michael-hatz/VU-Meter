package net.schmalenstroer.vumeter.viz

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.BandMapper
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Klassischer Demoszene-Feuereffekt in niedriger Auflösung. Das Spektrum facht die
 * Glut am unteren Rand an (Bass in der Mitte), Bassschläge lassen das Feuer auflodern.
 */
class Fire : Visualizer("Demoszene-Feuer") {
    private val surf = PixelSurface(smooth = true)
    private val dst = Rect()
    private val mapper = BandMapper(BANDS, 40f, 12000f)
    private val vals = FloatArray(BANDS)
    private val smooth = FloatArray(BANDS)
    private var heat = IntArray(0)
    private var gw = 0
    private var gh = 0
    private var acc = 0f
    private var bassAvg = 0.3f
    private var burst = 0f
    private val rnd = Random(3)
    private val cool = IntArray(1024) { rnd.nextInt(3) }
    private var coolOff = 0
    private val palette = IntArray(256) { i ->
        val t = i / 255f
        val r = (min(1f, t * 3f) * 255).toInt()
        val g = (((t - 0.33f) * 2.2f).coerceIn(0f, 1f) * 255).toInt()
        val b = (((t - 0.75f) * 4f).coerceIn(0f, 1f) * 255).toInt()
        Color.rgb(r, g, b)
    }

    override fun onResize(w: Int, h: Int) {
        gw = 160
        gh = (gw * h / w.toFloat()).roundToInt().coerceIn(60, 260)
        heat = IntArray(gw * (gh + 2))
        surf.ensure(gw, gh)
        dst.set(0, 0, w, h)
    }

    private fun step() {
        val w = gw
        val h = gh + 2
        // Glut: zwei unsichtbare Zeilen unter dem Bild
        val center = w / 2f
        for (x in 0 until w) {
            val d = (abs(x - center) / center).pow(0.7f)
            val b = (d * (BANDS - 1)).toInt()
            val v = smooth[b]
            val e = (v * v * 1.3f + burst * 0.5f).coerceAtMost(1f)
            // Einzelne Flammenzungen statt gleichmäßiger Glut (sonst steigen "Flächen" auf)
            val r = rnd.nextFloat()
            val hv = ((e * (if (r < 0.45f) 0.75f + 0.25f * r else 0.15f * r)) * 255f).toInt()
            heat[(h - 1) * w + x] = hv
            heat[(h - 2) * w + x] = hv
        }
        // Ausbreitung nach oben mit Abkühlung
        coolOff = (coolOff + 7) and 1023
        for (y in 0 until h - 2) {
            val r1 = (y + 1) * w
            val r2 = (y + 2) * w
            val row = y * w
            for (x in 0 until w) {
                val xl = if (x == 0) w - 1 else x - 1
                val xr = if (x == w - 1) 0 else x + 1
                val sum = heat[r1 + xl] + heat[r1 + x] + heat[r1 + xr] + heat[r2 + x]
                val v = (sum shr 2) - cool[(x + y * 13 + coolOff) and 1023]
                heat[row + x] = if (v < 0) 0 else v
            }
        }
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        if (gw == 0) return
        a.bands(mapper, vals)
        for (i in 0 until BANDS) smooth[i] = if (vals[i] > smooth[i]) vals[i] else smooth[i] - (smooth[i] - vals[i]) * min(1f, dt * 6f)
        val bass = (smooth[0] + smooth[1] + smooth[2]) / 3f
        bassAvg += (bass - bassAvg) * min(1f, dt * 1.5f)
        if (bass > bassAvg * 1.25f && bass > 0.4f) burst = 1f
        burst = (burst - dt * 3f).coerceAtLeast(0f)

        acc += dt
        var n = 0
        while (acc >= STEP && n < 3) { step(); acc -= STEP; n++ }
        if (acc > STEP) acc = 0f

        val px = surf.px
        for (i in 0 until gw * gh) px[i] = palette[heat[i].coerceAtMost(255)]
        surf.present(c, dst)
    }

    companion object {
        private const val BANDS = 24
        private const val STEP = 1f / 60f
    }
}
