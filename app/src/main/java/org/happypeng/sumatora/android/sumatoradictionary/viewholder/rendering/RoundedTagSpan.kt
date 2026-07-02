/* Sumatora Dictionary
        Copyright (C) 2019 Nicolas Centa

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version. */

package org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.style.ReplacementSpan

/**
 * Draws a rounded-rectangle pill around [label] with [bgColor] background and white text.
 * [sizeRatio] scales the text relative to the surrounding paint text size (e.g. 0.85 = 85%).
 * All padding/radius values are in pixels.
 */
class RoundedTagSpan(
    private val label: String,
    private val bgColor: Int,
    private val sizeRatio: Float = 0.85f,
    private val cornerRadiusPx: Float,
    private val hPadPx: Float,
    private val vPadPx: Float,
    private val trailingGapPx: Float
) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int,
                         fm: Paint.FontMetricsInt?): Int {
        val savedSize = paint.textSize
        val savedTypeface = paint.typeface
        paint.textSize = paint.textSize * sizeRatio
        paint.typeface = Typeface.DEFAULT_BOLD

        val textWidth = paint.measureText(label)
        if (fm != null) {
            paint.getFontMetricsInt(fm)
            fm.ascent = (fm.ascent - vPadPx).toInt()
            fm.descent = (fm.descent + vPadPx).toInt()
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        val total = (textWidth + 2 * hPadPx + trailingGapPx).toInt()

        paint.textSize = savedSize
        paint.typeface = savedTypeface
        return total
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int,
                      x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val savedSize = paint.textSize
        val savedTypeface = paint.typeface
        val savedColor = paint.color
        val savedStyle = paint.style
        val savedAntiAlias = paint.isAntiAlias

        paint.textSize = paint.textSize * sizeRatio
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.isAntiAlias = true

        val fm = paint.fontMetricsInt
        val chipHeight = fm.descent - fm.ascent + 2 * vPadPx
        val textWidth = paint.measureText(label)
        val chipWidth = textWidth + 2 * hPadPx
        val chipTop = y + fm.ascent - vPadPx

        paint.color = bgColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            RectF(x, chipTop, x + chipWidth, chipTop + chipHeight),
            cornerRadiusPx, cornerRadiusPx, paint
        )

        paint.color = Color.WHITE
        canvas.drawText(label, x + hPadPx, y.toFloat(), paint)

        paint.textSize = savedSize
        paint.typeface = savedTypeface
        paint.color = savedColor
        paint.style = savedStyle
        paint.isAntiAlias = savedAntiAlias
    }
}
