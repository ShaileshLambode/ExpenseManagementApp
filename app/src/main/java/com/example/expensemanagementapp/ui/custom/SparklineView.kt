package com.example.expensemanagementapp.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils

class SparklineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val expenseColor = androidx.core.content.ContextCompat.getColor(context, com.example.expensemanagementapp.R.color.expense_red)
    private val incomeColor = androidx.core.content.ContextCompat.getColor(context, com.example.expensemanagementapp.R.color.income_green)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(50f) // Simple smoothing
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1AFFFFFF") // Subtle white
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 24f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        isFakeBoldText = true
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val expenseData = mutableListOf<Double>()
    private val incomeData = mutableListOf<Double>()
    private var timestamps = mutableListOf<Long>()
    
    private var showExpense = true
    private var showIncome = true
    
    // Interaction
    private var touchX = -1f
    private var isTouching = false

    // Animation
    private var progress = 0f
    private var animator: ValueAnimator? = null

    fun setData(expense: List<Double>, income: List<Double>, dates: List<Long> = emptyList()) {
        expenseData.clear()
        expenseData.addAll(expense)
        incomeData.clear()
        incomeData.addAll(income)
        timestamps.clear()
        timestamps.addAll(dates)
        startAnimation()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setVisibility(showExp: Boolean, showInc: Boolean) {
        showExpense = showExp
        showIncome = showInc
        invalidate()
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                isTouching = true
                invalidate()
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (expenseData.isEmpty() && incomeData.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val paddingLeft = 100f
        val paddingRight = 40f
        val paddingTop = 60f
        val paddingBottom = 80f
        
        val chartHeight = height - paddingTop - paddingBottom
        val chartWidth = width - paddingLeft - paddingRight

        // Calculate global min/max
        var maxVal = 0.0
        if (showExpense && expenseData.isNotEmpty()) maxVal = maxOf(maxVal, expenseData.maxOrNull() ?: 0.0)
        if (showIncome && incomeData.isNotEmpty()) maxVal = maxOf(maxVal, incomeData.maxOrNull() ?: 0.0)
        
        maxVal = if (maxVal == 0.0) 100.0 else maxVal * 1.3
        val range = maxVal

        // 1. Draw Grid Lines (Horizontal)
        val gridCount = 4
        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..gridCount) {
            val y = paddingTop + chartHeight - (i * chartHeight / gridCount)
            canvas.drawLine(paddingLeft, y, paddingLeft + chartWidth, y, gridPaint)
            val labelVal = (i * maxVal / gridCount).toInt()
            canvas.drawText(formatValue(labelVal), paddingLeft - 10f, y + 8f, textPaint)
        }

        // 2. Draw Paths
        if (showExpense && expenseData.isNotEmpty()) {
            drawSmoothPath(canvas, expenseData, chartWidth, chartHeight, paddingLeft, paddingTop, range, expenseColor)
        }
        if (showIncome && incomeData.isNotEmpty()) {
            drawSmoothPath(canvas, incomeData, chartWidth, chartHeight, paddingLeft, paddingTop, range, incomeColor)
        }

        // 3. Draw X-Axis Labels (Date)
        if (timestamps.isNotEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            val labelCount = 4
            val step = (timestamps.size - 1) / (labelCount - 1).coerceAtLeast(1)
            val stepX = chartWidth / (timestamps.size - 1).coerceAtLeast(1)
            
            val sdf = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
            
            for (i in 0 until labelCount) {
                val index = (i * step).coerceAtMost(timestamps.size - 1)
                val x = paddingLeft + index * stepX
                val label = sdf.format(java.util.Date(timestamps[index]))
                canvas.drawText(label, x, height - 10f, textPaint)
            }
        }

        // 4. Interaction Overlay
        if (isTouching && touchX >= paddingLeft && touchX <= paddingLeft + chartWidth) {
            val dataSize = if (expenseData.isNotEmpty()) expenseData.size else incomeData.size
            if (dataSize > 1) {
                val stepX = chartWidth / (dataSize - 1)
                val index = ((touchX - paddingLeft) / stepX).toInt().coerceIn(0, dataSize - 1)
                val x = paddingLeft + index * stepX
                
                // Draw selection line
                canvas.drawLine(x, paddingTop, x, paddingTop + chartHeight, selectionPaint)
                
                // Draw markers and labels
                if (showExpense && index < expenseData.size) {
                    val y = getPointY(expenseData[index], range, chartHeight, paddingTop)
                    markerPaint.color = expenseColor
                    canvas.drawCircle(x, y, 12f, markerPaint)
                    drawValueLabel(canvas, expenseData[index], x, y - 20f, expenseColor)
                }
                if (showIncome && index < incomeData.size) {
                    val y = getPointY(incomeData[index], range, chartHeight, paddingTop)
                    markerPaint.color = incomeColor
                    canvas.drawCircle(x, y, 12f, markerPaint)
                    drawValueLabel(canvas, incomeData[index], x, y - 20f, incomeColor)
                }
            }
        }
    }

    private fun getPointY(value: Double, range: Double, h: Float, pTop: Float): Float {
        val normalizedValue = (value / range)
        val animatedValue = normalizedValue * progress
        return pTop + h - (animatedValue * h).toFloat()
    }

    private fun drawValueLabel(canvas: Canvas, value: Double, x: Float, y: Float, color: Int) {
        val label = "₹${value.toInt()}"
        val bounds = android.graphics.Rect()
        labelPaint.getTextBounds(label, 0, label.length, bounds)
        
        val bgPaint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
        }
        
        val rect = android.graphics.RectF(
            x - bounds.width()/2f - 12f,
            y - bounds.height() - 20f,
            x + bounds.width()/2f + 12f,
            y
        )
        canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
        canvas.drawText(label, x, y - 10f, labelPaint)
    }

    private fun formatValue(value: Int): String {
        return when {
            value >= 1000 -> "${value / 1000}k"
            else -> value.toString()
        }
    }

    private fun drawSmoothPath(
        canvas: Canvas, 
        data: List<Double>, 
        w: Float, 
        h: Float, 
        pLeft: Float, 
        pTop: Float,
        range: Double, 
        color: Int
    ) {
        if (data.isEmpty()) return
        val stepX = w / (data.size - 1).coerceAtLeast(1)
        val points = data.mapIndexed { index, value ->
            val x = pLeft + index * stepX
            val y = getPointY(value, range, h, pTop)
            x to y
        }
        if (points.isEmpty()) return

        // Fill
        val fillPath = Path()
        fillPath.moveTo(points.first().first, pTop + h)
        fillPath.lineTo(points.first().first, points.first().second)
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i+1]
            val cx1 = p1.first + (p2.first - p1.first) / 2
            val cy1 = p1.second
            val cx2 = p1.first + (p2.first - p1.first) / 2
            val cy2 = p2.second
            fillPath.cubicTo(cx1, cy1, cx2, cy2, p2.first, p2.second)
        }
        fillPath.lineTo(points.last().first, pTop + h)
        fillPath.close()
        fillPaint.shader = LinearGradient(0f, pTop, 0f, pTop + h, ColorUtils.setAlphaComponent(color, 80), ColorUtils.setAlphaComponent(color, 0), Shader.TileMode.CLAMP)
        canvas.drawPath(fillPath, fillPaint)

        // Line
        val linePath = Path()
        linePath.moveTo(points.first().first, points.first().second)
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i+1]
            val cx1 = p1.first + (p2.first - p1.first) / 2
            val cy1 = p1.second
            val cx2 = p1.first + (p2.first - p1.first) / 2
            val cy2 = p2.second
            linePath.cubicTo(cx1, cy1, cx2, cy2, p2.first, p2.second)
        }
        linePaint.color = color
        canvas.drawPath(linePath, linePaint)
    }
}
