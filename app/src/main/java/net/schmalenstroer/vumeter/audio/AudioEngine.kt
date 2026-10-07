package net.schmalenstroer.vumeter.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Momentaufnahme der Analyse, wird vom Render-Thread befüllt. */
class AudioFrame {
    /** Leistungsspektrum, normiert so dass ein Vollaussteuerungs-Sinus ≈ 1.0 ergibt. */
    val power = FloatArray(AudioEngine.FFT_SIZE / 2)
    /** Letzte Samples (DC-frei), -1..1. */
    val wave = FloatArray(AudioEngine.WAVE_SIZE)
    var rms = 0f
    var peak = 0f
    var rmsDb = -120f
    var peakDb = -120f
    /** true, wenn seit dem letzten Lesen neue Audiodaten analysiert wurden. */
    var fresh = false
    internal var seq = 0L
    /** Erkanntes Tempo (0 = unbekannt) und wie sicher die Erkennung ist (0..1). */
    var bpm = 0f
    var bpmConfidence = 0f
    /** Zählt jeden erkannten Beat hoch. */
    var beatCount = 0L
}

/**
 * Liest das Mikrofon in einem eigenen Thread, berechnet FFT, RMS und Spitzenpegel.
 * Der Render-Thread holt sich die Daten per [read] (kurzer Lock, nur Array-Kopien).
 */
