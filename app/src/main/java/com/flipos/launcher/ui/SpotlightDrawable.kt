package com.flipos.launcher.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** A broad, borderless pool of neutral light for the focused home app action. */
class SpotlightDrawable : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var intensity = 0f
    private var radius = 0f

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val nextIntensity = when {
            state.contains(android.R.attr.state_pressed) -> PRESSED_INTENSITY
            state.contains(android.R.attr.state_focused) -> FOCUSED_INTENSITY
            else -> 0f
        }
        if (nextIntensity == intensity) return false
        intensity = nextIntensity
        // The shader's color stops include the current intensity. It may have
        // been built while this drawable was unfocused (and fully transparent),
        // so regenerate it for every state transition.
        updateShader(bounds)
        invalidateSelf()
        return true
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        updateShader(bounds)
    }

    private fun updateShader(bounds: Rect) {
        if (bounds.isEmpty) return
        radius = maxOf(bounds.width(), bounds.height()) * RADIUS_MULTIPLIER
        paint.shader = RadialGradient(
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            radius,
            intArrayOf(
                Color.argb((255 * intensity).toInt(), 255, 255, 255),
                Color.argb((255 * intensity * INNER_ALPHA).toInt(), 255, 255, 255),
                Color.argb((255 * intensity * OUTER_ALPHA).toInt(), 255, 255, 255),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, INNER_MIDPOINT, OUTER_MIDPOINT, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        if (intensity <= 0f) return
        // Draw the light itself, not a rectangle carrying a gradient. The
        // parent deliberately allows this circle to bloom beyond the icon.
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val FOCUSED_INTENSITY = 0.20f
        const val PRESSED_INTENSITY = 0.28f
        const val INNER_ALPHA = 0.32f
        const val OUTER_ALPHA = 0.04f
        const val INNER_MIDPOINT = 0.42f
        const val OUTER_MIDPOINT = 0.78f
        const val RADIUS_MULTIPLIER = 2.2f
    }
}
