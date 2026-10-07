package net.schmalenstroer.vumeter.milk

import net.schmalenstroer.vumeter.milk.MilkParams.Companion.WAVE_CIRCLE
import net.schmalenstroer.vumeter.milk.MilkParams.Companion.WAVE_DOUBLE
import net.schmalenstroer.vumeter.milk.MilkParams.Companion.WAVE_LINE
import net.schmalenstroer.vumeter.milk.MilkParams.Companion.WAVE_LISSAJOUS
import net.schmalenstroer.vumeter.milk.MilkParams.Companion.WAVE_SPECTRUM
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Eigene Presets im Stil von MilkDrop. */
object Presets {
    val all: List<Preset> = listOf(
        Preset("Bass-Tunnel", setup = {
            decay = 0.975f; warp = 0.4f; darkenCenter = true; wrap = false
            waveMode = WAVE_CIRCLE; waveRadius = 0.18f; waveScale = 0.6f; waveThick = 2f
        }, update = { c ->
            zoom = 1f + 0.025f * c.bass
            rot = 0.01f * sin(c.time * 0.3f)
            cx = 0.5f + 0.05f * sin(c.time * 0.4f)
            cy = 0.5f + 0.05f * cos(c.time * 0.33f)
            waveHsv(c.time * 0.05f)
            waveA = min(1f, 0.5f + 0.3f * c.treb)
        }),

        Preset("Spiralgalaxie", setup = {
            decay = 0.985f; zoom = 1.01f; angAmp = 0.012f; angFreq = 3f; angSpeed = 0.7f; warp = 0.2f
            waveMode = WAVE_SPECTRUM; waveRadius = 0.12f; waveScale = 0.5f; waveDots = true; waveThick = 2.5f
        }, update = { c ->
            rot = 0.012f + 0.008f * c.midAtt
            waveHsv(0.6f + 0.15f * sin(c.time * 0.2f), 0.7f)
            waveA = min(1f, 0.4f + 0.4f * c.trebAtt)
        }),

        Preset("Flüssiges Feuer", setup = {
            decay = 0.97f; warpSpeed = 1.5f; warpScale = 1.3f; wrap = false
            waveMode = WAVE_LINE; waveY = 0.12f; waveScale = 0.6f; waveThick = 3f
        }, update = { c ->
            warp = 1.5f + c.bassAtt
            dy = 0.004f + 0.003f * c.bass
            waveHsv(0.03f + 0.04f * sin(c.time * 0.7f))
            waveA = min(1f, 0.6f + 0.3f * c.bass)
        }),

        Preset("Kaleidoskop", setup = {
            decay = 0.96f; echoAlpha = 0.5f; echoOrient = 3; warp = 0.3f
            waveMode = WAVE_CIRCLE; waveRadius = 0.1f; waveScale = 0.8f; waveThick = 2f
            shapes[0].apply {
                enabled = true; sides = 6; textured = true; additive = false; texZoom = 1.4f
                a = 0.9f; a2 = 0.9f
            }
        }, update = { c ->
            rot = 0.01f * sin(c.time * 0.5f)
            shapes[0].ang = c.time * 0.3f
            shapes[0].texAng = -c.time * 0.2f
            shapes[0].rad = 0.22f + 0.05f * c.bassAtt
            waveHsv(c.time * 0.08f)
        }),

        Preset("Plasmawellen", setup = {
            decay = 0.98f; radFreq = 18f; radSpeed = 3f; warp = 0.6f; gamma = 1.2f
            waveMode = WAVE_LINE; waveY = 0.5f; waveScale = 0.8f; waveThick = 2f
        }, update = { c ->
            radAmp = 0.008f + 0.015f * c.bassAtt
            zoom = 1.005f
            waveHsv(c.time * 0.1f)
        }),

        Preset("Hyperraum", setup = {
            decay = 0.97f; zoomExp = 1.6f; darkenCenter = true; obSize = 0.01f
            waveMode = WAVE_SPECTRUM; waveRadius = 0.08f; waveScale = 0.35f; waveDots = true; waveThick = 2f
        }, update = { c ->
            zoom = 1.03f + 0.035f * c.bassAtt
            rot = 0.005f * sin(c.time * 0.21f)
            obHsv(c.time * 0.04f)
            obA = min(1f, 0.15f + 0.35f * c.treb)
            waveHsv(c.time * 0.04f + 0.5f, 0.5f)
        }),

        Preset("Pulsierende Blüte", setup = {
            decay = 0.95f; zoom = 0.985f; warp = 0.5f
            waveMode = WAVE_CIRCLE; waveRadius = 0.32f; waveScale = 0.5f; waveThick = 1.5f; waveA = 0.5f
            shapes[0].apply { enabled = true; sides = 7; additive = true; a = 0.6f; a2 = 0f }
            shapes[1].apply { enabled = true; sides = 3; additive = true; a = 0.25f; a2 = 0.05f; rad = 0.4f }
        }, update = { c ->
            if (c.q[0] == 0f) c.q[0] = 1f
            if (c.beat) c.q[0] = -c.q[0]
            rot = 0.015f * c.q[0]
            val h = c.time * 0.06f
            shapes[0].rad = 0.08f + 0.14f * c.bassAtt.coerceAtMost(2f)
            shapes[0].ang = c.time * 0.5f
            shapes[0].hsv(h); shapes[0].hsv2(h + 0.3f)
            shapes[1].ang = -c.time * 0.3f
            shapes[1].hsv(h + 0.5f, 0.6f); shapes[1].hsv2(h + 0.7f)
            waveHsv(h + 0.15f, 0.4f)
        }),

        Preset("Oszillo-Wirbel", setup = {
            decay = 0.99f; zoom = 1.005f; warp = 0.3f
            echoAlpha = 0.3f; echoZoom = 1.3f; echoOrient = 1
            waveMode = WAVE_LISSAJOUS; waveScale = 0.9f; waveThick = 1.5f
        }, update = { c ->
            rot = 0.006f + 0.01f * sin(c.time * 0.2f)
            waveHsv(c.time * 0.03f, 0.8f)
            waveA = min(1f, 0.4f + 0.3f * c.midAtt)
        }),

        Preset("Neonregen", setup = {
            decay = 0.965f; warp = 0.2f; wrap = false
            waveMode = WAVE_LINE; waveY = 0.9f; waveDots = true; waveThick = 2.5f
        }, update = { c ->
            dy = -0.006f - 0.004f * c.bass
            waveScale = 0.5f + 0.3f * c.treb
            waveHsv(0.45f + 0.1f * sin(c.time * 0.3f))
        }),

        Preset("Strudel", setup = {
            decay = 0.98f; zoom = 0.97f; angAmp = 0.02f; angFreq = 2f; obSize = 0.015f
            waveMode = WAVE_CIRCLE; waveRadius = 0.06f; waveScale = 0.7f; waveThick = 2f
        }, update = { c ->
            rot = 0.02f + 0.01f * c.midAtt
            obHsv(c.time * 0.05f)
            obA = min(1f, 0.4f + 0.3f * c.bass)
            waveHsv(c.time * 0.05f + 0.5f)
        }),

        Preset("Sternenexplosion", setup = {
            decay = 0.94f; gamma = 1.1f
            waveMode = WAVE_SPECTRUM; waveRadius = 0.08f; waveScale = 0.4f; waveThick = 2f
        }, update = { c ->
            if (c.beat) c.q[1] = 1f
            c.q[1] *= exp(-c.dt * 4f)
            zoom = 1.01f + 0.12f * c.q[1]
            rot = 0.02f * sin(c.time * 0.4f)
            waveHsv(c.time * 0.07f)
        }),

        Preset("Doppelhelix", setup = {
            decay = 0.975f; warp = 0.8f; zoom = 1.008f; sx = 1.004f
            waveMode = WAVE_DOUBLE; waveY = 0.5f; waveRadius = 0.18f; waveScale = 0.5f; waveThick = 2f
            ibSize = 0.005f
        }, update = { c ->
            rot = 0.004f * sin(c.time * 0.37f)
            waveHsv(c.time * 0.06f, 0.9f)
            ibHsv(c.time * 0.06f + 0.5f)
            ibA = 0.2f * c.trebAtt.coerceAtMost(2f)
        }),
    )
}