class AudioEngine(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val FFT_SIZE = 2048
        const val HOP = 512
        const val WAVE_SIZE = 1024
        private const val TAG = "AudioEngine"
    }

    private val lock = Any()
    private val sharedPower = FloatArray(FFT_SIZE / 2)
    private val sharedWave = FloatArray(WAVE_SIZE)
    private var sumSq = 0.0
    private var sumCount = 0
    private var peakAcc = 0f
    private var lastRms = 0f
    private var lastPeak = 0f
    private var seq = 0L
    private var sharedBpm = 0f
    private var sharedBpmConf = 0f
    private var sharedBeats = 0L

    /** Zeitpunkt (elapsedRealtime) des letzten Signals deutlich über dem Grundrauschen. */
    @Volatile var lastLoudMs = SystemClock.elapsedRealtime()
        private set

    @Volatile var sourceName = "–"
        private set
    @Volatile var error: String? = null
        private set
    @Volatile private var running = false
    private var thread: Thread? = null

    /** Demo-Modus: synthetisches Signal statt Mikrofon (für Tests und Screenshots). */
    var demo = false

    fun start() {
        if (running) return
        error = null
        lastLoudMs = SystemClock.elapsedRealtime()
        running = true
        thread = Thread(::run, "AudioEngine").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
    }

    fun read(f: AudioFrame) {
        synchronized(lock) {
            System.arraycopy(sharedPower, 0, f.power, 0, sharedPower.size)
            System.arraycopy(sharedWave, 0, f.wave, 0, sharedWave.size)
            if (sumCount > 0) {
                lastRms = sqrt(sumSq / sumCount).toFloat()
                lastPeak = peakAcc
            }
            sumSq = 0.0
            sumCount = 0
            peakAcc = 0f
            f.rms = lastRms
            f.peak = lastPeak
            f.fresh = seq != f.seq
            f.seq = seq
            f.bpm = sharedBpm
            f.bpmConfidence = sharedBpmConf
            f.beatCount = sharedBeats
        }
        f.rmsDb = toDb(f.rms)
        f.peakDb = toDb(f.peak)
    }

    private fun toDb(v: Float) = if (v < 1e-6f) -120f else 20f * log10(v)

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val synth = if (demo) DemoSignal(SAMPLE_RATE) else null
        if (synth != null) sourceName = "Demo-Signal"
        val rec = if (synth != null) null else try {
            createRecorder()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord", e)
            null
        }
        if (rec == null && synth == null) {
            error = "Mikrofon konnte nicht geöffnet werden"
            running = false
            return
        }
        val effects = if (rec != null) disableEffects(rec.audioSessionId) else emptyList()

        val window = FloatArray(FFT_SIZE) { (0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1))).toFloat() }
        val fft = FFT(FFT_SIZE)
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        val ring = FloatArray(FFT_SIZE)
        val mask = FFT_SIZE - 1
        var pos = 0
        val chunk = ShortArray(HOP)
        val power = FloatArray(FFT_SIZE / 2)
        val wave = FloatArray(WAVE_SIZE)
        // Amplitudennormierung: 2 / Summe(Hann) = 4 / N, quadriert für Leistung
        val norm = (4f / FFT_SIZE) * (4f / FFT_SIZE)
        val beats = BeatTracker(SAMPLE_RATE.toFloat() / HOP)
        var noiseFloor = -60f
        var smoothPow = 1e-9f
        val hopSec = HOP.toFloat() / SAMPLE_RATE

        try {
            rec?.startRecording()
            if (rec != null && rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                error = "Aufnahme konnte nicht gestartet werden (Mikrofon belegt?)"
                return
            }
            while (running) {
                var got = if (synth != null) synth.fill(chunk) else 0
                while (got < HOP && running && rec != null) {
                    val n = rec.read(chunk, got, HOP - got)
                    if (n < 0) {
                        error = "Lesefehler vom Mikrofon ($n)"
                        running = false
                        break
                    }
                    got += n
                }
                if (!running) break

                var ss = 0.0
                var pk = 0f
                for (i in 0 until HOP) {
                    val v = chunk[i] / 32768f
                    ring[pos] = v
                    pos = (pos + 1) and mask
                    ss += (v * v).toDouble()
                    val a = abs(v)
                    if (a > pk) pk = a
                }

                for (i in 0 until FFT_SIZE) {
                    re[i] = ring[(pos + i) and mask] * window[i]
                    im[i] = 0f
                }
                fft.transform(re, im)
                for (k in 0 until FFT_SIZE / 2) power[k] = (re[k] * re[k] + im[k] * im[k]) * norm
                beats.process(power)

                // Ruhemodus: geglätteter Pegel (~0,3 s) gegen langsam nachgeführtes Grundrauschen.
                // Leiser als −70 dBFS zählt nie als Musik, lauter als −40 dBFS immer.
                smoothPow += ((ss / HOP).toFloat() - smoothPow) * (hopSec / 0.3f)
                val lvl = 10f * log10(smoothPow + 1e-12f)
                noiseFloor = if (lvl < noiseFloor) noiseFloor + (lvl - noiseFloor) * 0.05f
                else noiseFloor + hopSec / 60f
                if (lvl > -40f || (lvl > -70f && lvl > noiseFloor + 12f)) lastLoudMs = SystemClock.elapsedRealtime()

                var mean = 0f
                val start = pos - WAVE_SIZE
                for (i in 0 until WAVE_SIZE) {
                    val v = ring[(start + i) and mask]
                    wave[i] = v
                    mean += v
                }
                mean /= WAVE_SIZE
                for (i in 0 until WAVE_SIZE) wave[i] -= mean

                synchronized(lock) {
                    System.arraycopy(power, 0, sharedPower, 0, power.size)
                    System.arraycopy(wave, 0, sharedWave, 0, wave.size)
                    sumSq += ss
                    sumCount += HOP
                    if (pk > peakAcc) peakAcc = pk
                    sharedBpm = beats.bpm
                    sharedBpmConf = beats.confidence
                    sharedBeats = beats.beatCount
                    seq++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Aufnahme", e)
            error = "Aufnahmefehler: ${e.message}"
        } finally {
            try { rec?.stop() } catch (_: Exception) {}
            rec?.release()
            effects.forEach { try { it.release() } catch (_: Exception) {} }
            synchronized(lock) {
                sharedPower.fill(0f)
                sharedWave.fill(0f)
                lastRms = 0f
                lastPeak = 0f
                sharedBpm = 0f
                sharedBpmConf = 0f
                seq++
            }
            running = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun createRecorder(): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return null
        val bufBytes = max(minBuf, HOP * 2 * 8)

        // Bevorzugt Quellen ohne Sprachverarbeitung (AGC/Rauschunterdrückung verfälschen Musik)
        val sources = ArrayList<Pair<Int, String>>()
        if (Build.VERSION.SDK_INT >= 24) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
                sources += MediaRecorder.AudioSource.UNPROCESSED to "Unverarbeitet (UNPROCESSED)"
            }
        }
        sources += MediaRecorder.AudioSource.VOICE_RECOGNITION to "Spracherkennung (VOICE_RECOGNITION)"
        sources += MediaRecorder.AudioSource.MIC to "Mikrofon (MIC)"

        for ((src, name) in sources) {
            try {
                val r = AudioRecord(
                    src, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes
                )
                if (r.state == AudioRecord.STATE_INITIALIZED) {
                    sourceName = name
                    return r
                }
                r.release()
            } catch (e: Exception) {
                Log.w(TAG, "Quelle $name nicht verfügbar", e)
            }
        }
        return null
    }

    private fun disableEffects(session: Int): List<android.media.audiofx.AudioEffect> {
        val list = ArrayList<android.media.audiofx.AudioEffect>()
        try {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(session)?.let { it.enabled = false; list += it }
            }
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(session)?.let { it.enabled = false; list += it }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Effekte", e)
        }
        return list
    }
}
