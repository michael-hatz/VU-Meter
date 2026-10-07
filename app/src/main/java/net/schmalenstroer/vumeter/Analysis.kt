package net.schmalenstroer.vumeter

import net.schmalenstroer.vumeter.audio.AudioEngine
import net.schmalenstroer.vumeter.audio.AudioFrame
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

fun dbToLin(db: Float): Float = 10f.pow(db / 20f)

/**
 * Fasst FFT-Bins zu logarithmisch verteilten Frequenzbändern zusammen.
 * Tiefe Bänder ohne eigenen Bin werden interpoliert.
 */
class BandMapper(val count: Int, private val fMin: Float = 40f, private val fMax: Float = 16000f) {
    private val lo = IntArray(count)
    private val hi = IntArray(count)
    private val pos = FloatArray(count)
    private val oct = FloatArray(count)
    val centerHz = FloatArray(count)

    init {
        val binHz = AudioEngine.SAMPLE_RATE.toFloat() / AudioEngine.FFT_SIZE
        val maxBin = AudioEngine.FFT_SIZE / 2 - 1
        val ratio = fMax / fMin
        for (b in 0 until count) {
            val f0 = fMin * ratio.pow(b.toFloat() / count)
            val f1 = fMin * ratio.pow((b + 1f) / count)
            val fc = sqrt(f0 * f1)
            centerHz[b] = fc
            oct[b] = log2(fc / 1000f)
            val l = ceil(f0 / binHz).toInt()
            val h = min(maxBin, floor(f1 / binHz).toInt())
            if (h >= l) {
                lo[b] = l; hi[b] = h
            } else {
                lo[b] = -1; pos[b] = fc / binHz
            }
        }
    }

    /** Bandposition (0..count) einer Frequenz, z. B. für Achsenbeschriftungen. */
    fun positionOf(hz: Float): Float = (ln(hz / fMin) / ln(fMax / fMin)) * count

    fun mapDb(power: FloatArray, out: FloatArray, tiltDbPerOct: Float) {
        val last = power.size - 1
        for (b in 0 until count) {
            val l = lo[b]
            val p = if (l >= 0) {
                var m = 0f
                for (k in l..hi[b]) if (power[k] > m) m = power[k]
                m
            } else {
                val x = pos[b]
                val k = x.toInt()
                val f = x - k
                power[k] * (1f - f) + power[min(k + 1, last)] * f
            }
            out[b] = 10f * log10(p + 1e-14f) + tiltDbPerOct * oct[b]
        }
    }
}

/**
 * Render-seitige Auswertung: automatische bzw. manuelle Empfindlichkeit und
 * Normierung der Spektren auf 0..1.
 */
class Analysis(private val s: Settings) {
    val frame = AudioFrame()

    /** Spektrum: dB-Wert, der dem oberen Rand entspricht (vor Headroom). */
    var specTop = -40f
        private set
    /** Pegel: RMS in dBFS, der 0 VU entspricht. */
    var levelRef = -30f
        private set
    /** Wellenform: Amplitude, die den Bildschirm füllt. */
    var waveRef = 0.05f
        private set
    var time = 0f
        private set

    /** Bezugspegel für Spitzenwerte (0 dB ≈ Spitzen eines 0-VU-Signals). */
    val peakRef get() = levelRef + CREST_DB

    private val gainMapper = BandMapper(24)
    private val gainBands = FloatArray(24)

    fun update(dt: Float) {
        time += dt
        if (s.autoGain) {
            gainMapper.mapDb(frame.power, gainBands, s.tilt.toFloat())
            var mx = -200f
            for (v in gainBands) if (v > mx) mx = v
            specTop = if (mx > specTop) specTop + (mx - specTop) * min(1f, dt * 6f) else specTop - SPEC_RELEASE * dt
            specTop = specTop.coerceIn(SPEC_TOP_MIN, 0f)

            val r = frame.rmsDb
            levelRef = if (r > levelRef) levelRef + (r - levelRef) * min(1f, dt * 1.2f) else levelRef - LEVEL_RELEASE * dt
            levelRef = levelRef.coerceIn(LEVEL_MIN, -3f)

            val p = frame.peak
            waveRef = if (p > waveRef) waveRef + (p - waveRef) * min(1f, dt * 8f) else waveRef * exp(-0.4f * dt)
            waveRef = waveRef.coerceIn(0.004f, 1f)
        } else {
            val k = s.sensitivity / 100f
            specTop = -10f - k * 55f
            levelRef = -6f - k * 48f
            waveRef = dbToLin(-3f - k * 45f)
        }
    }

    fun norm(db: Float): Float {
        val range = s.range.toFloat()
        val bottom = specTop - HEADROOM - range
        return ((db - bottom) / range).coerceIn(0f, 1f)
    }

    /** Füllt [out] mit normierten Bandwerten 0..1. */
    fun bands(m: BandMapper, out: FloatArray) {
        m.mapDb(frame.power, out, s.tilt.toFloat())
        for (i in out.indices) out[i] = norm(out[i])
    }

    companion object {
        const val CREST_DB = 10f
        const val HEADROOM = 3f
        const val SPEC_RELEASE = 2.5f
        const val LEVEL_RELEASE = 0.8f
        const val SPEC_TOP_MIN = -55f
        const val LEVEL_MIN = -58f
    }
}
