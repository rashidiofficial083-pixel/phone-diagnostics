package com.rashid.phonediagnostics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CircularGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0f
    private var centerText = "0%"
    private var subText = ""
    private var progressColor = Color.parseColor("#2196F3")
    private val bgColor = Color.parseColor("#2A2E38")

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#9AA0A6")
    }

    fun setData(percent: Int, centerLabel: String, subLabel: String, color: Int) {
        progress = percent.coerceIn(0, 100).toFloat()
        centerText = centerLabel
        subText = subLabel
        progressColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val strokeWidth = width * 0.09f
        bgPaint.strokeWidth = strokeWidth
        bgPaint.color = bgColor
        progressPaint.strokeWidth = strokeWidth
        progressPaint.color = progressColor

        val padding = strokeWidth
        val rect = RectF(padding, padding, width - padding, height - padding)

        canvas.drawArc(rect, 0f, 360f, false, bgPaint)
        val sweep = 360f * (progress / 100f)
        canvas.drawArc(rect, -90f, sweep, false, progressPaint)

        textPaint.textSize = width * 0.16f
        canvas.drawText(centerText, width / 2f, height / 2f + textPaint.textSize * 0.15f, textPaint)

        subTextPaint.textSize = width * 0.07f
        canvas.drawText(subText, width / 2f, height / 2f + textPaint.textSize * 0.65f, subTextPaint)
    }
}
