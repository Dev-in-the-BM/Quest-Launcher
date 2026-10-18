package quest.devinthebm.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.palette.graphics.Palette
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Masks app icons into the user's chosen [LauncherPrefs.IconShape].
 *
 * Adaptive icons are composited from their own foreground/background layers
 * (the app developer's intended look) and then clipped to the shape — the
 * shape is ours to pick, the artwork inside it stays theirs. Non-adaptive
 * (legacy) icons have no background layer of their own, so one is optionally
 * synthesized from a pale tint of the icon's dominant color.
 */
object IconShapeRenderer {

    /** Square canvas every icon is rendered onto, matching Android's standard adaptive icon size. */
    private const val CANVAS_DP = 108

    /** Legacy icons are drawn smaller than the canvas so they read clearly against their tinted disc. */
    private const val LEGACY_ICON_SCALE = 0.62f

    /**
     * Adaptive icons reserve a safe zone around their content for the system's
     * mask variance, which makes them read smaller than legacy icons at the
     * same canvas size. Drawing the layers 25% oversized (and letting the
     * canvas clip the overflow) crops into that padding so the artwork fills
     * more of the shape.
     */
    private const val ADAPTIVE_ICON_SCALE = 1.5625f

    /** How far each color channel is pushed toward white for the legacy icon background. */
    private const val LEGACY_TINT_LIGHTEN = 0.82f

    fun render(
        context: Context,
        source: Drawable,
        shape: LauncherPrefs.IconShape,
        wrapEnabled: Boolean,
        legacyBackgroundEnabled: Boolean,
    ): Drawable {
        if (!wrapEnabled || shape == LauncherPrefs.IconShape.NONE) return source
        val size = (CANVAS_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source is AdaptiveIconDrawable) {
            renderAdaptive(source, size, shape)
        } else {
            renderLegacy(source, size, shape, legacyBackgroundEnabled)
        }
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun renderAdaptive(drawable: AdaptiveIconDrawable, size: Int, shape: LauncherPrefs.IconShape): Bitmap {
        val content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(content)
        val scaledSize = (size * ADAPTIVE_ICON_SCALE).toInt()
        val offset = (size - scaledSize) / 2
        drawable.background?.let { it.setBounds(offset, offset, offset + scaledSize, offset + scaledSize); it.draw(canvas) }
        drawable.foreground?.let { it.setBounds(offset, offset, offset + scaledSize, offset + scaledSize); it.draw(canvas) }
        return applyMask(content, shape, size)
    }

    private fun renderLegacy(
        drawable: Drawable,
        size: Int,
        shape: LauncherPrefs.IconShape,
        backgroundEnabled: Boolean,
    ): Bitmap {
        val content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(content)
        if (backgroundEnabled) {
            canvas.drawColor(lightenedDominantColor(drawable))
        }
        val iconSize = (size * LEGACY_ICON_SCALE).toInt()
        val offset = (size - iconSize) / 2
        drawable.setBounds(offset, offset, offset + iconSize, offset + iconSize)
        drawable.draw(canvas)
        // Without a background disc there's nothing to mask into a shape; show the icon untouched.
        return if (backgroundEnabled) applyMask(content, shape, size) else content
    }

    /** Clips [content] to [shape], anti-aliased via a mask composite rather than canvas.clipPath. */
    private fun applyMask(content: Bitmap, shape: LauncherPrefs.IconShape, size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawPath(pathFor(shape, size), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(content, 0f, 0f, paint)
        return output
    }

    private fun lightenedDominantColor(drawable: Drawable): Int {
        val sampleSize = 48
        val sample = Bitmap.createBitmap(sampleSize, sampleSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sample)
        drawable.setBounds(0, 0, sampleSize, sampleSize)
        drawable.draw(canvas)
        val dominant = Palette.from(sample).generate().getDominantColor(Color.LTGRAY)
        return lighten(dominant, LEGACY_TINT_LIGHTEN)
    }

    private fun lighten(color: Int, amount: Float): Int {
        fun channel(c: Int) = c + ((255 - c) * amount).toInt()
        return Color.rgb(channel(Color.red(color)), channel(Color.green(color)), channel(Color.blue(color)))
    }

    private fun pathFor(shape: LauncherPrefs.IconShape, size: Int): Path {
        val s = size.toFloat()
        return when (shape) {
            LauncherPrefs.IconShape.CIRCLE -> Path().apply { addOval(0f, 0f, s, s, Path.Direction.CW) }
            LauncherPrefs.IconShape.SQUARE -> Path().apply { addRect(0f, 0f, s, s, Path.Direction.CW) }
            LauncherPrefs.IconShape.ROUNDED_SQUARE -> Path().apply {
                val r = s * 0.22f
                addRoundRect(0f, 0f, s, s, r, r, Path.Direction.CW)
            }
            LauncherPrefs.IconShape.SQUIRCLE -> squirclePath(s)
            // Never reached: render() short-circuits before masking when the shape is NONE.
            LauncherPrefs.IconShape.NONE -> Path().apply { addRect(0f, 0f, s, s, Path.Direction.CW) }
        }
    }

    /** Superellipse |x/r|^n + |y/r|^n = 1 (n=4), sampled into a polygon for a true squircle. */
    private fun squirclePath(size: Float): Path {
        val path = Path()
        val r = size / 2f
        val n = 4.0
        val steps = 90
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val ct = cos(t)
            val st = sin(t)
            val x = r + r * ct.sign() * ct.absPow(n)
            val y = r + r * st.sign() * st.absPow(n)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun Double.sign(): Float = if (this < 0) -1f else 1f
    private fun Double.absPow(n: Double): Float = kotlin.math.abs(this).pow(2.0 / n).toFloat()
}
