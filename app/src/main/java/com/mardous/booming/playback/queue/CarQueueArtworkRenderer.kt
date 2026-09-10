package com.mardous.booming.playback.queue

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.mardous.booming.R
import com.mardous.booming.playback.lyrics.CarLyricsArtwork
import java.io.ByteArrayOutputStream

/** Artwork is supplied to the car host as a bitmap; no Activity or Compose hierarchy is needed. */
internal class CarQueueArtworkRenderer(context: Context) {
    private val background = ContextCompat.getColor(context, R.color.surfaceContainerBlack)
    private val primary = ContextCompat.getColor(context, R.color.primary_text_dark)
    private val secondary = ContextCompat.getColor(context, R.color.secondary_text_dark)
    private val accent = ContextCompat.getColor(context, R.color.md_theme_primaryFixedDim)
    private val nowPlaying = context.getString(R.string.now_playing)
    private val upNext = context.getString(R.string.up_next)
    private val queueEnd = context.getString(R.string.car_queue_end)
    private val repeatCurrent = context.getString(R.string.car_queue_repeat_one)
    private val coverPlaceholder = ContextCompat.getDrawable(context, R.drawable.ic_album_24dp)

    fun render(card: CarQueueCard, cover: Bitmap?): CarLyricsArtwork {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(background)
            text(canvas, nowPlaying, 32f, 24f, 576, 26f, accent, bold = true)
            val coverBounds = RectF(32f, 70f, 176f, 214f)
            if (cover != null) {
                val edge = minOf(cover.width, cover.height)
                val left = (cover.width - edge) / 2
                val top = (cover.height - edge) / 2
                canvas.drawBitmap(cover, Rect(left, top, left + edge, top + edge), coverBounds,
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            } else {
                coverPlaceholder?.apply {
                    setTint(secondary)
                    bounds = Rect(56, 94, 152, 190)
                    draw(canvas)
                }
            }
            val current = card.entries.first()
            val titleHeight = text(canvas, current.title, 200f, 72f, 408, 40f, primary,
                bold = true, maxLines = 2)
            text(canvas, current.artist, 200f, 80f + titleHeight, 408, 28f, secondary)
            canvas.drawLine(32f, 238f, 608f, 238f, Paint().apply { color = secondary; alpha = 50 })
            text(canvas, upNext, 32f, 254f, 576, 26f, accent, bold = true)
            val upcoming = card.entries.drop(1)
            if (upcoming.isEmpty()) {
                text(canvas, if (card.repeatCurrent) repeatCurrent else queueEnd,
                    32f, 320f, 576, 34f, secondary, maxLines = 2)
            } else {
                upcoming.forEachIndexed { index, entry ->
                    val top = 304f + index * 76f
                    text(canvas, (index + 1).toString(), 32f, top + 4f, 40, 28f, accent)
                    text(canvas, entry.title, 88f, top, 520, 34f, primary, bold = true)
                    text(canvas, entry.artist, 88f, top + 38f, 520, 25f, secondary)
                }
            }
            val bytes = ByteArrayOutputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
            CarLyricsArtwork(bitmap, bytes)
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private fun text(
        canvas: Canvas, value: String, left: Float, top: Float, width: Int,
        size: Float, color: Int, bold: Boolean = false, maxLines: Int = 1
    ): Int {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        val checkpoint = canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restoreToCount(checkpoint)
        return layout.height
    }

    private companion object {
        const val SIZE = 640
    }
}
