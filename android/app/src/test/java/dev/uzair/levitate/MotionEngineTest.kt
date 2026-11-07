package dev.uzair.levitate

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class MotionEngineTest {
    /** Rotation about world Z (negative for rightward aim), then device Y (roll). */
    private fun matrix(yaw: Float = 0f, roll: Float = 0f, pitch: Float = 0f): FloatArray {
        val z = -yaw
        val cz = cos(z); val sz = sin(z); val cy = cos(roll); val sy = sin(roll)
        val cx = cos(pitch); val sx = sin(pitch)
        return floatArrayOf(cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
            sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
            -sy, cy * sx, cy * cx)
    }

    @Test fun faceUpPointerHasCorrectDirectionsAndNoStationaryDrift() {
        val engine = MotionEngine()
        var time = 1_000_000_000L
        engine.update(matrix(), time)
        val right = engine.update(matrix(yaw = 0.01f), time + 16_667_000)
        assertTrue(right.x > 0)
        val up = engine.update(matrix(yaw = 0.01f, pitch = 0.01f), time + 33_334_000)
        assertTrue(up.y < 0)
        time += 33_334_000
        repeat(120) { time += 16_667_000; engine.update(matrix(yaw = 0.01f, pitch = 0.01f), time) }
        val resting = engine.update(matrix(yaw = 0.01f, pitch = 0.01f), time + 16_667_000)
        assertEquals(0f, resting.x, 0f)
        assertEquals(0f, resting.y, 0f)
    }

    @Test fun automaticModesRequireDwellAndUseOppositeEdges() {
        val engine = MotionEngine()
        engine.update(matrix(), 1_000_000_000)
        assertEquals(ControlMode.POINTER, engine.update(matrix(roll = 1.2f), 1_016_000_000).mode)
        var frame = engine.update(matrix(roll = 1.2f), 1_216_000_000)
        assertEquals(ControlMode.WORKSPACES, frame.mode)
        engine.update(matrix(roll = -1.2f), 1_232_000_000)
        frame = engine.update(matrix(roll = -1.2f), 1_432_000_000)
        assertEquals(ControlMode.SCROLL, frame.mode)
    }

    @Test fun yawWrapAndSensorGapsDoNotJumpThePointer() {
        val engine = MotionEngine().apply { selectedMode = ControlMode.POINTER }
        engine.update(matrix(yaw = 3.13f), 1_000_000_000)
        val wrapped = engine.update(matrix(yaw = -3.13f), 1_016_000_000)
        assertTrue(wrapped.x in 0f..40f)
        val resumed = engine.update(matrix(yaw = -2.9f), 5_000_000_000)
        assertEquals(0f, resumed.x, 0f)
    }

    @Test fun workspaceSweepsAreDeliberateAndDebounced() {
        val engine = MotionEngine().apply { selectedMode = ControlMode.WORKSPACES }
        var time = 1_000_000_000L
        engine.update(matrix(), time)
        var switches = 0
        for (step in 1..30) {
            time += 16_667_000
            if (engine.update(matrix(yaw = step * 0.01f), time).workspace != 0) switches++
        }
        assertEquals(1, switches)
    }

    @Test fun manualModeOverridesRollAndRecenterDoesNotGenerateMotion() {
        val engine = MotionEngine().apply { selectedMode = ControlMode.SCROLL }
        engine.update(matrix(), 1_000_000_000)
        engine.recenter()
        val centered = engine.update(matrix(yaw = 0.5f, roll = 1.2f), 1_016_000_000)
        assertEquals(ControlMode.SCROLL, centered.mode)
        assertEquals(0f, centered.scrollY, 0f)
    }
}
