package net.schmalenstroer.vumeter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import net.schmalenstroer.vumeter.audio.AudioEngine
import net.schmalenstroer.vumeter.milk.MilkdropViz
import net.schmalenstroer.vumeter.viz.AnalogVu
import net.schmalenstroer.vumeter.viz.LedSpectrum
import net.schmalenstroer.vumeter.viz.LevelMeter
import net.schmalenstroer.vumeter.viz.RadialSpectrum
import net.schmalenstroer.vumeter.viz.Spectrogram
import net.schmalenstroer.vumeter.viz.Visualizer
import net.schmalenstroer.vumeter.viz.WinampScope
import net.schmalenstroer.vumeter.viz.WinampSpectrum
import kotlin.math.abs
import kotlin.math.max

/**
 * Vollbild-View: zeichnet im Takt des Displays (Choreographer), optional auf 30 FPS begrenzt.
 * Tippen/Wischen wechselt die Darstellung, langes Drücken öffnet die Einstellungen.
 */
@SuppressLint("ViewConstructor")
class VisualizerView(
    ctx: Context,
    private val engine: AudioEngine,
    private val s: Settings,
) : View(ctx), Choreographer.FrameCallback {

    val visualizers: List<Visualizer> = listOf(
        WinampSpectrum(s), WinampScope(s), LedSpectrum(), AnalogVu(), LevelMeter(), Spectrogram(), RadialSpectrum(),
        MilkdropViz(),
    )
    private val analysis = Analysis(s)
    private var current = visualizers[s.viz.coerceIn(0, visualizers.size - 1)]

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

    val isGl get() = current.usesGl
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

    fun select(index: Int) {
        val i = ((index % visualizers.size) + visualizers.size) % visualizers.size
        s.viz = i
        val wasGl = isGl
        current = visualizers[i]
        if (wasGl != isGl) onGlModeChanged?.invoke(isGl)
        showOverlay(current.name, 2f)
        invalidate()
    }

    private fun step(d: Int) = select(s.viz + d)

    fun showOverlay(text: String, seconds: Float) {
        overlay = text
        overlayLeft = seconds
        invalidate()
    }

    fun start() {
        if (looping) return
        looping = true
        lastDrawNs = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        looping = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!looping) return
        Choreographer.getInstance().postFrameCallback(this)
        val minInterval = if (s.fps60) 0L else 25_000_000L
        if (frameTimeNanos - lastFrameNs >= minInterval) {
            lastFrameNs = frameTimeNanos
            if (isGl) {
                onGlFrame?.invoke()
                // Eigene Ebene nur neu zeichnen, wenn Text darüber liegt
                if (overlay != null || s.showFps || status != null || engine.error != null || drewText) invalidate()
            } else {
                invalidate()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
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
    }

    companion object {
        private const val SWIPE_DP = 60f
        private const val SWIPE_MAX_MS = 1000L
        private const val HINT = "Tippen/Wischen: Ansicht wechseln · Lange drücken: Einstellungen"
    }
}
