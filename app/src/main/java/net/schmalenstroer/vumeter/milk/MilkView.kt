package net.schmalenstroer.vumeter.milk

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import net.schmalenstroer.vumeter.Settings
import net.schmalenstroer.vumeter.audio.AudioEngine

/** GL-Fläche hinter der normalen View; gerendert wird nur auf Anforderung (Takt kommt von der VisualizerView). */
@SuppressLint("ViewConstructor")
class MilkView(ctx: Context, engine: AudioEngine, s: Settings) : GLSurfaceView(ctx) {
    val renderer = MilkRenderer(engine, s)

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}
