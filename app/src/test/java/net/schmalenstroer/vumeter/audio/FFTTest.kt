package net.schmalenstroer.vumeter.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

class FFTTest {
    private val n = AudioEngine.FFT_SIZE

    private fun spectrum(signal: (Int) -> Float): FloatArray {
        val re = FloatArray(n) { signal(it) * (0.5f - 0.5f * cos(2.0 * PI * it / (n - 1)).toFloat()) }
        val im = FloatArray(n)
        FFT(n).transform(re, im)
        val norm = (4f / n) * (4f / n)
        return FloatArray(n / 2) { (re[it] * re[it] + im[it] * im[it]) * norm }
    }

    @Test
    fun fullScaleSineIsZeroDb() {
        val bin = 93
        val p = spectrum { sin(2.0 * PI * bin * it / n).toFloat() }
        val peak = p.indices.maxByOrNull { p[it] }!!
        assertEquals(bin, peak)
        assertEquals(0.0, 10 * log10(p[peak].toDouble()), 0.1)
    }

    @Test
    fun halfAmplitudeIsMinusSixDb() {
        val p = spectrum { 0.5f * sin(2.0 * PI * 200 * it / n).toFloat() }
        assertEquals(-6.02, 10 * log10(p[200].toDouble()), 0.1)
    }

    @Test
    fun silenceIsEmpty() {
        assertTrue(spectrum { 0f }.all { it == 0f })
    }
}
