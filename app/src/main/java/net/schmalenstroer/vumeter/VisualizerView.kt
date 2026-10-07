package net.schmalenstroer.vumeter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import net.schmalenstroer.vumeter.audio.AudioEngine
import net.schmalenstroer.vumeter.milk.MilkdropViz
import net.schmalenstroer.vumeter.viz.AnalogVu
import net.schmalenstroer.vumeter.viz.BbcPpm
import net.schmalenstroer.vumeter.viz.BpmView
import net.schmalenstroer.vumeter.viz.ChromaView
import net.schmalenstroer.vumeter.viz.Fire
import net.schmalenstroer.vumeter.viz.LedSpectrum
import net.schmalenstroer.vumeter.viz.LevelMeter
import net.schmalenstroer.vumeter.viz.LightOrgan
import net.schmalenstroer.vumeter.viz.MagicEye
import net.schmalenstroer.vumeter.viz.NixieClock
import net.schmalenstroer.vumeter.viz.PhosphorScope
import net.schmalenstroer.vumeter.viz.PulsarLines
import net.schmalenstroer.vumeter.viz.RadialSpectrum
import net.schmalenstroer.vumeter.viz.SoundLevel
import net.schmalenstroer.vumeter.viz.Spectrogram
import net.schmalenstroer.vumeter.viz.Starfield
import net.schmalenstroer.vumeter.viz.VfdDisplay
import net.schmalenstroer.vumeter.viz.Visualizer
import net.schmalenstroer.vumeter.viz.WinampScope
import net.schmalenstroer.vumeter.viz.WinampSpectrum
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Vollbild-View: zeichnet im Takt des Displays (Choreographer), optional auf 30 FPS begrenzt.
 * Tippen/Wischen wechselt die Darstellung, langes Drücken öffnet die Einstellungen.
 * Kümmert sich außerdem um Diashow, Uhr-Einblendung und Ruhemodus.
 */
