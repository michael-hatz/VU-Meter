package net.schmalenstroer.vumeter.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Einfacher "Musik"-Generator: Bassdrum, Hi-Hat, Basslinie und Akkord, in Echtzeit getaktet. */
internal class DemoSignal(private val rate: Int) {
    private var n = 0L
    private val rnd = Random(1)
    private var startNs = 0L

    fun fill(out: ShortArray): Int {
        if (startNs == 0L) startNs = System.nanoTime()
        val beat = rate / 2
        val bass = floatArrayOf(55f, 55f, 65.4f, 49f)
        val chords = arrayOf(floatArrayOf(220f, 277f, 330f), floatArrayOf(196f, 247f, 294f))
        for (i in out.indices) {
            val t = n.toDouble() / rate
            val inBeat = (n % beat).toDouble() / rate
            val bar = (n / (beat * 4)).toInt()
            val kick = exp(-inBeat * 18) * sin(2 * PI * (50 + 90 * exp(-inBeat * 30)) * inBeat)
            val off = ((n + beat / 2) % beat).toDouble() / rate
            val hat = exp(-off * 60) * (rnd.nextFloat() * 2 - 1) * 0.25
            val b = sin(2 * PI * bass[bar % 4] * t) * 0.3
            var ch = 0.0
            for (f in chords[bar % 2]) ch += sin(2 * PI * f * t) * 0.06 * (1 + 0.5 * sin(2 * PI * 0.25 * t))
            val swell = 0.55 + 0.45 * sin(2 * PI * t / 8)
            val v = (kick * 0.6 + hat + b + ch) * swell * 0.5
            out[i] = (v.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
            n++
        }
        // Echtzeit-Takt einhalten
        val dueNs = startNs + n * 1_000_000_000L / rate
        val waitMs = (dueNs - System.nanoTime()) / 1_000_000L
        if (waitMs > 0) Thread.sleep(waitMs)
        return out.size
    }
}
