package net.schmalenstroer.vumeter

import android.content.Context

/** Einstellungen, im Speicher gecacht und bei Änderung persistiert. */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun put(k: String, v: Int) = p.edit().putInt(k, v).apply()
    private fun put(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()

    var viz = p.getInt("viz", 0)
        set(v) { field = v; put("viz", v) }

    var autoGain = p.getBoolean("autoGain", true)
        set(v) { field = v; put("autoGain", v) }
    /** 0..100, nur ohne Automatik. */
    var sensitivity = p.getInt("sensitivity", 50)
        set(v) { field = v; put("sensitivity", v) }
    /** Dargestellter Dynamikbereich in dB. */
    var range = p.getInt("range", 45)
        set(v) { field = v; put("range", v) }
    /** Höhenanhebung in dB pro Oktave (Musik fällt zu hohen Frequenzen hin ab). */
    var tilt = p.getInt("tilt", 3)
        set(v) { field = v; put("tilt", v) }

    var fps60 = p.getBoolean("fps60", true)
        set(v) { field = v; put("fps60", v) }
    var showFps = p.getBoolean("showFps", false)
        set(v) { field = v; put("showFps", v) }

    // Winamp Classic
    var waThin = p.getBoolean("waThin", false)
        set(v) { field = v; put("waThin", v) }
    /** 0 = Normal, 1 = Feuer, 2 = Linie */
    var waColor = p.getInt("waColor", 0)
        set(v) { field = v; put("waColor", v) }
    var waPeaks = p.getBoolean("waPeaks", true)
        set(v) { field = v; put("waPeaks", v) }
    var waFalloff = p.getInt("waFalloff", 2)
        set(v) { field = v; put("waFalloff", v) }
    var waPeakFalloff = p.getInt("waPeakFalloff", 2)
        set(v) { field = v; put("waPeakFalloff", v) }
    /** 0 = Punkte, 1 = Linien, 2 = Gefüllt */
    var waScope = p.getInt("waScope", 1)
        set(v) { field = v; put("waScope", v) }
    // MilkDrop-Stil
    /** Index in MilkRenderer.INTERVALS (10 s, 20 s, 40 s, manuell) */
    var milkInterval = p.getInt("milkInterval", 1)
        set(v) { field = v; put("milkInterval", v) }
    var milkHighRes = p.getBoolean("milkHighRes", true)
        set(v) { field = v; put("milkHighRes", v) }

    var waOriginalSize = p.getBoolean("waOriginalSize", false)
        set(v) { field = v; put("waOriginalSize", v) }
}
