package net.schmalenstroer.vumeter.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Tempo- und Beat-Erkennung, läuft im Audio-Thread einmal pro Analyse-Schritt.
 *
 * Onset-Hüllkurve aus dem spektralen Fluss (Zunahme der log. Magnituden), Tempo per
 * Autokorrelation über ~6 s mit Vorliebe für Tempi um 120 BPM. Beats werden an Onsets
 * ausgerichtet und bei kurzen Lücken im erkannten Tempo weitergezählt ("Schwungrad").
 */
internal class BeatTracker(private val hopRate: Float) {
    var bpm = 0f
        private set
    var confidence = 0f
        private set
    var beatCount = 0L
        private set

    private val env = FloatArray(N)
    private var pos = 0
    private var filled = 0
    private val prevLog = FloatArray(MAX_BIN)
    private val fluxHist = FloatArray(16)
    private var fluxPos = 0
    private val tmp = FloatArray(N)
    private var hopsSinceEstimate = 0
    private var candidate = 0f
    private var candidateHits = 0
    private var sinceTick = 0f

    fun process(power: FloatArray) {
        var flux = 0f
        for (k in 1 until MAX_BIN) {
            val lm = ln(1f + 1000f * sqrt(power[k]))
            val d = lm - prevLog[k]
            if (d > 0f) flux += d
            prevLog[k] = lm
        }
        // Gleitenden Mittelwert abziehen → nur deutliche Anstiege bleiben
        fluxHist[fluxPos] = flux
        fluxPos = (fluxPos + 1) % fluxHist.size
        var mean = 0f
        for (v in fluxHist) mean += v
        mean /= fluxHist.size
        env[pos] = max(0f, flux - mean)
        pos = (pos + 1) % N
        filled++

        sinceTick += 1f
        if (filled > 4) detectBeat()

        if (++hopsSinceEstimate >= ESTIMATE_EVERY && filled >= N) {
            hopsSinceEstimate = 0
            estimate()
        }
    }

    private fun at(back: Int) = env[(pos - 1 - back + N * 2) % N]

    private fun detectBeat() {
        // Spitzenwert einen Schritt zurück (braucht den Nachfolger zum Vergleich)
        val c = at(1)
        val isPeak = c > at(2) && c >= at(0)
        var onset = false
        if (isPeak) {
            val window = minOf(filled, ONSET_WINDOW)
            var m = 0f
            var sq = 0f
            for (i in 0 until window) { val v = at(i); m += v; sq += v * v }
            m /= window
            val std = sqrt(max(0f, sq / window - m * m))
            onset = c > m + 1.5f * std && c > 0.05f
        }
        val period = if (bpm > 0f && confidence > 0.15f) 60f * hopRate / bpm else 0f
        if (period == 0f) {
            if (onset && sinceTick > 0.3f * hopRate) tick(true)
        } else {
            if (onset && sinceTick > 0.8f * period) tick(true)
            else if (sinceTick >= period * 1.08f) {
                beatCount++
                sinceTick -= period
            }
        }
    }

    private fun tick(resync: Boolean) {
        beatCount++
        if (resync) sinceTick = 0f
    }

    private fun estimate() {
        var mean = 0f
        for (i in 0 until N) { val v = env[(pos + i) % N]; tmp[i] = v; mean += v }
        mean /= N
        var r0 = 0f
        for (i in 0 until N) { tmp[i] -= mean; r0 += tmp[i] * tmp[i] }
        if (r0 < 1e-6f) {
            confidence *= 0.5f
            return
        }
        r0 /= N
        val lagMin = (hopRate * 60f / MAX_BPM).toInt()
        val lagMax = (hopRate * 60f / MIN_BPM).toInt() + 1
        val r = FloatArray(lagMax + 2)
        for (lag in lagMin - 1..lagMax + 1) {
            var s = 0f
            for (i in 0 until N - lag) s += tmp[i] * tmp[i + lag]
            r[lag] = s / (N - lag)
        }
        var best = -1
        var bestScore = 0f
        for (lag in lagMin..lagMax) {
            val b = 60f * hopRate / lag
            val o = log2(b / 120f)
            val score = r[lag] * exp(-0.5f * o * o)
            if (score > bestScore) { bestScore = score; best = lag }
        }
        if (best < 0) {
            confidence *= 0.5f
            return
        }
        // Parabolische Verfeinerung für Bruchteile eines Schritts
        val a = r[best - 1]; val b = r[best]; val c = r[best + 1]
        val den = a - 2f * b + c
        val delta = if (abs(den) > 1e-9f) (0.5f * (a - c) / den).coerceIn(-0.5f, 0.5f) else 0f
        val newBpm = 60f * hopRate / (best + delta)
        val conf = (b / r0).coerceIn(0f, 1f)
        confidence += (conf - confidence) * 0.3f

        if (bpm == 0f) {
            bpm = newBpm
        } else if (abs(newBpm - bpm) / bpm < 0.04f) {
            bpm += (newBpm - bpm) * 0.25f
            candidateHits = 0
        } else {
            if (candidate > 0f && abs(newBpm - candidate) / candidate < 0.04f) candidateHits++
            else { candidate = newBpm; candidateHits = 1 }
            if (candidateHits >= 3) { bpm = candidate; candidateHits = 0 }
        }
    }

    companion object {
        private const val N = 512
        private const val MAX_BIN = 240 // ~5 kHz
        private const val ESTIMATE_EVERY = 43
        private const val ONSET_WINDOW = 86
        private const val MIN_BPM = 55f
        private const val MAX_BPM = 200f
    }
}
