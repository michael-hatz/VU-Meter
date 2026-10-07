package net.schmalenstroer.vumeter.milk

import net.schmalenstroer.vumeter.audio.AudioEngine
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Bass/Mitten/Höhen wie in MilkDrop: Energie relativ zum gleitenden Mittelwert
 * (≈1 im Schnitt, >1 bei lauten Stellen) – damit unabhängig von der Mikrofonlautstärke.
 */
class MilkAudio {
    var bass = 1f; var mid = 1f; var treb = 1f
    var bassAtt = 1f; var midAtt = 1f; var trebAtt = 1f
    var beat = false
        private set

    private val lo: IntArray
    private val hi: IntArray
    private val avg = FloatArray(3)
    private val imm = FloatArray(3) { 1f }
    private var initialized = false
    private var sinceBeat = 0f

    init {
        val binHz = AudioEngine.SAMPLE_RATE.toFloat() / AudioEngine.FFT_SIZE
        fun bin(f: Float) = (f / binHz).toInt()
        lo = intArrayOf(1, bin(250f) + 1, bin(2000f) + 1)
        hi = intArrayOf(bin(250f), bin(2000f), bin(12000f))
    }

    fun update(power: FloatArray, fresh: Boolean, dt: Float) {
        sinceBeat += dt
        beat = false
        if (fresh) {
            val rate = 1f - exp(-dt / 2f)
            for (b in 0..2) {
                var e = 0f
                for (k in lo[b]..hi[b]) e += power[k]
                e = sqrt(e)
                if (!initialized) avg[b] = e + 1e-6f
                avg[b] += (e - avg[b]) * rate
                imm[b] = (e / max(avg[b], 1e-5f)).coerceIn(0f, 3f)
            }
            initialized = true
        }
        bass = imm[0]; mid = imm[1]; treb = imm[2]
        val att = 1f - exp(-dt / 0.3f)
        bassAtt += (bass - bassAtt) * att
        midAtt += (mid - midAtt) * att
        trebAtt += (treb - trebAtt) * att
        if (bass > 1.35f && bass > bassAtt * 1.2f && sinceBeat > 0.3f) {
            beat = true
            sinceBeat = 0f
        }
    }
}
