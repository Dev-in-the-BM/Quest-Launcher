package quest.devinthebm.launcher.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

/**
 * Squircle (superellipse, n=4) highlight shown behind the focused/pressed/
 * selected app drawer icon. Drawn as a path rather than a rounded-rect shape
 * so the corners read as a continuous curve like the icon shapes themselves,
 * and is invisible outside those states so it behaves like the selector
 * drawable it replaces.
 */
class SquircleDrawable(@ColorInt color: Int = 0) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }
    private val path = Path()
    private var visible = false

    fun setColor(@ColorInt color: Int) {
        if (paint.color != color) {
            paint.color = color
            invalidateSelf()
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        buildPath(bounds)
    }

    private fun buildPath(bounds: Rect) {
        path.reset()
        if (bounds.isEmpty) return
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val rx = bounds.width() / 2f
        val ry = bounds.height() / 2f
        for (i in 0..SEGMENTS) {
            val t = (i.toFloat() / SEGMENTS) * (2 * Math.PI).toFloat()
            val x = cx + rx * sign(cos(t)) * abs(cos(t)).pow(HALF_EXPONENT)
            val y = cy + ry * sign(sin(t)) * abs(sin(t)).pow(HALF_EXPONENT)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val newVisible = state.contains(android.R.attr.state_pressed) ||
            state.contains(android.R.attr.state_focused) ||
            state.contains(android.R.attr.state_selected)
        if (newVisible == visible) return false
        visible = newVisible
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        if (!visible) return
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val SEGMENTS = 64
        const val HALF_EXPONENT = 0.5f
    }
}

private fun Float.pow(exp: Float): Float = Math.pow(this.toDouble(), exp.toDouble()).toFloat()
