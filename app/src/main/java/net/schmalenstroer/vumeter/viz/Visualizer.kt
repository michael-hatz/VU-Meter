package net.schmalenstroer.vumeter.viz

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import net.schmalenstroer.vumeter.Analysis

abstract class Visualizer(val name: String) {
    /** true: wird nicht per Canvas, sondern von der OpenGL-Fläche dahinter gezeichnet. */
    open val usesGl: Boolean = false

    var width = 0
        private set
    var height = 0
        private set

    fun resize(w: Int, h: Int) {
        width = w
        height = h
        if (w > 0 && h > 0) onResize(w, h)
    }

    protected open fun onResize(w: Int, h: Int) {}

    /** Zeichnet ein Bild. [dt] = Sekunden seit dem letzten Bild. */
    abstract fun draw(c: Canvas, a: Analysis, dt: Float)
}

/** Kleiner Pixelpuffer, der ohne Filterung hochskaliert wird (Retro-Look, sehr günstig). */
class PixelSurface(smooth: Boolean = false) {
    var w = 0
        private set
    var h = 0
        private set
    var px = IntArray(0)
        private set
    private var bmp: Bitmap? = null
    private val paint = Paint().apply { isFilterBitmap = smooth; isDither = false; isAntiAlias = false }

    fun ensure(w: Int, h: Int) {
        if (w == this.w && h == this.h && bmp != null) return
        this.w = w
        this.h = h
        px = IntArray(w * h)
        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    fun present(c: Canvas, dst: Rect) {
        val b = bmp ?: return
        b.setPixels(px, 0, w, 0, 0, w, h)
        c.drawBitmap(b, null, dst, paint)
    }
}
