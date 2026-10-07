package net.schmalenstroer.vumeter

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import net.schmalenstroer.vumeter.audio.AudioEngine
import net.schmalenstroer.vumeter.milk.MilkView

class MainActivity : Activity() {
    private lateinit var settings: Settings
    private lateinit var engine: AudioEngine
    private lateinit var view: VisualizerView
    private lateinit var glView: MilkView
    private var askedPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        settings = Settings(this)
        engine = AudioEngine(applicationContext)
        engine.demo = intent?.getStringExtra("demo") != null
        view = VisualizerView(this, engine, settings)
        view.onOpenSettings = { SettingsDialog(this, settings, view, engine).show() }
        view.onStatusTap = { requestMic() }

        glView = MilkView(this, engine, settings)
        val renderer = glView.renderer
        renderer.onPresetChanged = { name -> view.post { view.showOverlay(name, 3f) } }
        view.onGlModeChanged = { on -> glView.visibility = if (on) View.VISIBLE else View.GONE }
        view.onGlFrame = { glView.requestRender() }
        view.onVerticalFling = { dir -> glView.queueEvent { renderer.step(dir) } }
        view.glFps = { renderer.fps }
        view.onIdleChanged = { dim ->
            window.attributes = window.attributes.apply {
                screenBrightness = if (dim) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        glView.visibility = if (view.isGl) View.VISIBLE else View.GONE

        val root = FrameLayout(this)
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        root.addView(glView, FrameLayout.LayoutParams(match, match))
        root.addView(view, FrameLayout.LayoutParams(match, match))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        glView.onResume()
        when {
            engine.demo || hasMic() -> startAudio()
            !askedPermission -> requestMic()
            else -> view.status = NO_PERMISSION
        }
        view.start()
    }

    override fun onPause() {
        view.stop()
        glView.onPause()
        engine.stop()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hasMic() = Build.VERSION.SDK_INT < 23 ||
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMic() {
        askedPermission = true
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
    }

    private fun startAudio() {
        view.status = null
        engine.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startAudio()
        else view.status = NO_PERMISSION
    }

    @Suppress("DEPRECATION")
    fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    companion object {
        private const val REQ_MIC = 1
        private const val NO_PERMISSION = "Kein Zugriff auf das Mikrofon.\nTippen, um die Berechtigung erneut anzufragen."
    }
}
