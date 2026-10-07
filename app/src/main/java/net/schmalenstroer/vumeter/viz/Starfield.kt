package net.schmalenstroer.vumeter.viz

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.BandMapper
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Sternenflug: der Bass bestimmt die Geschwindigkeit, die Höhen färben die Sterne ein. */
class Starfield : Visualizer("Sternenflug") {
    private val rnd = Random(11)
    private val x = FloatArray(STARS) { rnd.nextFloat() * 2f - 1f }
    private val y = FloatArray(STARS) { rnd.nextFloat() * 2f - 1f }
    private val z = FloatArray(STARS) { 0.05f + rnd.nextFloat() * 0.95f }
    private val mapper = BandMapper(8, 40f, 12000f)
    private val vals = FloatArray(8)
    private var speed = 0.3f
    private var tint = 0f
    private var time = 0f
    private val buckets = Array(4) { FloatArray(STARS * 4) }
    private val counts = IntArray(4)
    private val paints = Array(4) { Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND } }

    override fun onResize(w: Int, h: Int) {
        val base = min(w, h) / 500f
        for (i in 0 until 4) paints[i].strokeWidth = max(1f, base * (1f + i * 0.8f))
    }

    override fun draw(c: Canvas, a: Analysis, dt: Float) {
        c.drawColor(Color.BLACK)
        a.bands(mapper, vals)
        time += dt
        val bass = (vals[0] + vals[1]) / 2f
        val treble = (vals[5] + vals[6] + vals[7]) / 3f
        val target = 0.15f + 1.6f * bass * bass
        speed += (target - speed) * min(1f, dt * (if (target > speed) 10f else 2f))
        tint += (treble - tint) * min(1f, dt * 4f)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2 + sin(time * 0.13f) * w * 0.05f
        val cy = h / 2 + sin(time * 0.17f) * h * 0.05f
        val fov = min(w, h) * 0.5f
        counts.fill(0)
        for (i in 0 until STARS) {
            val pz = z[i]
            z[i] -= speed * dt * 0.6f
            if (z[i] <= 0.03f) {
                x[i] = rnd.nextFloat() * 2f - 1f
                y[i] = rnd.nextFloat() * 2f - 1f
                z[i] = 1f
                continue
            }
            val sx = cx + x[i] / z[i] * fov
            val sy = cy + y[i] / z[i] * fov
            if (sx < 0 || sx > w || sy < 0 || sy > h) { z[i] = 0f; continue }
            val px = cx + x[i] / pz * fov
            val py = cy + y[i] / pz * fov
            val b = ((1f - z[i]) * 4f).toInt().coerceIn(0, 3)
            val arr = buckets[b]
            var k = counts[b]
            arr[k++] = px; arr[k++] = py; arr[k++] = sx; arr[k++] = sy
            counts[b] = k
        }
        val r = (255 - 120 * tint).toInt()
        for (b in 0 until 4) {
            if (counts[b] == 0) continue
            paints[b].color = Color.argb(70 + b * 60, r, 255 - (40 * tint).toInt(), 255)
            c.drawLines(buckets[b], 0, counts[b], paints[b])
        }
    }

    companion object {
        private const val STARS = 500
    }
}
