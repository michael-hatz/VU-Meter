package net.schmalenstroer.vumeter.milk

import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.BandMapper
import net.schmalenstroer.vumeter.Settings
import net.schmalenstroer.vumeter.audio.AudioEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * MilkDrop-artiger Renderer (OpenGL ES 2.0).
 *
 * Pro Bild:
 *  1. Warp: das vorherige Bild wird über ein Gitter gezoomt/gedreht/verzerrt und abgedunkelt
 *     in den zweiten Puffer kopiert (Rückkopplung).
 *  2. Darüber werden Ränder, Formen und die Wellenform gezeichnet.
 *  3. Ausgabe auf den Bildschirm, optional mit "Video-Echo" (gespiegelte Überlagerung).
 * Gerendert wird intern in niedriger Auflösung und hochskaliert.
 */
class MilkRenderer(private val engine: AudioEngine, private val s: Settings) : GLSurfaceView.Renderer {

    /** Wird (auf dem GL-Thread) mit dem Namen eines neuen Presets aufgerufen. */
    var onPresetChanged: ((String) -> Unit)? = null
    @Volatile var fps = 0f
        private set

    private inner class Slot(val preset: Preset) {
        val p = MilkParams()
        val q = FloatArray(8)
        fun eval() {
            p.reset()
            preset.setup(p)
            ctx.q = q
            preset.update(p, ctx)
        }
    }

    private val analysis = Analysis(s)
    private val audio = MilkAudio()
    private val specMapper = BandMapper(SPEC_BANDS, 40f, 12000f)
    private val spec = FloatArray(SPEC_BANDS)
    private val ctx = MilkCtx()
    private val mix = MilkParams()
    private val presets = Presets.all
    private val rnd = Random(System.nanoTime())
    private var index = rnd.nextInt(presets.size)
    private var cur = Slot(presets[index])
    private var old: Slot? = null
    private var blend = 1f
    private var blendDur = 3f
    private var sinceSwitch = 0f
    private var announce = true

    // GL-Zustand
    private var warpProg = 0
    private var compProg = 0
    private var colorProg = 0
    private var texProg = 0
    private val tex = IntArray(2)
    private val fbo = IntArray(2)
    private var texW = 0
    private var texH = 0
    private var srcIdx = 0
    private var screenW = 1
    private var screenH = 1
    private var aspect = 1f
    private var meshVbo = 0
    private var meshIbo = 0
    private var meshCount = 0
    private var lastNs = 0L
    private var warpPhase = 0f
    private var radPhase = 0f
    private var angPhase = 0f

