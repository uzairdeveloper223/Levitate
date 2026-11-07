package dev.uzair.levitate

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

enum class ControlMode { AUTO, POINTER, SCROLL, WORKSPACES }

data class MotionFrame(val mode: ControlMode, val roll: Float, val x: Float = 0f, val y: Float = 0f,
                       val scrollX: Float = 0f, val scrollY: Float = 0f, val workspace: Int = 0)

/** Top-edge direction avoids the face-up singularity of Android's Euler azimuth. */
class MotionEngine {
    var sensitivity = 1400f
    var naturalScroll = true
    var automaticGestures = true
    var selectedMode = ControlMode.AUTO
    private var activeMode = ControlMode.POINTER
    private var pendingMode = ControlMode.POINTER
    private var pendingSince = 0L
    private var previousYaw = 0f
    private var previousPitch = 0f
    private var previousTime = 0L
    private var rollReference = 0f
    private var workspaceTravel = 0f
    private var lastWorkspace = 0L
    private var smoothX = 0f
    private var smoothY = 0f
    private var needsCenter = true

    @Synchronized fun recenter() { needsCenter = true }

    @Synchronized fun update(matrix: FloatArray, timestamp: Long): MotionFrame {
        val yaw = atan2(matrix[1], matrix[4])
        val pitch = atan2(matrix[7], hypot(matrix[1], matrix[4]))
        val absoluteRoll = atan2(-matrix[6], matrix[8])
        if (needsCenter) {
            rollReference = if (abs(absoluteRoll) < 0.6f) absoluteRoll else 0f
            previousYaw = yaw
            previousPitch = pitch
            previousTime = timestamp
            smoothX = 0f; smoothY = 0f; workspaceTravel = 0f
            needsCenter = false
        }
        val roll = wrappedAngle(absoluteRoll - rollReference)
        val nextMode = chooseMode(roll, timestamp)
        val yawDelta = wrappedAngle(yaw - previousYaw)
        val pitchDelta = pitch - previousPitch
        val gap = timestamp - previousTime
        previousYaw = yaw; previousPitch = pitch; previousTime = timestamp
        if (nextMode != activeMode || gap !in 1..100_000_000L) {
            activeMode = nextMode
            smoothX = 0f; smoothY = 0f; workspaceTravel = 0f
            return MotionFrame(activeMode, roll)
        }
        // Suppress discontinuities from a sensor reset or the top edge becoming vertical.
        if (abs(yawDelta) > 0.25f || abs(pitchDelta) > 0.25f) return MotionFrame(activeMode, roll)
        return when (activeMode) {
            ControlMode.SCROLL -> scrollFrame(roll, yawDelta, pitchDelta)
            ControlMode.WORKSPACES -> workspaceFrame(roll, yawDelta, timestamp)
            else -> pointerFrame(roll, yawDelta, pitchDelta)
        }
    }

    private fun chooseMode(roll: Float, timestamp: Long): ControlMode {
        if (selectedMode != ControlMode.AUTO) return selectedMode
        if (!automaticGestures) return ControlMode.POINTER
        val enter = Math.toRadians(55.0).toFloat()
        val leave = Math.toRadians(38.0).toFloat()
        val candidate = when {
            activeMode == ControlMode.WORKSPACES && roll > leave -> ControlMode.WORKSPACES
            activeMode == ControlMode.SCROLL && roll < -leave -> ControlMode.SCROLL
            roll > enter -> ControlMode.WORKSPACES
            roll < -enter -> ControlMode.SCROLL
            else -> ControlMode.POINTER
        }
        if (candidate != pendingMode) { pendingMode = candidate; pendingSince = timestamp }
        return if (timestamp - pendingSince >= 180_000_000L) candidate else activeMode
    }

    private fun pointerFrame(roll: Float, yaw: Float, pitch: Float): MotionFrame {
        smoothX += 0.55f * (yaw * sensitivity - smoothX)
        smoothY += 0.55f * (-pitch * sensitivity - smoothY)
        return MotionFrame(activeMode, roll, quiet(smoothX), quiet(smoothY))
    }

    private fun scrollFrame(roll: Float, yaw: Float, pitch: Float): MotionFrame {
        val direction = if (naturalScroll) 1f else -1f
        return MotionFrame(activeMode, roll, scrollX = -yaw * 22f * direction,
            scrollY = pitch * 22f * direction)
    }

    private fun workspaceFrame(roll: Float, yaw: Float, timestamp: Long): MotionFrame {
        workspaceTravel += yaw
        if (abs(workspaceTravel) < 0.18f || timestamp - lastWorkspace < 550_000_000L)
            return MotionFrame(activeMode, roll)
        val direction = if (workspaceTravel > 0) 1 else -1
        workspaceTravel = 0f
        lastWorkspace = timestamp
        return MotionFrame(activeMode, roll, workspace = direction)
    }

    private fun quiet(value: Float) = if (abs(value) < 0.12f) 0f else value.coerceIn(-300f, 300f)

    companion object {
        fun wrappedAngle(angle: Float): Float {
            val circle = (2 * Math.PI).toFloat()
            var wrapped = angle % circle
            if (wrapped > Math.PI) wrapped -= circle
            if (wrapped < -Math.PI) wrapped += circle
            return wrapped
        }
    }
}
