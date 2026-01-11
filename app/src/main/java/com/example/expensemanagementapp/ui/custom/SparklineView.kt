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

    private val expenseData = mutableListOf<Double>()
    private val incomeData = mutableListOf<Double>()
    private var showExpense = true
    private var showIncome = true
    
    // Animation
    private var progress = 0f
    private var animator: ValueAnimator? = null

    fun setData(expense: List<Double>, income: List<Double>) {
        expenseData.clear()
        expenseData.addAll(expense)
        incomeData.clear()
        incomeData.addAll(income)
        startAnimation()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
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
        // Re-animate on toggle? Maybe just invalidate.
        // Let's re-animate if we have data for a cool effect.
        if (expenseData.isNotEmpty() || incomeData.isNotEmpty()) {
            startAnimation()
        } else {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!showExpense && !showIncome) return
        if (expenseData.isEmpty() && incomeData.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 20f
        val chartHeight = height - 2 * padding
        val chartWidth = width - 2 * padding

        // Calculate global min/max
        var maxVal = 0.0
        if (showExpense && expenseData.isNotEmpty()) maxVal = maxOf(maxVal, expenseData.maxOrNull() ?: 0.0)
        if (showIncome && incomeData.isNotEmpty()) maxVal = maxOf(maxVal, incomeData.maxOrNull() ?: 0.0)
        
        // Add 20% top padding to chart scale so lines don't hit the very top
        maxVal *= 1.2
        val minVal = 0.0 
        val range = if (maxVal == 0.0) 1.0 else maxVal

        if (showExpense && expenseData.isNotEmpty()) {
            drawSmoothPath(canvas, expenseData, chartWidth, chartHeight, padding, minVal, range, expenseColor)
        }
        if (showIncome && incomeData.isNotEmpty()) {
            drawSmoothPath(canvas, incomeData, chartWidth, chartHeight, padding, minVal, range, incomeColor)
        }
    }

    private fun drawSmoothPath(
        canvas: Canvas, 
        data: List<Double>, 
        w: Float, 
        h: Float, 
        padding: Float, 
        minVal: Double, 
        range: Double, 
        color: Int
    ) {
        if (data.isEmpty()) return

        val path = Path()
        val stepX = w / (data.size - 1).coerceAtLeast(1)

        // Points calculation
        val points = data.mapIndexed { index, value ->
            val x = padding + index * stepX
            val normalizedValue = (value - minVal) / range
            // Animate only Y
            val animatedValue = normalizedValue * progress
            val y = padding + h - (animatedValue * h).toFloat()
            x to y
        }

        if (points.isEmpty()) return

        // 1. Draw Fill (Gradient)
        // Create a closed path for filling
        val fillPath = Path()
        fillPath.moveTo(points.first().first, padding + h) // Start at bottom-left
        fillPath.lineTo(points.first().first, points.first().second) // To first point

        // Cubic Bezier for smooth curves
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i+1]
            val cx1 = p1.first + (p2.first - p1.first) / 2
            val cy1 = p1.second
            val cx2 = p1.first + (p2.first - p1.first) / 2
            val cy2 = p2.second
            
            // To use CornerPathEffect properly we usually just lineTo, but for bezier we do:
            fillPath.cubicTo(cx1, cy1, cx2, cy2, p2.first, p2.second)
        }
        
        fillPath.lineTo(points.last().first, padding + h) // To bottom-right
        fillPath.close()

        fillPaint.shader = LinearGradient(
            0f, padding, 0f, padding + h,
            color, ColorUtils.setAlphaComponent(color, 0),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)

        // 2. Draw Line
        path.moveTo(points.first().first, points.first().second)
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i+1]
            val cx1 = p1.first + (p2.first - p1.first) / 2
            val cy1 = p1.second
            val cx2 = p1.first + (p2.first - p1.first) / 2
            val cy2 = p2.second
             path.cubicTo(cx1, cy1, cx2, cy2, p2.first, p2.second)
        }
        
        linePaint.color = color
        canvas.drawPath(path, linePaint)
    }
}
