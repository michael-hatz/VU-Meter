package net.schmalenstroer.vumeter.milk

import android.graphics.Canvas
import net.schmalenstroer.vumeter.Analysis
import net.schmalenstroer.vumeter.viz.Visualizer

/** Platzhalter in der Ansichtsliste; gezeichnet wird von [MilkView]. */
class MilkdropViz : Visualizer("MilkDrop-Stil") {
    override val usesGl = true
    override fun draw(c: Canvas, a: Analysis, dt: Float) {}
}
