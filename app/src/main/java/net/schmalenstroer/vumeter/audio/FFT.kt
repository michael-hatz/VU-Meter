package net.schmalenstroer.vumeter.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Iterative radix-2 complex FFT mit vorberechneten Tabellen (keine Allokationen pro Aufruf). */
class FFT(private val n: Int) {
    private val cosT = FloatArray(n / 2)
    private val sinT = FloatArray(n / 2)
    private val rev = IntArray(n)

    init {
        require(n > 1 && n and (n - 1) == 0) { "n muss eine Zweierpotenz sein" }
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) rev[i] = Integer.reverse(i) ushr (32 - bits)
        for (i in 0 until n / 2) {
            val a = -2.0 * PI * i / n
            cosT[i] = cos(a).toFloat()
            sinT[i] = sin(a).toFloat()
        }
    }

    fun transform(re: FloatArray, im: FloatArray) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val l = j + half
                    val wr = cosT[k]
                    val wi = sinT[k]
                    val tr = wr * re[l] - wi * im[l]
                    val ti = wr * im[l] + wi * re[l]
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                i += size
            }
            size *= 2
        }
    }
}
