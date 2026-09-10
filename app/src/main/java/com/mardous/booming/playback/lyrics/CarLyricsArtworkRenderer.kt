package com.mardous.booming.playback.lyrics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import com.mardous.booming.BuildConfig
import com.mardous.booming.R
import java.io.ByteArrayOutputStream

/** Renders a high-contrast square lyrics card that stays legible after car-host downscaling. */
internal class CarLyricsArtworkRenderer(context: Context) {

    private val backgroundColor = ContextCompat.getColor(context, R.color.surfaceContainerBlack)
    private val primaryColor = ContextCompat.getColor(context, R.color.primary_text_dark)
    private val secondaryColor = ContextCompat.getColor(context, R.color.secondary_text_dark)
    private val accentColor = ContextCompat.getColor(context, R.color.md_theme_primaryFixedDim)

    fun render(primary: String, secondary: String?): CarLyricsArtwork {
        val bitmap = Bitmap.createBitmap(ARTWORK_SIZE, ARTWORK_SIZE, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(backgroundColor)
            drawAccent(canvas)

            val secondaryLayout = secondary?.takeIf(String::isNotBlank)?.let {
                createFittedLayout(
                    text = it,
                    color = secondaryColor,
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL),
                    width = CONTENT_WIDTH,
                    maxLines = SECONDARY_MAX_LINES,
                    maxHeight = SECONDARY_MAX_HEIGHT,
                    maximumTextSize = SECONDARY_TEXT_SIZE_MAX,
                    minimumTextSize = SECONDARY_TEXT_SIZE_MIN
                )
            }
            val secondaryBlockHeight = secondaryLayout?.let { TEXT_GAP + it.layout.height } ?: 0
            val primaryLayout = createFittedLayout(
                text = primary,
                color = primaryColor,
                typeface = Typeface.create("sans-serif", Typeface.BOLD),
                width = CONTENT_WIDTH,
                maxLines = PRIMARY_MAX_LINES,
                maxHeight = AVAILABLE_TEXT_HEIGHT - secondaryBlockHeight,
                maximumTextSize = PRIMARY_TEXT_SIZE_MAX,
                minimumTextSize = PRIMARY_TEXT_SIZE_MIN
            )

            val combinedHeight = primaryLayout.layout.height + secondaryBlockHeight
            var top = ((ARTWORK_SIZE - combinedHeight) / 2f)
                .coerceAtLeast(CONTENT_PADDING.toFloat())
            drawLayout(canvas, primaryLayout.layout, TEXT_LEFT.toFloat(), top)
            if (secondaryLayout != null) {
                top += primaryLayout.layout.height + TEXT_GAP
                drawLayout(canvas, secondaryLayout.layout, TEXT_LEFT.toFloat(), top)
            }

            val data = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
                output.toByteArray()
            }
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "primarySize=${primaryLayout.textSize.toInt()}, " +
                            "primaryLines=${primaryLayout.layout.lineCount}, " +
                            "primaryEllipsized=${primaryLayout.ellipsized}, " +
                            "secondarySize=${secondaryLayout?.textSize?.toInt() ?: 0}, " +
                            "secondaryLines=${secondaryLayout?.layout?.lineCount ?: 0}, " +
                            "secondaryEllipsized=${secondaryLayout?.ellipsized ?: false}, " +
                            "artworkBytes=${data.size}"
                )
            }
            CarLyricsArtwork(bitmap, data)
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private fun drawAccent(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawRoundRect(
            CONTENT_PADDING.toFloat(),
            CONTENT_PADDING.toFloat(),
            (CONTENT_PADDING + ACCENT_WIDTH).toFloat(),
            (ARTWORK_SIZE - CONTENT_PADDING).toFloat(),
            ACCENT_RADIUS,
            ACCENT_RADIUS,
            paint
        )
    }

    private fun createFittedLayout(
        text: String,
        color: Int,
        typeface: Typeface,
        width: Int,
        maxLines: Int,
        maxHeight: Int,
        maximumTextSize: Float,
        minimumTextSize: Float
    ): FittedLayout {
        var textSize = maximumTextSize
        while (true) {
            val layout = createLayout(text, color, textSize, typeface, width, maxLines)
            val ellipsized = layout.isEllipsized()
            if ((layout.height <= maxHeight && !ellipsized) || textSize <= minimumTextSize) {
                return FittedLayout(layout, textSize, ellipsized)
            }
            textSize = (textSize - TEXT_SIZE_STEP).coerceAtLeast(minimumTextSize)
        }
    }

    private fun createLayout(
        text: String,
        color: Int,
        textSize: Float,
        typeface: Typeface,
        width: Int,
        maxLines: Int
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.typeface = typeface
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false)
            .setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    private fun StaticLayout.isEllipsized(): Boolean =
        (0 until lineCount).any { getEllipsisCount(it) > 0 }

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, left: Float, top: Float) {
        val checkpoint = canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    private data class FittedLayout(
        val layout: StaticLayout,
        val textSize: Float,
        val ellipsized: Boolean
    )

    private companion object {
        const val TAG = "CarLyricsArtwork"
        const val ARTWORK_SIZE = 640
        const val CONTENT_PADDING = 40
        const val ACCENT_WIDTH = 10
        const val ACCENT_RADIUS = 5f
        const val ACCENT_TEXT_GAP = 22
        const val TEXT_RIGHT_PADDING = 40
        const val TEXT_LEFT = CONTENT_PADDING + ACCENT_WIDTH + ACCENT_TEXT_GAP
        const val CONTENT_WIDTH = ARTWORK_SIZE - TEXT_LEFT - TEXT_RIGHT_PADDING
        const val AVAILABLE_TEXT_HEIGHT = ARTWORK_SIZE - (CONTENT_PADDING * 2)
        const val TEXT_GAP = 20
        const val PRIMARY_MAX_LINES = 4
        const val SECONDARY_MAX_LINES = 2
        const val SECONDARY_MAX_HEIGHT = 124
        const val PRIMARY_TEXT_SIZE_MAX = 92f
        const val PRIMARY_TEXT_SIZE_MIN = 64f
        const val SECONDARY_TEXT_SIZE_MAX = 52f
        const val SECONDARY_TEXT_SIZE_MIN = 38f
        const val TEXT_SIZE_STEP = 2f
        const val LINE_SPACING_MULTIPLIER = 1.04f
        const val PNG_QUALITY = 100
    }
}

internal data class CarLyricsArtwork(val bitmap: Bitmap, val data: ByteArray)
