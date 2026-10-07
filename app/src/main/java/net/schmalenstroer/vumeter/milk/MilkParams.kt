package net.schmalenstroer.vumeter.milk

/** HSV → RGB ohne Allokation. [h] wird zyklisch auf 0..1 abgebildet. */
internal inline fun hsv(h: Float, s: Float, v: Float, set: (Float, Float, Float) -> Unit) {
    val hh = (((h % 1f) + 1f) % 1f) * 6f
    val i = hh.toInt()
    val f = hh - i
    val p = v * (1 - s)
    val q = v * (1 - s * f)
    val t = v * (1 - s * (1 - f))
    when (i) {
        0 -> set(v, t, p)
        1 -> set(q, v, p)
        2 -> set(p, v, t)
        3 -> set(p, q, v)
        4 -> set(t, p, v)
        else -> set(v, p, q)
    }
}

/** Gefüllte Form (Vieleck), optional mit dem vorherigen Bild als Textur. */
class Shape {
    var enabled = false
    var sides = 6
    var x = 0.5f
    var y = 0.5f
    var rad = 0.1f
    var ang = 0f
    var r = 1f; var g = 1f; var b = 1f; var a = 1f
    var r2 = 1f; var g2 = 1f; var b2 = 1f; var a2 = 0f
    var additive = true
    var textured = false
    var texZoom = 1f
    var texAng = 0f
    var borderR = 1f; var borderG = 1f; var borderB = 1f; var borderA = 0f

    fun reset() {
        enabled = false; sides = 6; x = 0.5f; y = 0.5f; rad = 0.1f; ang = 0f
        r = 1f; g = 1f; b = 1f; a = 1f; r2 = 1f; g2 = 1f; b2 = 1f; a2 = 0f
        additive = true; textured = false; texZoom = 1f; texAng = 0f
        borderR = 1f; borderG = 1f; borderB = 1f; borderA = 0f
    }

    fun hsv(h: Float, s: Float = 1f, v: Float = 1f) = hsv(h, s, v) { rr, gg, bb -> r = rr; g = gg; b = bb }
    fun hsv2(h: Float, s: Float = 1f, v: Float = 1f) = hsv(h, s, v) { rr, gg, bb -> r2 = rr; g2 = gg; b2 = bb }
}

/**
 * Parameter eines Presets, angelehnt an die Variablen von MilkDrop
 * (zoom, rot, warp, decay, wave_*, ob_*, echo_* …). Bewegungswerte gelten pro Bild bei 60 FPS.
 */
class MilkParams {
    var decay = 0.98f
    var zoom = 1f
    var zoomExp = 1f
    var rot = 0f
    var warp = 1f
    var warpSpeed = 1f
    var warpScale = 1f
    var cx = 0.5f; var cy = 0.5f
    var dx = 0f; var dy = 0f
    var sx = 1f; var sy = 1f
    /** Radiale Welle im Zoom: Amplitude, Frequenz, Geschwindigkeit. */
    var radAmp = 0f; var radFreq = 10f; var radSpeed = 1f
    /** Winkelabhängige Rotation (Spiralen): Amplitude, Frequenz, Geschwindigkeit. */
    var angAmp = 0f; var angFreq = 3f; var angSpeed = 1f
    var wrap = true
    var darkenCenter = false
    var gamma = 1f
    var echoAlpha = 0f
    var echoZoom = 1f
    /** 0 = normal, 1 = horizontal gespiegelt, 2 = vertikal, 3 = beides */
    var echoOrient = 0
    var obSize = 0f; var obR = 0f; var obG = 0f; var obB = 0f; var obA = 0f
    var ibSize = 0f; var ibR = 0f; var ibG = 0f; var ibB = 0f; var ibA = 0f

    var waveMode = WAVE_CIRCLE
    var waveR = 1f; var waveG = 1f; var waveB = 1f; var waveA = 0.8f
    var waveScale = 1f
    var waveX = 0.5f; var waveY = 0.5f
    /** Kreisradius bzw. Modus-spezifischer Wert (wie wave_mystery). */
    var waveRadius = 0.2f
    var waveThick = 1.5f
    var waveDots = false
    var waveAdditive = true

    val shapes = arrayOf(Shape(), Shape())