    private val buf: FloatBuffer = Gl.floats(MAX_VERTS * 8)
    private val quad: FloatBuffer = Gl.floats(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val px = FloatArray(MAX_POINTS)
    private val py = FloatArray(MAX_POINTS)

    // Uniform-/Attribut-Positionen
    private var wPos = 0; private var wAspect = 0; private var wCenter = 0; private var wZoom = 0
    private var wZoomExp = 0; private var wRot = 0; private var wWarp = 0; private var wWarpPhase = 0
    private var wWarpScaleInv = 0; private var wF = 0; private var wD = 0; private var wS = 0
    private var wRad = 0; private var wAng = 0; private var wK = 0; private var wTex = 0
    private var wDecay = 0; private var wFade = 0
    private var cPos = 0; private var cEcho = 0; private var cTex = 0; private var cEchoAlpha = 0; private var cGamma = 0
    private var kPos = 0; private var kCol = 0; private var kPointSize = 0
    private var tPos = 0; private var tUv = 0; private var tCol = 0; private var tTex = 0

    /** Nächstes/vorheriges Preset (auf dem GL-Thread aufrufen). */
    fun step(dir: Int) {
        switchTo((index + dir + presets.size) % presets.size, 1.2f)
    }

    private fun switchTo(i: Int, duration: Float) {
        index = i
        old = cur
        cur = Slot(presets[i])
        blend = 0f
        blendDur = duration
        sinceSwitch = 0f
        announce = true
    }

    private fun randomNext() {
        var i = rnd.nextInt(presets.size - 1)
        if (i >= index) i++
        switchTo(i, 3f)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        warpProg = Gl.program(Gl.WARP_VS, Gl.WARP_FS)
        compProg = Gl.program(Gl.COMP_VS, Gl.COMP_FS)
        colorProg = Gl.program(Gl.COLOR_VS, Gl.COLOR_FS)
        texProg = Gl.program(Gl.TEX_VS, Gl.TEX_FS)

        wPos = glGetAttribLocation(warpProg, "aPos")
        wAspect = glGetUniformLocation(warpProg, "uAspect")
        wCenter = glGetUniformLocation(warpProg, "uCenter")
        wZoom = glGetUniformLocation(warpProg, "uZoom")
        wZoomExp = glGetUniformLocation(warpProg, "uZoomExp")
        wRot = glGetUniformLocation(warpProg, "uRot")
        wWarp = glGetUniformLocation(warpProg, "uWarp")
        wWarpPhase = glGetUniformLocation(warpProg, "uWarpPhase")
        wWarpScaleInv = glGetUniformLocation(warpProg, "uWarpScaleInv")
        wF = glGetUniformLocation(warpProg, "uF")
        wD = glGetUniformLocation(warpProg, "uD")
        wS = glGetUniformLocation(warpProg, "uS")
        wRad = glGetUniformLocation(warpProg, "uRad")
        wAng = glGetUniformLocation(warpProg, "uAng")
        wK = glGetUniformLocation(warpProg, "uK")
        wTex = glGetUniformLocation(warpProg, "uTex")
        wDecay = glGetUniformLocation(warpProg, "uDecay")
        wFade = glGetUniformLocation(warpProg, "uFade")

        cPos = glGetAttribLocation(compProg, "aPos")
        cEcho = glGetUniformLocation(compProg, "uEcho")
        cTex = glGetUniformLocation(compProg, "uTex")
        cEchoAlpha = glGetUniformLocation(compProg, "uEchoAlpha")
        cGamma = glGetUniformLocation(compProg, "uGamma")

        kPos = glGetAttribLocation(colorProg, "aPos")
        kCol = glGetAttribLocation(colorProg, "aCol")
        kPointSize = glGetUniformLocation(colorProg, "uPointSize")

        tPos = glGetAttribLocation(texProg, "aPos")
        tUv = glGetAttribLocation(texProg, "aUv")
        tCol = glGetAttribLocation(texProg, "aCol")
        tTex = glGetUniformLocation(texProg, "uTex")

        buildMesh()
        // Neuer Kontext: alte Handles sind ungültig
        texW = 0
        texH = 0
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenW = max(1, width)
        screenH = max(1, height)
        aspect = screenW.toFloat() / screenH
        ensureTargets()
    }

    private fun buildMesh() {
        val nx = GRID_X + 1
        val ny = GRID_Y + 1
        val verts = FloatArray(nx * ny * 2)
        var k = 0
        for (j in 0 until ny) for (i in 0 until nx) {
            verts[k++] = i * 2f / GRID_X - 1f
            verts[k++] = j * 2f / GRID_Y - 1f
        }
        val idx = ShortArray(GRID_X * GRID_Y * 6)
        k = 0
        for (j in 0 until GRID_Y) for (i in 0 until GRID_X) {
            val a = (j * nx + i).toShort()
            val b = (j * nx + i + 1).toShort()
            val c = ((j + 1) * nx + i).toShort()
            val d = ((j + 1) * nx + i + 1).toShort()
            idx[k++] = a; idx[k++] = b; idx[k++] = c
            idx[k++] = b; idx[k++] = d; idx[k++] = c
        }
        meshCount = idx.size
        val ids = IntArray(2)
        glGenBuffers(2, ids, 0)
        meshVbo = ids[0]
        meshIbo = ids[1]
        glBindBuffer(GL_ARRAY_BUFFER, meshVbo)
        glBufferData(GL_ARRAY_BUFFER, verts.size * 4, Gl.floats(*verts), GL_STATIC_DRAW)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, meshIbo)
        val ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(idx).position(0)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, ib, GL_STATIC_DRAW)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun ensureTargets() {
        val (a, b) = if (s.milkHighRes) 1024 to 512 else 512 to 256
        val w = if (screenW >= screenH) a else b
        val h = if (screenW >= screenH) b else a
        if (w == texW && h == texH) return
        if (texW > 0) {
            glDeleteFramebuffers(2, fbo, 0)
            glDeleteTextures(2, tex, 0)
        }
        texW = w
        texH = h
        glGenTextures(2, tex, 0)
        glGenFramebuffers(2, fbo, 0)
        for (i in 0..1) {
            glBindTexture(GL_TEXTURE_2D, tex[i])
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glBindFramebuffer(GL_FRAMEBUFFER, fbo[i])
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex[i], 0)
            glViewport(0, 0, w, h)
            glClearColor(0f, 0f, 0f, 1f)
            glClear(GL_COLOR_BUFFER_BIT)
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        srcIdx = 0
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 1f / 60f else ((now - lastNs) / 1e9f).coerceIn(0.001f, 0.1f)
        lastNs = now
        fps += (1f / dt - fps) * 0.05f
        ensureTargets()

        // Audio
        engine.read(analysis.frame)
        analysis.update(dt)
        audio.update(analysis.frame.power, analysis.frame.fresh, dt)
        analysis.bands(specMapper, spec)

        ctx.time += dt
        ctx.dt = dt
        ctx.bass = audio.bass; ctx.mid = audio.mid; ctx.treb = audio.treb
        ctx.bassAtt = audio.bassAtt; ctx.midAtt = audio.midAtt; ctx.trebAtt = audio.trebAtt
        ctx.beat = audio.beat

        // Preset-Wechsel
        sinceSwitch += dt
        val interval = INTERVALS[s.milkInterval.coerceIn(0, INTERVALS.size - 1)]
        if (interval > 0f && sinceSwitch > interval && (audio.beat || sinceSwitch > interval + 4f)) randomNext()
        if (announce) {
            announce = false
            onPresetChanged?.invoke(cur.preset.name)
        }
        if (blend < 1f) blend = min(1f, blend + dt / blendDur)
        val o = old
        cur.eval()
        val f = blend * blend * (3f - 2f * blend)
        if (o != null && blend < 1f) {
            o.eval()
            mix.blend(o.p, cur.p, f)
        } else {
            old = null
            mix.blend(cur.p, cur.p, 1f)
        }

        val src = srcIdx
        val dst = 1 - srcIdx

        // 1. Warp
        glBindFramebuffer(GL_FRAMEBUFFER, fbo[dst])
        glViewport(0, 0, texW, texH)
        glDisable(GL_BLEND)
        drawWarp(src, dt)

        // 2. Elemente
        glEnable(GL_BLEND)
        if (mix.darkenCenter) drawDarkenCenter()
        drawBorders()
        if (o != null && blend < 1f) {
            drawElements(o.p, 1f - f, src)
            drawElements(cur.p, f, src)
        } else {
            drawElements(cur.p, 1f, src)
        }

        // 3. Ausgabe
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glViewport(0, 0, screenW, screenH)
        glDisable(GL_BLEND)
        drawComposite(dst)

        srcIdx = dst
    }

