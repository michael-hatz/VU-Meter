# VU-Meter

**Turn your Android phone or tablet into a music visualizer.**

VU-Meter listens to the music playing in the room through your device's built-in microphone and shows it live on screen, in 20 different views. You get the classic Winamp spectrum, analog needle meters, glowing tube displays, a sound level meter, a BPM counter and a MilkDrop-style light show.

**[⬇ Download the APK](https://github.com/michael-hatz/VU-Meter/releases/latest/download/VU-Meter.apk)** · Android 5.0+ · [Installation](#installation)

It works with any music source (a record player, the radio, a live band or a party next door) because it listens instead of connecting to a player. Put it next to your stereo and it works as a retro hi-fi display.

<p align="center">
  <img src="docs/screenshots/winamp-spectrum.jpg" width="49%" alt="Winamp Classic spectrum">
  <img src="docs/screenshots/analog-vu.jpg" width="49%" alt="Analog VU meters">
  <img src="docs/screenshots/milkdrop.jpg" width="49%" alt="MilkDrop-style visualizer">
  <img src="docs/screenshots/nixie-clock.jpg" width="49%" alt="Nixie clock">
</p>

## Highlights

- **20 views** in five groups: Winamp, spectrum, level meters, measuring tools and visualizations
- **Works with any sound source**, because it listens through the microphone
- **Simple to use**: tap or swipe to change the view, long-press for settings
- **Always-on mode** for a second screen or party display: a slideshow, a clock overlay and a sleep mode that dims the screen when the music stops
- **Adjusts to the room**: sensitivity adapts automatically to quiet and loud rooms
- **Small and private**: no ads, no tracking and no internet permission. Audio is analyzed live and is never recorded or stored.
- **Runs on old devices** too, from Android 5.0 onward, with a 30 FPS mode for slower hardware

## The views

### Winamp

| | |
|---|---|
| ![Winamp Classic spectrum](docs/screenshots/winamp-spectrum.jpg) | ![Winamp Classic oscilloscope](docs/screenshots/winamp-oscilloscope.jpg) |
| **Winamp Classic – Spectrum**<br>The famous Winamp 2 spectrum analyzer with its original colors and dot grid. Choose 19 wide or 75 thin bars, the *Normal*, *Fire* or *Line* color style, and falling peak markers. | **Winamp Classic – Oscilloscope**<br>The waveform as Winamp drew it, using dots, lines or a filled curve. Both Winamp views can also be shown at their original tiny 76×16 pixel size, scaled up. |

### Spectrum

| | |
|---|---|
| ![LED spectrum](docs/screenshots/led-spectrum.jpg) | ![VFD display](docs/screenshots/vfd-display.jpg) |
| **LED Spectrum**<br>A 32-band segmented LED analyzer with peak hold. | **VFD Display**<br>A glowing vacuum fluorescent display like those on 1980s hi-fi equipment, with a level bar. |

### Level meters

| | |
|---|---|
| ![Analog VU meters](docs/screenshots/analog-vu.jpg) | ![BBC PPM](docs/screenshots/bbc-ppm.jpg) |
| **Analog VU Meters**<br>Two backlit needle meters for average (VU) and peak level, with realistic needle movement. | **PPM (BBC scale)**<br>The black broadcast peak meter with its 1–7 scale, as used in radio studios. |
| ![Magic eye](docs/screenshots/magic-eye.jpg) | ![Level meter](docs/screenshots/level-meter.jpg) |
| **Magic Eye**<br>The green tuning indicator tube from old valve radios, as a round eye and as a glowing bar. | **Level Meter**<br>LED bars for average and peak level with dB readouts. |
| ![Nixie clock](docs/screenshots/nixie-clock.jpg) | |
| **Nixie Clock**<br>The time in glowing Nixie tubes. The separator dots blink to the beat and bar tubes show the level. | |

### Measuring tools

| | |
|---|---|
| ![Sound level meter](docs/screenshots/sound-level.jpg) | ![BPM display](docs/screenshots/bpm.jpg) |
| **Sound Level dB(A)**<br>A sound level meter with A-weighting, maximum and 60-second average, and a history graph. You can calibrate it against a reference meter. | **BPM Display**<br>Detects the tempo of the music, names the tempo (e.g. *Allegro*), shows the beats as they happen and draws the tempo over the last 3 minutes. |
| ![Notes and chords](docs/screenshots/notes-chords.jpg) | |
| **Notes & Chords**<br>Shows which of the 12 notes are sounding, detects the major or minor chord being played and lights up the matching keys on a piano keyboard. | |

### Visualizations

| | |
|---|---|
| ![Spectrogram](docs/screenshots/spectrogram.jpg) | ![Pulsar lines](docs/screenshots/pulsar-lines.jpg) |
| **Spectrogram**<br>A scrolling waterfall of the last ~8 seconds of sound. | **Pulsar Lines**<br>Stacked spectrum lines in the style of a famous album cover. |
| ![Phosphor oscilloscope](docs/screenshots/phosphor-scope.jpg) | ![Demoscene fire](docs/screenshots/fire.jpg) |
| **Phosphor Oscilloscope**<br>A green CRT oscilloscope screen with afterglow. | **Demoscene Fire**<br>The classic demoscene fire effect, driven by the music. |
| ![Light organ](docs/screenshots/light-organ.jpg) | ![Starfield](docs/screenshots/starfield.jpg) |
| **Light Organ**<br>Four colored lamps for bass, low mids, high mids and treble. It never strobes. | **Starfield**<br>A flight through space that speeds up with the bass. |
| ![Radial spectrum](docs/screenshots/radial.jpg) | ![MilkDrop-style visualizer](docs/screenshots/milkdrop-2.jpg) |
| **Radial**<br>A circular spectrum with a pulsing bass core and a waveform ring. | **MilkDrop Style**<br>A flowing, hypnotic light show inspired by Winamp's MilkDrop, with 12 built-in presets. Presets change automatically on a beat, or you can swipe up or down to switch them yourself. |

## How to use it

| Action | What it does |
|---|---|
| **Tap** or **swipe left/right** | Next / previous view |
| **Swipe up/down** (MilkDrop view) | Next / previous preset |
| **Long-press** | Open the settings |

The first time you start the app, Android asks for permission to use the microphone. The app only needs it to analyze the sound and does not record anything.

## Settings

![Settings](docs/screenshots/settings.jpg)

Long-press anywhere to open the settings:

- **Views**: jump straight to a view, and choose which views appear when you tap or swipe through them
- **Always-on mode**
  - *Slideshow*: switch views automatically every 1, 2 or 5 minutes
  - *Show clock*: show the time on top of any view
  - *Sleep on silence*: dim the screen after 2, 5 or 15 minutes of silence. Music or a touch wakes it up again.
- **Sensitivity**: automatic adjustment (on by default), manual sensitivity, dynamic range and treble boost
- **Winamp Classic**: bar width, color style, peaks, fall-off speeds, oscilloscope style and original size
- **Sound level**: calibration for the dB(A) meter
- **MilkDrop style**: preset change interval (10, 20 or 40 seconds, or manual only) and render resolution
- **Performance**: 60 or 30 FPS, and an optional FPS counter

> The app's interface is currently in German.

## Requirements

- An Android phone or tablet running **Android 5.0** or newer
- A built-in **microphone**
- OpenGL ES 2.0 support (almost every Android device has it)
- No internet connection or account

The app works in portrait and landscape. Landscape looks best on most views.

## Installation

### Install the APK

1. **[Download VU-Meter.apk](https://github.com/michael-hatz/VU-Meter/releases/latest/download/VU-Meter.apk)** onto your device (older versions are on the [Releases](https://github.com/michael-hatz/VU-Meter/releases) page).
2. Open the file. If Android asks, allow installing apps from this source.
3. Start **VU-Meter** and allow microphone access.

### Build it yourself

You need JDK 17 and the Android SDK (platform 35).

```sh
git clone https://github.com/michael-hatz/VU-Meter.git
cd VU-Meter
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`./gradlew assembleRelease` builds an unsigned release APK in the format F-Droid expects.

### Demo mode

To try the app without a microphone (for example in the emulator), start it with a built-in test signal:

```sh
adb shell am start -n net.schmalenstroer.vumeter/.MainActivity -e demo 1
```

## Tips

- Place the device near the speakers but not right in front of them. Very loud sound can overload the microphone.
- Readings from the sound level meter depend on your device's microphone. If accuracy matters, calibrate it against a real sound level meter in the settings.
- For a permanent display, connect a charger and enable the slideshow and sleep mode.
