package net.schmalenstroer.vumeter.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BeatTrackerTest {
    private val hopRate = AudioEngine.SAMPLE_RATE.toFloat() / AudioEngine.HOP

    /** Simuliert Spektren mit einem breitbandigen Schlag pro Beat und leisem Rauschen dazwischen. */
    private fun run(bpm: Float, seconds: Float, jitter: Boolean = false): BeatTracker {
        val t = BeatTracker(hopRate)
        val rnd = Random(1)
        val power = FloatArray(AudioEngine.FFT_SIZE / 2)
        val period = hopRate * 60f / bpm
        var next = 0f
        val hops = (seconds * hopRate).toInt()
        for (h in 0 until hops) {
            val beat = h >= next
            if (beat) next += period + if (jitter) (rnd.nextFloat() - 0.5f) * 2f else 0f
            for (k in power.indices) {
                val noise = 1e-7f * rnd.nextFloat()
                power[k] = if (beat) 1e-3f + noise else noise
            }
            t.process(power)
        }
        return t
    }

    @Test
    fun detects120() {
        val t = run(120f, 20f)
        assertEquals(120f, t.bpm, 2f)
        assertTrue(t.confidence > 0.2f)
    }

    @Test
    fun detects95WithJitter() {
        assertEquals(95f, run(95f, 25f, jitter = true).bpm, 2.5f)
    }

    @Test
    fun detects140() {
        assertEquals(140f, run(140f, 20f).bpm, 2.5f)
    }

    @Test
    fun countsBeats() {
        // 20 s bei 120 BPM ≈ 40 Schläge
        val n = run(120f, 20f).beatCount
        assertTrue("beats=$n", n in 34..44)
    }
}
