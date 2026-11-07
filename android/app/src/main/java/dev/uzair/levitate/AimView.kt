package dev.uzair.levitate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat

/** Small live diagram, not an animation loop: redraw only when a sensor frame arrives. */
class AimView @JvmOverloads constructor(context: Context, attributes: AttributeSet? = null) : View(context, attributes) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var roll = 0f
    var touchpad = false
        set(value) { field = value; invalidate() }
    var active = false
        set(value) { field = value; invalidate() }

    fun showFrame(frame: MotionFrame) {
        roll = Math.toDegrees(frame.roll.toDouble()).toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        paint.color = ContextCompat.getColor(context, R.color.surface_container)
        paint.style = Paint.Style.FILL
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, 24 * density, 24 * density, paint)
        if (touchpad) { drawTouchpad(canvas, density); return }
        canvas.save()
        canvas.translate(width / 2f, height / 2f + 4 * density)
        canvas.rotate(roll.coerceIn(-100f, 100f))
        paint.color = ContextCompat.getColor(context, if (active) R.color.primary else R.color.outline)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        bounds.set(-25 * density, -46 * density, 25 * density, 46 * density)
        canvas.drawRoundRect(bounds, 9 * density, 9 * density, paint)
        canvas.drawLine(-8 * density, 35 * density, 8 * density, 35 * density, paint)
        canvas.drawLine(0f, -56 * density, 0f, -68 * density, paint)
        canvas.drawLine(-5 * density, -63 * density, 0f, -68 * density, paint)
        canvas.drawLine(5 * density, -63 * density, 0f, -68 * density, paint)
        canvas.restore()
    }

    private fun drawTouchpad(canvas: Canvas, density: Float) {
        paint.color = ContextCompat.getColor(context, R.color.on_surface_variant)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
        canvas.drawText("Move a finger to point", width / 2f, height / 2f - 4 * density, paint)
        canvas.drawText("Two fingers to scroll · tap to click", width / 2f, height / 2f + 20 * density, paint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
