package dev.uzair.levitate

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.google.android.material.button.MaterialButton
import dev.uzair.levitate.databinding.ActivityMainBinding
import org.json.JSONObject
import kotlin.math.hypot

class ControlUi(private val binding: ActivityMainBinding, private val remote: RemoteConnection,
                private val haptics: Haptics) {
    var enabled = false
        set(value) {
            field = value
            listOf(binding.leftButton, binding.rightButton, binding.middleButton,
                binding.recenterButton, binding.pauseButton, binding.previousButton,
                binding.playButton, binding.nextButton).forEach { it.isEnabled = value }
            binding.aimView.active = value
        }
    var touchpad = false
        private set
    private var lastUiFrame = 0L
    private var lastMode: ControlMode? = null

    fun bindActions() {
        wireMouseButton(binding.leftButton, "left")
        wireMouseButton(binding.rightButton, "right")
        binding.middleButton.setOnClickListener { click("middle") }
        binding.previousButton.setOnClickListener { media("previous") }
        binding.playButton.setOnClickListener { media("play") }
        binding.nextButton.setOnClickListener { media("next") }
        wireTouchpad()
    }

    fun toggleTouchpad() {
        touchpad = !touchpad
        binding.aimView.touchpad = touchpad
        binding.touchpadButton.setText(if (touchpad) R.string.use_sensors else R.string.touchpad)
        binding.modeTitle.text = if (touchpad) "Touchpad" else "Air mouse"
        binding.modeHint.text = if (touchpad) "Tap to click. Two fingers to scroll." else binding.root.context.getString(R.string.point_hint)
        listOf(binding.modeAuto, binding.modePoint, binding.modeScroll, binding.modeSpaces)
            .forEach { it.isEnabled = !touchpad }
        release()
    }

    fun sendMotion(frame: MotionFrame) {
        if (!enabled || touchpad) return
        if (frame.x != 0f || frame.y != 0f) sendVector("move", frame.x, frame.y)
        if (frame.scrollX != 0f || frame.scrollY != 0f) sendVector("scroll", frame.scrollX, frame.scrollY)
        if (frame.workspace != 0) remote.send(JSONObject().put("type", "workspace").put("direction", frame.workspace))
    }

    fun showMotion(frame: MotionFrame) {
        if (!enabled || touchpad) return
        binding.aimView.showFrame(frame)
        if (lastMode == frame.mode) return
        if (lastMode != null) haptics.tick()
        lastMode = frame.mode
        binding.modeTitle.text = when (frame.mode) {
            ControlMode.SCROLL -> "Scrolling"
            ControlMode.WORKSPACES -> "Workspaces"
            else -> "Air mouse"
        }
        binding.modeHint.text = when (frame.mode) {
            ControlMode.SCROLL -> "Tilt the top up or down to scroll."
            ControlMode.WORKSPACES -> "Sweep left or right to change workspace."
            else -> binding.root.context.getString(R.string.point_hint)
        }
    }

    @Synchronized fun shouldRefresh(): Boolean {
        val now = System.nanoTime()
        if (now - lastUiFrame < 100_000_000L) return false
        lastUiFrame = now
        return true
    }

    fun release() { remote.send(JSONObject().put("type", "release")) }

    private fun sendButton(name: String, down: Boolean) {
        if (enabled || !down) remote.send(JSONObject().put("type", "button").put("button", name).put("down", down))
    }

    private fun click(name: String) {
        if (!enabled) return
        haptics.tick()
        sendButton(name, true); sendButton(name, false)
    }

    private fun media(action: String) {
        if (enabled) { haptics.tick(); remote.send(JSONObject().put("type", "media").put("action", action)) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireMouseButton(button: MaterialButton, name: String) {
        var physicalClick = false
        button.setOnClickListener { if (!physicalClick) click(name) }
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!enabled) return@setOnTouchListener false
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    view.isPressed = true
                    sendButton(name, true); haptics.tick()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    sendButton(name, false)
                    view.isPressed = false
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        physicalClick = true; view.performClick(); physicalClick = false
                    }
                }
            }
            true
        }
    }

    private fun sendVector(type: String, x: Float, y: Float) {
        remote.send(JSONObject().put("type", type).put("x", x.coerceIn(-300f, 300f))
            .put("y", y.coerceIn(-300f, 300f)))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireTouchpad() {
        var previousX = 0f; var previousY = 0f; var travel = 0f; var started = 0L
        var previousCount = 0; var usedTwoFingers = false
        val slop = ViewConfiguration.get(binding.root.context).scaledTouchSlop
        binding.aimView.setOnClickListener { if (touchpad) click("left") }
        binding.aimView.setOnTouchListener { view, event ->
            if (!touchpad || !enabled) return@setOnTouchListener false
            val x = (0 until event.pointerCount).sumOf { event.getX(it).toDouble() }.toFloat() / event.pointerCount
            val y = (0 until event.pointerCount).sumOf { event.getY(it).toDouble() }.toFloat() / event.pointerCount
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    travel = 0f; started = event.eventTime; usedTwoFingers = false
                }
                MotionEvent.ACTION_MOVE -> if (event.pointerCount == previousCount) {
                    val deltaX = x - previousX; val deltaY = y - previousY
                    travel += hypot(deltaX, deltaY)
                    if (event.pointerCount >= 2) {
                        usedTwoFingers = true
                        sendVector("scroll", -deltaX / 80f, deltaY / 80f)
                    } else sendVector("move", deltaX, deltaY)
                }
                MotionEvent.ACTION_UP -> {
                    if (!usedTwoFingers && travel < slop && event.eventTime - started < 300) view.performClick()
                    view.parent.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_CANCEL -> view.parent.requestDisallowInterceptTouchEvent(false)
            }
            previousX = x; previousY = y; previousCount = event.pointerCount
            true
        }
    }
}
