package net.schmalenstroer.vumeter

import android.app.AlertDialog
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import net.schmalenstroer.vumeter.audio.AudioEngine

/** Einstellungsdialog, programmatisch aufgebaut. Änderungen wirken sofort. */
class SettingsDialog(
    private val act: MainActivity,
    private val s: Settings,
    private val view: VisualizerView,
    private val engine: AudioEngine,
) {
    fun show() {
        val builder = AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        val ctx = builder.context
        val dp = act.resources.displayMetrics.density
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt())
        }
        val wrap = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        fun header(t: String) {
            root.addView(TextView(ctx).apply {
                text = t
                setTypeface(typeface, Typeface.BOLD)
                textSize = 16f
                setPadding(0, (16 * dp).toInt(), 0, (4 * dp).toInt())
            }, wrap)
        }

        fun label(t: String) = TextView(ctx).apply { text = t; textSize = 14f }

        fun choice(t: String, options: Array<String>, sel: Int, cb: (Int) -> Unit) {
            root.addView(label(t), wrap)
            val sp = Spinner(ctx)
            sp.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, options)
            sp.setSelection(sel.coerceIn(0, options.size - 1))
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = cb(pos)
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            root.addView(sp, wrap)
        }

        fun toggle(t: String, v: Boolean, cb: (Boolean) -> Unit) {
            root.addView(Switch(ctx).apply {
                text = t
                isChecked = v
                textSize = 14f
                setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
                setOnCheckedChangeListener { _, c -> cb(c) }
            }, wrap)
        }

        fun slider(t: String, min: Int, max: Int, v: Int, fmt: (Int) -> String, cb: (Int) -> Unit) {
            val l = label("$t: ${fmt(v)}")
            root.addView(l, wrap)
            root.addView(SeekBar(ctx).apply {
                this.max = max - min
                progress = v - min
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        l.text = "$t: ${fmt(p + min)}"
                        cb(p + min)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }, wrap)
        }

        val speeds = arrayOf("Sehr langsam", "Langsam", "Mittel", "Schnell", "Sehr schnell")

        header("Darstellung")
        val names = view.visualizers.map { it.name }.toTypedArray()
        choice("Ansicht", names, view.currentIndex) {
            if (it != view.currentIndex) view.select(it)
        }
        root.addView(Button(ctx).apply {
            text = "Ansichten zum Durchschalten auswählen …"
            setOnClickListener {
                val checked = BooleanArray(names.size) { names[it] !in s.hiddenViz }
                AlertDialog.Builder(ctx)
                    .setTitle("Ansichten beim Tippen/Wischen")
                    .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
                    .setPositiveButton("OK") { _, _ ->
                        val hidden = names.filterIndexed { i, _ -> !checked[i] }.toSet()
                        // Mindestens eine Ansicht muss bleiben
                        s.hiddenViz = if (hidden.size >= names.size) emptySet() else hidden
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }, wrap)

        header("Dauerbetrieb")
        choice("Diashow (Ansicht automatisch wechseln)", arrayOf("Aus", "Jede Minute", "Alle 2 Minuten", "Alle 5 Minuten"),
            s.slideshow) { s.slideshow = it }
        toggle("Uhrzeit einblenden", s.showClock) { s.showClock = it }
        choice("Ruhemodus bei Stille (Bildschirm dunkel)", arrayOf("Aus", "Nach 2 Minuten", "Nach 5 Minuten", "Nach 15 Minuten"),
            s.idleMode) { s.idleMode = it }

        header("Empfindlichkeit")
        toggle("Automatisch anpassen", s.autoGain) { s.autoGain = it }
        slider("Empfindlichkeit (ohne Automatik)", 0, 100, s.sensitivity, { "$it %" }) { s.sensitivity = it }
        slider("Dynamikbereich", 20, 80, s.range, { "$it dB" }) { s.range = it }
        slider("Höhenanhebung", 0, 6, s.tilt, { "$it dB/Oktave" }) { s.tilt = it }

        header("Winamp Classic")
        choice("Balken", arrayOf("Breit (19 Bänder)", "Schmal (75 Bänder)"), if (s.waThin) 1 else 0) { s.waThin = it == 1 }
        choice("Farbstil", arrayOf("Normal", "Feuer", "Linie"), s.waColor) { s.waColor = it }
        toggle("Spitzen anzeigen", s.waPeaks) { s.waPeaks = it }
        choice("Balken-Abfall", speeds, s.waFalloff) { s.waFalloff = it }
        choice("Spitzen-Abfall", speeds, s.waPeakFalloff) { s.waPeakFalloff = it }
        choice("Oszilloskop", arrayOf("Punkte", "Linien", "Gefüllt"), s.waScope) { s.waScope = it }
        choice("Größe", arrayOf("Vollbild", "Original (76×16 Pixel, skaliert)"), if (s.waOriginalSize) 1 else 0) {
            s.waOriginalSize = it == 1
        }

        header("Schallpegel")
        slider("Kalibrierung (dB SPL bei 0 dBFS)", 80, 140, s.splOffset, { "$it dB" }) { s.splOffset = it }

        header("MilkDrop-Stil")
        choice("Preset-Wechsel", arrayOf("Alle 10 Sekunden", "Alle 20 Sekunden", "Alle 40 Sekunden", "Nur manuell"),
            s.milkInterval) { s.milkInterval = it }
        choice("Auflösung", arrayOf("Hoch (1024×512)", "Niedrig (512×256, schneller)"), if (s.milkHighRes) 0 else 1) {
            s.milkHighRes = it == 0
        }

        header("Leistung")
        toggle("60 FPS (aus: 30 FPS für ältere Geräte)", s.fps60) { s.fps60 = it }
        toggle("FPS anzeigen", s.showFps) { s.showFps = it }

        root.addView(label(
            "\nAudioquelle: ${engine.sourceName}\n\n" +
                "Bedienung: Tippen oder Wischen wechselt die Ansicht, langes Drücken öffnet diese Einstellungen. " +
                "Im MilkDrop-Stil wechselt Wischen nach oben/unten das Preset."
        ).apply { alpha = 0.7f }, wrap)

        builder.setTitle("Einstellungen")
            .setView(ScrollView(ctx).apply { addView(root) })
            .setPositiveButton("Schließen", null)
            .setOnDismissListener { act.hideSystemUi() }
            .show()
    }
}