@SuppressLint("ViewConstructor")
class VisualizerView(
    ctx: Context,
    private val engine: AudioEngine,
    private val s: Settings,
) : View(ctx), Choreographer.FrameCallback {

    val visualizers: List<Visualizer> = listOf(
        WinampSpectrum(s), WinampScope(s), LedSpectrum(), VfdDisplay(),
        AnalogVu(), BbcPpm(), MagicEye(), LevelMeter(), NixieClock(),
        SoundLevel(s), BpmView(), ChromaView(),
        Spectrogram(), PulsarLines(), PhosphorScope(), Fire(), LightOrgan(), Starfield(), RadialSpectrum(),
        MilkdropViz(),
    )
    private val analysis = Analysis(s)
    private var index = visualizers.indexOfFirst { it.name == s.vizName }.coerceAtLeast(0)
    private val current get() = visualizers[index]

    var onOpenSettings: (() -> Unit)? = null
    var onStatusTap: (() -> Unit)? = null
    /** Wechsel zwischen Canvas- und OpenGL-Ansicht. */
    var onGlModeChanged: ((Boolean) -> Unit)? = null
    /** Bildtakt für die OpenGL-Ansicht. */
    var onGlFrame: (() -> Unit)? = null
    /** Wischen hoch/runter in der OpenGL-Ansicht (+1 = nächstes Preset). */
    var onVerticalFling: ((Int) -> Unit)? = null
    /** FPS-Quelle der OpenGL-Ansicht. */
    var glFps: (() -> Float)? = null
    /** Ruhemodus an/aus (Activity dimmt den Bildschirm). */
    var onIdleChanged: ((Boolean) -> Unit)? = null

    val isGl get() = current.usesGl && !idle
    val currentIndex get() = index
    /** Dauerhafte Meldung (z. B. fehlende Berechtigung). */
    var status: String? = null
        set(v) { field = v; invalidate() }

    private var looping = false
    private var lastFrameNs = 0L
    private var lastDrawNs = 0L
    private var overlay: String? = null
    private var overlayLeft = 0f
    private var fps = 0f
    /** Im letzten Bild wurde Text gezeichnet (muss in der GL-Ansicht wieder gelöscht werden). */
    private var drewText = false
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastInteractionMs = SystemClock.elapsedRealtime()
    private var lastSwitchMs = SystemClock.elapsedRealtime()
    private var idle = false
    private val cal = Calendar.getInstance()
    private var clockMinute = -1
    private var clockText = ""

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
        textAlign = Paint.Align.RIGHT
    }

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (status != null) onStatusTap?.invoke() else step(1)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onOpenSettings?.invoke()
        }
    })

    init {
        showOverlay(HINT, 4f)
    }

    fun select(i: Int) {
        val wasGl = isGl
        index = ((i % visualizers.size) + visualizers.size) % visualizers.size
        s.vizName = current.name
        lastSwitchMs = SystemClock.elapsedRealtime()
        if (wasGl != isGl) onGlModeChanged?.invoke(isGl)
        showOverlay(current.name, 2f)
        invalidate()
    }

    /** Nächste/vorherige Ansicht, ausgeblendete werden übersprungen. */
    private fun step(d: Int) {
        var i = index
        for (n in visualizers.indices) {
            i = ((i + d) % visualizers.size + visualizers.size) % visualizers.size
            if (visualizers[i].name !in s.hiddenViz) break
        }
        select(i)
    }

    fun showOverlay(text: String, seconds: Float) {
        overlay = text
        overlayLeft = seconds
        invalidate()
    }

    fun start() {
        if (looping) return
        looping = true
        lastDrawNs = 0L
        lastInteractionMs = SystemClock.elapsedRealtime()
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        looping = false
        Choreographer.getInstance().removeFrameCallback(this)
        setIdle(false)
    }

    private fun setIdle(on: Boolean) {
        if (on == idle) return
        val wasGl = isGl
        idle = on
        if (wasGl != isGl) onGlModeChanged?.invoke(isGl)
        onIdleChanged?.invoke(on)
        invalidate()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!looping) return
        Choreographer.getInstance().postFrameCallback(this)
        val now = SystemClock.elapsedRealtime()

        // Ruhemodus: lange Stille und keine Berührung
        val idleSec = Settings.IDLE_SEC[s.idleMode.coerceIn(0, Settings.IDLE_SEC.size - 1)]
        val quietFor = now - max(engine.lastLoudMs, lastInteractionMs)
        if (idleSec > 0 && !idle && quietFor > idleSec * 1000L) setIdle(true)
        else if (idle && (idleSec == 0 || quietFor < 1000L)) setIdle(false)
        if (idle) return

        // Diashow
        val showSec = Settings.SLIDESHOW_SEC[s.slideshow.coerceIn(0, Settings.SLIDESHOW_SEC.size - 1)]
        if (showSec > 0 && now - max(lastSwitchMs, lastInteractionMs) > showSec * 1000L) step(1)

        val minInterval = if (s.fps60) 0L else 25_000_000L
        if (frameTimeNanos - lastFrameNs >= minInterval) {
            lastFrameNs = frameTimeNanos
            if (isGl) {
                onGlFrame?.invoke()
                // Eigene Ebene nur neu zeichnen, wenn Text darüber liegt
                if (overlay != null || s.showFps || s.showClock || status != null || engine.error != null || drewText) invalidate()
            } else {
                invalidate()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        lastInteractionMs = SystemClock.elapsedRealtime()
        if (idle) {
            // Erste Berührung weckt nur auf
            if (event.actionMasked == MotionEvent.ACTION_DOWN) setIdle(false)
            return true
        }
        // Wischen über die Strecke erkennen statt über die Geschwindigkeit: robuster, wenn der
        // Finger vor dem Loslassen kurz anhält (dann meldet der VelocityTracker 0)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val minDist = SWIPE_DP * resources.displayMetrics.density
                if (event.eventTime - downTime < SWIPE_MAX_MS && max(abs(dx), abs(dy)) > minDist) {
                    if (abs(dx) > abs(dy)) step(if (dx < 0) 1 else -1)
                    else if (isGl) onVerticalFling?.invoke(if (dy < 0) 1 else -1)
                }
            }
        }
        gestures.onTouchEvent(event)
        return true
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastDrawNs == 0L) 1f / 60f else ((now - lastDrawNs) / 1e9f).coerceIn(0.001f, 0.1f)
        lastDrawNs = now
        fps += (1f / dt - fps) * 0.05f

        if (idle) {
            c.drawColor(Color.BLACK)
            return
        }
        if (isGl) {
            // Transparent lassen: die OpenGL-Fläche dahinter ist sichtbar
            drewText = false
        } else {
            engine.read(analysis.frame)
            analysis.update(dt)
            if (current.width != width || current.height != height) current.resize(width, height)
            c.drawColor(Color.BLACK)
            if (width > 0 && height > 0) current.draw(c, analysis, dt)
        }

        val h = height.toFloat()
        val msg = status ?: engine.error
        if (msg != null) {
            drewText = true
            textPaint.textSize = h * 0.04f
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.alpha = 255
            var y = h / 2
            for (line in msg.split('\n')) {
                c.drawText(line, width / 2f, y, textPaint)
                y += textPaint.textSize * 1.4f
            }
        }

        val ov = overlay
        if (ov != null) {
            overlayLeft -= dt
            if (overlayLeft <= 0f) {
                overlay = null
            } else {
                textPaint.textSize = h * 0.04f
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.alpha = (overlayLeft.coerceAtMost(0.5f) * 2f * 255).toInt()
                c.drawText(ov, h * 0.03f, h * 0.07f, textPaint)
                drewText = true
            }
        }
        if (s.showFps) {
            smallPaint.textSize = h * 0.03f
            val f = if (isGl) glFps?.invoke() ?: fps else fps
            c.drawText("${f.toInt()} FPS", width - h * 0.02f, h * 0.05f, smallPaint)
            drewText = true
        }
        if (s.showClock) {
            cal.timeInMillis = System.currentTimeMillis()
            val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            if (m != clockMinute) {
                clockMinute = m
                clockText = String.format(Locale.GERMANY, "%02d:%02d", m / 60, m % 60)
            }
            smallPaint.textSize = h * 0.045f
            smallPaint.alpha = 170
            c.drawText(clockText, width - h * 0.03f, h - h * 0.03f, smallPaint)
            smallPaint.alpha = 200
            drewText = true
        }
    }

    companion object {
        private const val SWIPE_DP = 60f
        private const val SWIPE_MAX_MS = 1000L
        private const val HINT = "Tippen/Wischen: Ansicht wechseln · Lange drücken: Einstellungen"
    }
}
