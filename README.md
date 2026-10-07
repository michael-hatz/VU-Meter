# VU-Meter

Android-App, die Musik aus der Umgebung über das eingebaute Mikrofon aufnimmt und visualisiert –
im Stil des Winamp-Classic-Spektrums und weiterer klassischer Anzeigen.

## Ansichten

| Gruppe | Ansicht | Beschreibung |
|--------|---------|--------------|
| Winamp | Winamp Classic – Spektrum | Originalpalette (viscolor.txt), Punktraster, 19/75 Bänder, Stile Normal/Feuer/Linie, fallende Spitzen |
| | Winamp Classic – Oszilloskop | Punkte, Linien oder gefüllt |
| Spektrum | LED-Spektrum | 32 Bänder, segmentiert, Peak-Hold |
| | VFD-Display | Leuchtanzeige im Stil von Hi-Fi-Geräten der 80er, mit Pegelzeile |
| Pegel | Analoge VU-Meter | VU (RMS, 300 ms) und Spitzenpegel mit Zeigerphysik |
| | PPM (BBC-Skala) | Schwarzes Zifferblatt, Skala 1–7, Rücklauf 24 dB in 2,8 s |
| | Magisches Auge | Abstimmanzeige alter Röhrenradios (rund wie EM34, Leuchtband wie EM84) |
| | Pegelmeter | LED-Pegel für RMS und Spitze mit Zahlenwert |
| | Nixie-Uhr | Uhrzeit in Ziffernröhren, Glimmpunkte im Takt, Balkenröhren für den Pegel |
| Messen | Schallpegel dB(A) | A-Bewertung, Zeitbewertung Fast, Max/Leq 60 s, Verlauf; kalibrierbar |
| | BPM-Anzeige | Tempo, Tempobezeichnung, Taktschläge, Tempoverlauf |
| | Noten & Akkorde | 12 Tonklassen, Dur-/Moll-Erkennung, leuchtende Klaviatur |
| Visualisierung | Spektrogramm | Laufender Wasserfall, ca. 8 s Verlauf |
| | Pulsar-Linien | Gestaffelte Spektrumlinien wie auf dem bekannten Pulsar-Plattencover |
| | Phosphor-Oszilloskop | Grüner Röhrenschirm, getriggert, mit Nachleuchten |
| | Demoszene-Feuer | Klassischer Feuereffekt, vom Spektrum angefacht |
| | Lichtorgel | Vier Farblampen für Bass bis Höhen (bewusst ohne Stroboskop) |
| | Sternenflug | Fluggeschwindigkeit folgt dem Bass |
| | Radial | Kreisförmiges Spektrum mit Bass-Puls und Wellenform-Ring |
| | MilkDrop-Stil | Rückkopplungs-Visualisierung wie MilkDrop (OpenGL ES 2.0), 12 eigene Presets |

**Bedienung:** Tippen oder Wischen (links/rechts) wechselt die Ansicht, langes Drücken öffnet die Einstellungen.
Im MilkDrop-Stil wechselt Wischen nach oben/unten das Preset.

**Dauerbetrieb** (Einstellungen): Diashow wechselt die Ansicht automatisch, die Uhrzeit lässt sich
einblenden, und der Ruhemodus dunkelt den Bildschirm nach längerer Stille ab (Musik oder Berührung
weckt ihn wieder). Welche Ansichten beim Durchschalten vorkommen, ist auswählbar.

### MilkDrop-Stil

Nachbau des MilkDrop-Prinzips, nicht kompatibel mit `.milk`-Dateien:

- Jedes Bild entsteht aus dem vorherigen: ein 48×32-Gitter zoomt, dreht und verzerrt es
  (Warp-Formeln wie im Original) und dunkelt es ab (`decay`).
- Darüber: Wellenform (Kreis, Linie, Lissajous, Spektrum-Ring, Doppellinie), Formen (auch mit dem
  vorherigen Bild als Textur → Kaleidoskop), Ränder, die ins Bild „einfließen“, Video-Echo.
- Presets sind Kotlin-Lambdas in `milk/Presets.kt` mit denselben Variablen wie MilkDrop
  (`zoom`, `rot`, `warp`, `bass`, `bass_att`, `q1…` usw.) – neue Presets sind schnell ergänzt.
- `bass`/`mid`/`treb` sind relativ zum gleitenden Mittel (≈ 1), daher unabhängig von der Mikrofonlautstärke.
- Interne Auflösung 1024×512 oder 512×256 (Einstellung), automatischer Wechsel alle 10/20/40 s auf einen Beat.

## Technik

- Kotlin, **keine Bibliotheken** (kein AndroidX/Compose) – Release-APK ≈ 100 KB, minSdk 21 (Android 5.0), OpenGL ES 2.0
- Aufnahme in eigenem Thread: 44,1 kHz mono, FFT 2048 (Hann), neue Analyse alle 512 Samples (~86/s)
- Bevorzugt die Audioquelle `UNPROCESSED`, sonst `VOICE_RECOGNITION`; AGC/Rauschunterdrückung werden abgeschaltet
- Zeichnen per Canvas im Displaytakt (Choreographer); Winamp über eine 76 px breite Bitmap,
  die ungefiltert hochskaliert wird; statische Teile (Skalen, LED-Raster) sind vorgerendert
- Automatische Empfindlichkeit passt sich an die Lautstärke im Raum an (abschaltbar)
- Tempo-Erkennung im Audio-Thread: spektraler Fluss → Autokorrelation über ~6 s, Beats an Onsets
  ausgerichtet und bei kurzen Lücken im erkannten Tempo weitergezählt
- Schallpegel: Standard-Kalibrierung nach dem Android-Richtwert (94 dB SPL ≈ −22 dBFS), anpassbar
- Für ältere Geräte: in den Einstellungen auf 30 FPS begrenzen

## Bauen

Voraussetzungen: JDK 17 und Android SDK (Platform 35).

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
JAVA_HOME=$HOME/Android/jdk17 ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Unit-Tests (FFT-Normierung, Tempo-Erkennung): `./gradlew testDebugUnitTest`

`assembleRelease` erzeugt ein unsigniertes APK (`app-release-unsigned.apk`), wie es F-Droid erwartet.
F-Droid-Metadaten liegen unter `fastlane/metadata/android/`.

### Demo-Modus

Ohne Mikrofon (z. B. im Emulator oder für Screenshots) lässt sich ein synthetisches Signal einspeisen:

```sh
adb shell am start -n net.schmalenstroer.vumeter/.MainActivity -e demo 1
```
