package dev.uzair.levitate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.uzair.levitate.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val preferences by lazy { getSharedPreferences("preferences", MODE_PRIVATE) }
    private val engine = MotionEngine()
    private lateinit var sensors: MotionSensors
    private lateinit var remote: RemoteConnection
    private lateinit var controls: ControlUi
    private lateinit var haptics: Haptics
    private lateinit var session: PairingSession
    private var pairing: Pairing?
        get() = session.pairing
        set(value) { session.pairing = value }
    private var resumed = false
    private var paused = false
    private var desktopPaused = false
    private var inputReady = false
    private val scanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { connectLink(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(preferences.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        super.onCreate(savedInstanceState)
        session = ViewModelProvider(this)[PairingSession::class.java]
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        haptics = Haptics(this)
        remote = RemoteConnection { status -> runOnUiThread { showConnection(status) } }
        controls = ControlUi(binding, remote, haptics)
        sensors = MotionSensors(this, engine) { frame ->
            controls.sendMotion(frame)
            if (controls.shouldRefresh()) runOnUiThread { if (resumed) controls.showMotion(frame) }
        }
        bindPages()
        bindActions()
        controls.bindActions()
        controls.enabled = false
        PreferencesUi(binding.settingsPage, preferences, ::applyPreferences).bind()
        binding.settingsPage.sensorDescription.text = sensors.description
        applyPreferences()
        if (savedInstanceState == null && intent.dataString != null) connectLink(intent.dataString!!)
        else pairing?.let { remote.connect(it) }
    }

    private fun bindPages() {
        listOf(R.string.control, R.string.settings, R.string.guide).forEach {
            binding.tabs.addTab(binding.tabs.newTab().setText(it))
        }
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.controlPage.visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                binding.settingsPage.root.visibility = if (tab.position == 1) View.VISIBLE else View.GONE
                binding.guidePage.root.visibility = if (tab.position == 2) View.VISIBLE else View.GONE
                updateSensors()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        binding.guidePage.websiteButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://uzair.is-a.dev")))
        }
    }

    private fun bindActions() {
        binding.scanButton.setOnClickListener {
            if (pairing != null && !remote.connected) remote.connect(pairing!!) else scanQr()
        }
        binding.pasteButton.setOnClickListener { pasteLink() }
        binding.recenterButton.setOnClickListener { engine.recenter(); haptics.tick(); notice("Pointing reference reset") }
        binding.pauseButton.setOnClickListener {
            paused = !paused; controls.release(); updateSensors(); haptics.tick()
            binding.pauseButton.setText(if (paused) R.string.resume else R.string.pause)
        }
        binding.touchpadButton.setOnClickListener { controls.toggleTouchpad(); updateSensors() }
        binding.settingsPage.disconnectButton.setOnClickListener { disconnectPhone() }
        binding.modeGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                engine.selectedMode = when (id) {
                    R.id.modePoint -> ControlMode.POINTER
                    R.id.modeScroll -> ControlMode.SCROLL
                    R.id.modeSpaces -> ControlMode.WORKSPACES
                    else -> ControlMode.AUTO
                }
                controls.release()
            }
        }
    }

    private fun scanQr() {
        scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan the QR in Levitate on your laptop")
            .setBeepEnabled(false).setOrientationLocked(true).setCaptureActivity(QrActivity::class.java))
    }

    private fun pasteLink() {
        val entry = TextInputEditText(this).apply {
            hint = "levitate://pair?…"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(this).setTitle("Pairing link").setMessage("Copy the link from your Linux companion and paste it here.")
            .setView(entry).setNegativeButton("Cancel", null)
            .setPositiveButton("Connect") { _, _ -> connectLink(entry.text.toString()) }.show()
    }

    private fun connectLink(text: String) {
        try {
            pairing = Pairing.parse(text)
            paused = false
            binding.pauseButton.setText(R.string.pause)
            engine.recenter()
            remote.connect(pairing!!)
        } catch (error: IllegalArgumentException) {
            notice(error.message ?: "Invalid pairing link")
        }
    }

    private fun showConnection(status: ConnectionStatus) {
        if (isDestroyed) return
        inputReady = status.inputReady && status.connected
        desktopPaused = status.desktopPaused
        binding.connectionTitle.text = if (status.connected) "Connected · ${status.message}" else "Not connected"
        binding.connectionTitle.setTextColor(getColor(if (status.connected) R.color.connected else R.color.on_surface))
        binding.connectionDetail.text = when {
            status.connected && !status.inputReady -> "Laptop input needs setup. See Linux Preferences."
            status.desktopPaused -> "Input paused from the laptop tray."
            status.connected -> "Encrypted local connection · ${status.latencyMs} ms round trip"
            else -> status.message
        }
        binding.scanButton.setText(if (status.connected) R.string.scan_qr else if (pairing != null) R.string.reconnect else R.string.scan_qr)
        binding.scanButton.visibility = if (status.connected) View.GONE else View.VISIBLE
        binding.pasteButton.setText(if (status.connected) R.string.disconnect else R.string.paste_link)
        binding.pasteButton.setOnClickListener { if (remote.connected) disconnectPhone() else pasteLink() }
        updateSensors()
    }

    private fun applyPreferences() {
        engine.sensitivity = preferences.getFloat("sensitivity", 1400f)
        engine.naturalScroll = preferences.getBoolean("natural", true)
        engine.automaticGestures = preferences.getBoolean("gestures", true)
        haptics.enabled = preferences.getBoolean("haptics", true)
        val period = if (preferences.getBoolean("battery", false)) 33_333 else 16_667
        if (sensors.samplePeriodUs != period) { sensors.stop(); sensors.samplePeriodUs = period }
        updateSensors()
    }

    private fun updateSensors() {
        val canControl = resumed && remote.connected && inputReady && !desktopPaused
        controls.enabled = canControl && !paused
        val pointing = canControl && !paused && !controls.touchpad && binding.tabs.selectedTabPosition == 0
        if (pointing) sensors.start() else sensors.stop()
        binding.pauseButton.isEnabled = canControl
        binding.recenterButton.isEnabled = canControl
        if (canControl && preferences.getBoolean("awake", true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun disconnectPhone() {
        controls.release(); remote.disconnect(); sensors.stop()
        pairing = null
        showConnection(ConnectionStatus(false, getString(R.string.pair_description)))
    }

    override fun onResume() { super.onResume(); resumed = true; updateSensors() }
    override fun onPause() {
        resumed = false; sensors.stop(); controls.release(); controls.enabled = false
        super.onPause()
    }
    override fun onDestroy() { sensors.stop(); remote.disconnect(); super.onDestroy() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); intent.dataString?.let { connectLink(it) } }
    private fun notice(text: String) { Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show() }
}
