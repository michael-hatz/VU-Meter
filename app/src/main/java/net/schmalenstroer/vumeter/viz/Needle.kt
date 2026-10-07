package net.schmalenstroer.vumeter.viz

import kotlin.math.max
import kotlin.math.min

/** Zeiger als gedämpftes Feder-Masse-System (Position 0..1 auf der Skala). */
class Needle(private val omega: Float, private val zeta: Float) {
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