    private fun drawWarp(src: Int, dt: Float) {
        val m = mix
        val k = dt * 60f
        warpPhase += dt * m.warpSpeed
        radPhase += dt * m.radSpeed * 2f
        angPhase += dt * m.angSpeed
        val t = warpPhase
        glUseProgram(warpProg)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, tex[src])
        val wrap = if (m.wrap) GL_REPEAT else GL_CLAMP_TO_EDGE
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap)
        glUniform1i(wTex, 0)
        glUniform1f(wAspect, aspect)
        glUniform2f(wCenter, m.cx, m.cy)
        glUniform1f(wZoom, m.zoom)
        glUniform1f(wZoomExp, m.zoomExp)
        glUniform1f(wRot, m.rot)
        glUniform1f(wWarp, m.warp)
        glUniform1f(wWarpPhase, t)
        glUniform1f(wWarpScaleInv, 1f / max(0.01f, m.warpScale))
        glUniform4f(
            wF,
            11.68f + 4f * cos(t * 1.413f + 10f), 8.77f + 3f * cos(t * 1.113f + 7f),
            10.54f + 3f * cos(t * 1.233f + 3f), 11.49f + 4f * cos(t * 0.933f + 5f),
        )
        glUniform2f(wD, m.dx, m.dy)
        glUniform2f(wS, m.sx, m.sy)
        glUniform3f(wRad, m.radAmp, m.radFreq, radPhase)
        glUniform3f(wAng, m.angAmp, m.angFreq, angPhase)
        glUniform1f(wK, k)
        glUniform1f(wDecay, Math.pow(m.decay.toDouble(), k.toDouble()).toFloat())
        glUniform1f(wFade, 1f / 255f)
        glBindBuffer(GL_ARRAY_BUFFER, meshVbo)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, meshIbo)
        glEnableVertexAttribArray(wPos)
        glVertexAttribPointer(wPos, 2, GL_FLOAT, false, 8, 0)
        glDrawElements(GL_TRIANGLES, meshCount, GL_UNSIGNED_SHORT, 0)
        glDisableVertexAttribArray(wPos)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun drawComposite(dst: Int) {
        val m = mix
        glUseProgram(compProg)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, tex[dst])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glUniform1i(cTex, 0)
        val z = 1f / max(0.1f, m.echoZoom)
        val fx = if (m.echoOrient == 1 || m.echoOrient == 3) -1f else 1f
        val fy = if (m.echoOrient >= 2) -1f else 1f
        glUniform2f(cEcho, fx * z, fy * z)
        glUniform1f(cEchoAlpha, m.echoAlpha)
        glUniform1f(cGamma, m.gamma)
        quad.position(0)
        glEnableVertexAttribArray(cPos)
        glVertexAttribPointer(cPos, 2, GL_FLOAT, false, 8, quad)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(cPos)
    }

    // ---------- Farbige Geometrie ----------

    private var vCount = 0

    private fun begin() {
        buf.clear()
        vCount = 0
    }

    /** Position in 0..1-Koordinaten (y nach oben). */
    private fun vtx(x: Float, y: Float, r: Float, g: Float, b: Float, a: Float) {
        if (vCount >= MAX_VERTS) return
        buf.put(x * 2f - 1f).put(y * 2f - 1f).put(r).put(g).put(b).put(a)
        vCount++
    }

    private fun flush(mode: Int, additive: Boolean, pointSize: Float = 1f) {
        if (vCount == 0) return
        glUseProgram(colorProg)
        glBlendFunc(GL_SRC_ALPHA, if (additive) GL_ONE else GL_ONE_MINUS_SRC_ALPHA)
        glUniform1f(kPointSize, pointSize)
        glEnableVertexAttribArray(kPos)
        glEnableVertexAttribArray(kCol)
        buf.position(0)
        glVertexAttribPointer(kPos, 2, GL_FLOAT, false, 24, buf)
        buf.position(2)
        glVertexAttribPointer(kCol, 4, GL_FLOAT, false, 24, buf)
        glDrawArrays(mode, 0, vCount)
        glDisableVertexAttribArray(kPos)
        glDisableVertexAttribArray(kCol)
    }

    private fun drawDarkenCenter() {
        begin()
        val r = 0.18f
        vtx(0.5f, 0.5f, 0f, 0f, 0f, 0.1f)
        for (i in 0..24) {
            val a = (i / 24f * 2f * PI).toFloat()
            vtx(0.5f + r * cos(a) / aspect, 0.5f + r * sin(a), 0f, 0f, 0f, 0f)
        }
        flush(GL_TRIANGLE_FAN, false)
    }

    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, g: Float, b: Float, a: Float) {
        vtx(x0, y0, r, g, b, a); vtx(x1, y0, r, g, b, a); vtx(x0, y1, r, g, b, a)
        vtx(x1, y0, r, g, b, a); vtx(x1, y1, r, g, b, a); vtx(x0, y1, r, g, b, a)
    }

    private fun frame(inset: Float, size: Float, r: Float, g: Float, b: Float, a: Float) {
        val sx = size / aspect
        val ix = inset / aspect
        rect(ix, inset, 1f - ix, inset + size, r, g, b, a)
        rect(ix, 1f - inset - size, 1f - ix, 1f - inset, r, g, b, a)
        rect(ix, inset + size, ix + sx, 1f - inset - size, r, g, b, a)
        rect(1f - ix - sx, inset + size, 1f - ix, 1f - inset - size, r, g, b, a)
    }

    private fun drawBorders() {
        val m = mix
        if (m.obSize <= 0.0005f && m.ibSize <= 0.0005f) return
        begin()
        if (m.obSize > 0.0005f && m.obA > 0.01f) frame(0f, m.obSize, m.obR, m.obG, m.obB, m.obA)
        if (m.ibSize > 0.0005f && m.ibA > 0.01f) frame(m.obSize, m.ibSize, m.ibR, m.ibG, m.ibB, m.ibA)
        flush(GL_TRIANGLES, false)
    }

    private fun drawElements(p: MilkParams, fade: Float, src: Int) {
        if (fade <= 0.01f) return
        for (sh in p.shapes) if (sh.enabled) drawShape(sh, fade, src)
        drawWave(p, fade)
    }

    private fun drawShape(sh: Shape, fade: Float, src: Int) {
        val n = sh.sides.coerceIn(3, 40)
        if (sh.textured) {
            buf.clear()
            var count = 0
            fun tv(x: Float, y: Float, u: Float, v: Float, a: Float) {
                buf.put(x * 2f - 1f).put(y * 2f - 1f).put(u).put(v).put(sh.r).put(sh.g).put(sh.b).put(a * fade)
                count++
            }
            tv(sh.x, sh.y, 0.5f, 0.5f, sh.a)
            for (i in 0..n) {
                val a = sh.ang + i * 2f * PI.toFloat() / n
                val ta = a + sh.texAng
                tv(
                    sh.x + sh.rad * cos(a) / aspect, sh.y + sh.rad * sin(a),
                    0.5f + sh.rad * cos(ta) / aspect / sh.texZoom, 0.5f + sh.rad * sin(ta) / sh.texZoom,
                    sh.a2,
                )
            }
            glUseProgram(texProg)
            glBlendFunc(GL_SRC_ALPHA, if (sh.additive) GL_ONE else GL_ONE_MINUS_SRC_ALPHA)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, tex[src])
            glUniform1i(tTex, 0)
            glEnableVertexAttribArray(tPos)
            glEnableVertexAttribArray(tUv)
            glEnableVertexAttribArray(tCol)
            buf.position(0); glVertexAttribPointer(tPos, 2, GL_FLOAT, false, 32, buf)
            buf.position(2); glVertexAttribPointer(tUv, 2, GL_FLOAT, false, 32, buf)
            buf.position(4); glVertexAttribPointer(tCol, 4, GL_FLOAT, false, 32, buf)
            glDrawArrays(GL_TRIANGLE_FAN, 0, count)
            glDisableVertexAttribArray(tPos)
            glDisableVertexAttribArray(tUv)
            glDisableVertexAttribArray(tCol)
        } else {
            begin()
            vtx(sh.x, sh.y, sh.r, sh.g, sh.b, sh.a * fade)
            for (i in 0..n) {
                val a = sh.ang + i * 2f * PI.toFloat() / n
                vtx(sh.x + sh.rad * cos(a) / aspect, sh.y + sh.rad * sin(a), sh.r2, sh.g2, sh.b2, sh.a2 * fade)
            }
            flush(GL_TRIANGLE_FAN, sh.additive)
        }
        if (sh.borderA > 0.01f) {
            begin()
            for (i in 0 until n) {
                val a = sh.ang + i * 2f * PI.toFloat() / n
                vtx(sh.x + sh.rad * cos(a) / aspect, sh.y + sh.rad * sin(a), sh.borderR, sh.borderG, sh.borderB, sh.borderA * fade)
            }
            flush(GL_LINE_LOOP, false)
        }
    }

    // ---------- Wellenform ----------

    private fun sample(i: Int): Float {
        val w = analysis.frame.wave
        return (w[i.coerceIn(0, w.size - 1)] / analysis.waveRef).coerceIn(-1.5f, 1.5f)
    }

    private fun drawWave(p: MilkParams, fade: Float) {
        val a = p.waveA * fade
        if (a <= 0.01f) return
        val sc = p.waveScale
        var n: Int
        var closed = false
        when (p.waveMode) {
            MilkParams.WAVE_LINE, MilkParams.WAVE_DOUBLE -> {
                n = 256
                val off = if (p.waveMode == MilkParams.WAVE_DOUBLE) p.waveRadius else 0f
                for (i in 0 until n) {
                    px[i] = i / (n - 1f)
                    py[i] = p.waveY + off + 0.25f * sc * sample(i * 4)
                }
                stroke(p, n, false, a)
                if (p.waveMode == MilkParams.WAVE_DOUBLE) {
                    for (i in 0 until n) py[i] = p.waveY - off - 0.25f * sc * sample(i * 4)
                    stroke(p, n, false, a)
                }
                return
            }
            MilkParams.WAVE_LISSAJOUS -> {
                n = 300
                for (i in 0 until n) {
                    px[i] = p.waveX + 0.3f * sc * sample(i * 3) / aspect
                    py[i] = p.waveY + 0.3f * sc * sample(i * 3 + 20)
                }
            }
            MilkParams.WAVE_SPECTRUM -> {
                n = SPEC_BANDS * 2
                closed = true
                val rot = ctx.time * 0.15f
                for (i in 0 until n) {
                    val b = if (i < SPEC_BANDS) i else n - 1 - i
                    val ang = rot + i * 2f * PI.toFloat() / n
                    val r = p.waveRadius + 0.35f * sc * spec[b]
                    px[i] = p.waveX + r * cos(ang) / aspect
                    py[i] = p.waveY + r * sin(ang)
                }
            }
            else -> {
                n = 160
                closed = true
                val rot = ctx.time * 0.2f
                val blendN = 24
                for (i in 0 until n) {
                    // Übergang am Kreisende glätten
                    var v = sample(i * 5)
                    if (i >= n - blendN) {
                        val t = (i - (n - blendN)) / blendN.toFloat()
                        v = v * (1f - t) + sample(0) * t
                    }
                    val ang = rot + i * 2f * PI.toFloat() / n
                    val r = p.waveRadius * (1f + 0.5f * sc * v)
                    px[i] = p.waveX + r * cos(ang) / aspect
                    py[i] = p.waveY + r * sin(ang)
                }
            }
        }
        stroke(p, n, closed, a)
    }

    /** Zeichnet die Punkte in px/py als dicke Linie (Dreiecksstreifen) oder als Punkte. */
    private fun stroke(p: MilkParams, n: Int, closed: Boolean, alpha: Float) {
        begin()
        val r = p.waveR; val g = p.waveG; val b = p.waveB
        if (p.waveDots) {
            for (i in 0 until n) vtx(px[i], py[i], r, g, b, alpha)
            flush(GL_POINTS, p.waveAdditive, max(1f, p.waveThick * texH / 360f))
            return
        }
        val half = p.waveThick * 0.0018f
        val total = if (closed) n + 1 else n
        for (j in 0 until total) {
            val i = j % n
            val prev = if (j == 0) (if (closed) n - 1 else 0) else (j - 1) % n
            val next = if (j + 1 >= total && !closed) i else (j + 1) % n
            // Normale in Höhen-Einheiten (seitenrichtig)
            val tx = (px[next] - px[prev]) * aspect
            val ty = py[next] - py[prev]
            val len = sqrt(tx * tx + ty * ty).coerceAtLeast(1e-6f)
            val nx = -ty / len * half / aspect
            val ny = tx / len * half
            vtx(px[i] + nx, py[i] + ny, r, g, b, alpha)
            vtx(px[i] - nx, py[i] - ny, r, g, b, alpha)
        }
        flush(GL_TRIANGLE_STRIP, p.waveAdditive)
    }

    companion object {
        private const val GRID_X = 48
        private const val GRID_Y = 32
        private const val SPEC_BANDS = 64
        private const val MAX_POINTS = 512
        private const val MAX_VERTS = 1200
        /** Automatischer Preset-Wechsel in Sekunden (0 = nur manuell). */
        val INTERVALS = floatArrayOf(10f, 20f, 40f, 0f)
    }
}