    fun reset() {
        decay = 0.98f; zoom = 1f; zoomExp = 1f; rot = 0f; warp = 1f; warpSpeed = 1f; warpScale = 1f
        cx = 0.5f; cy = 0.5f; dx = 0f; dy = 0f; sx = 1f; sy = 1f
        radAmp = 0f; radFreq = 10f; radSpeed = 1f; angAmp = 0f; angFreq = 3f; angSpeed = 1f
        wrap = true; darkenCenter = false; gamma = 1f; echoAlpha = 0f; echoZoom = 1f; echoOrient = 0
        obSize = 0f; obR = 0f; obG = 0f; obB = 0f; obA = 0f
        ibSize = 0f; ibR = 0f; ibG = 0f; ibB = 0f; ibA = 0f
        waveMode = WAVE_CIRCLE; waveR = 1f; waveG = 1f; waveB = 1f; waveA = 0.8f; waveScale = 1f
        waveX = 0.5f; waveY = 0.5f; waveRadius = 0.2f; waveThick = 1.5f; waveDots = false; waveAdditive = true
        for (sh in shapes) sh.reset()
    }

    fun waveHsv(h: Float, s: Float = 1f, v: Float = 1f) = hsv(h, s, v) { r, g, b -> waveR = r; waveG = g; waveB = b }
    fun obHsv(h: Float, s: Float = 1f, v: Float = 1f) = hsv(h, s, v) { r, g, b -> obR = r; obG = g; obB = b }
    fun ibHsv(h: Float, s: Float = 1f, v: Float = 1f) = hsv(h, s, v) { r, g, b -> ibR = r; ibG = g; ibB = b }

    /** Überblendet die Bewegungs- und Bildparameter zwischen zwei Presets (Welle/Formen werden getrennt gezeichnet). */
    fun blend(a: MilkParams, b: MilkParams, f: Float) {
        fun l(x: Float, y: Float) = x + (y - x) * f
        decay = l(a.decay, b.decay); zoom = l(a.zoom, b.zoom); zoomExp = l(a.zoomExp, b.zoomExp)
        rot = l(a.rot, b.rot); warp = l(a.warp, b.warp); warpSpeed = l(a.warpSpeed, b.warpSpeed)
        warpScale = l(a.warpScale, b.warpScale)
        cx = l(a.cx, b.cx); cy = l(a.cy, b.cy); dx = l(a.dx, b.dx); dy = l(a.dy, b.dy)
        sx = l(a.sx, b.sx); sy = l(a.sy, b.sy)
        radAmp = l(a.radAmp, b.radAmp); radFreq = l(a.radFreq, b.radFreq); radSpeed = l(a.radSpeed, b.radSpeed)
        angAmp = l(a.angAmp, b.angAmp); angFreq = l(a.angFreq, b.angFreq); angSpeed = l(a.angSpeed, b.angSpeed)
        gamma = l(a.gamma, b.gamma); echoAlpha = l(a.echoAlpha, b.echoAlpha); echoZoom = l(a.echoZoom, b.echoZoom)
        obSize = l(a.obSize, b.obSize); obR = l(a.obR, b.obR); obG = l(a.obG, b.obG); obB = l(a.obB, b.obB); obA = l(a.obA, b.obA)
        ibSize = l(a.ibSize, b.ibSize); ibR = l(a.ibR, b.ibR); ibG = l(a.ibG, b.ibG); ibB = l(a.ibB, b.ibB); ibA = l(a.ibA, b.ibA)
        val n = if (f < 0.5f) a else b
        wrap = n.wrap; darkenCenter = n.darkenCenter; echoOrient = n.echoOrient
    }

    companion object {
        const val WAVE_CIRCLE = 0
        const val WAVE_LINE = 1
        const val WAVE_LISSAJOUS = 2
        const val WAVE_SPECTRUM = 3
        const val WAVE_DOUBLE = 4
    }
}

/** Werte, die den Preset-Formeln pro Bild zur Verfügung stehen (wie in MilkDrop: time, bass, mid, treb …). */
class MilkCtx {
    var time = 0f
    var dt = 1f / 60f
    var bass = 1f; var mid = 1f; var treb = 1f
    var bassAtt = 1f; var midAtt = 1f; var trebAtt = 1f
    var beat = false
    /** Speicher, der pro Preset über Bilder hinweg erhalten bleibt (wie q1…q8). */
    var q = FloatArray(8)
}

class Preset(
    val name: String,
    val setup: MilkParams.() -> Unit,
    val update: MilkParams.(MilkCtx) -> Unit = {},
)
