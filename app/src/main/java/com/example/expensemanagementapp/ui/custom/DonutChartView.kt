package com.example.expensemanagementapp.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.example.expensemanagementapp.data.entity.CategorySum

class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var data: List<CategorySum> = emptyList()
    private var totalAmount: Double = 0.0
    
    // Animation
    private var progress = 0f
    private var animator: ValueAnimator? = null

    // Nice Colors
    private val colors = listOf(
        androidx.core.content.ContextCompat.getColor(context, com.example.expensemanagementapp.R.color.cat_food),
        androidx.core.content.ContextCompat.getColor(context, com.example.expensemanagementapp.R.color.cat_travel),
        androidx.core.content.ContextCompat.getColor(context, com.example.expensemanagementapp.R.color.cat_bills),
        androidx.core.content.ContextCompat.getColor(context, com.example.expensemanagementapp.R.color.cat_shopping),
        androidx.core.content.ContextCompat.getColor(context, com.example.expensemanagementapp.R.color.cat_others),
        androidx.core.content.ContextCompat.getColor(context, com.example.expensemanagementapp.R.color.muted_violet),
        androidx.core.content.ContextCompat.getColor(context, com.example.expensemanagementapp.R.color.soft_gold)
    )

    init {
        bgPaint.style = Paint.Style.STROKE
        bgPaint.strokeWidth = 40f
        bgPaint.color = ContextCompat.getColor(context, android.R.color.darker_gray)
        bgPaint.alpha = 30 // Highly transparent
        
        textPaint.color = ContextCompat.getColor(context, android.R.color.black) // Default, updated on draw
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 32f
        textPaint.isFakeBoldText = true
    }

    fun interface OnCategoryClickListener {
        fun onCategoryClick(category: CategorySum)
    }

    private var listener: OnCategoryClickListener? = null

    fun setData(newData: List<CategorySum>) {
        this.data = newData
        this.totalAmount = newData.sumOf { it.total }
        startAnimation()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            interpolator = OvershootInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setOnCategoryClickListener(listener: OnCategoryClickListener) {
        this.listener = listener
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_UP && data.isNotEmpty()) {
            val cx = width / 2f
            val cy = height / 2f
            val dx = event.x - cx
            val dy = event.y - cy
            val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            
            val size = minOf(width, height) * 0.8f
            val radius = size / 2f
            
            if (dist in (radius - 50f)..(radius + 50f)) {
                var angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                var relativeAngle = angle + 90f
                if (relativeAngle < 0) relativeAngle += 360f
                
                var currentAngle = 0f
                for (item in data) {
                    val sweepAngle = ((item.total / totalAmount) * 360).toFloat()
                    if (relativeAngle >= currentAngle && relativeAngle < currentAngle + sweepAngle) {
                        listener?.onCategoryClick(item)
                        performClick()
                        break
                    }
                    currentAngle += sweepAngle
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val size = minOf(w, h) * 0.8f
        val strokeWidth = 50f
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.strokeCap = Paint.Cap.ROUND
        
        // Background track
        bgPaint.strokeWidth = strokeWidth
        val left = (w - size) / 2
        val top = (h - size) / 2
        rect.set(left, top, left + size, top + size)
        
        // Draw background circle (track)
        canvas.drawOval(rect, bgPaint)

        if (data.isEmpty()) return

        var startAngle = -90f
        // Gap between segments (in degrees)
        val gapAngle = 4f 
        
        data.forEachIndexed { index, item ->
            val sweepAngleRaw = ((item.total / totalAmount) * 360).toFloat()
            
            // Animate sweep
            val animatedSweep = sweepAngleRaw * progress
            
            // Adjust for gap (don't draw if too small)
            if (animatedSweep > gapAngle) {
                paint.color = colors[index % colors.size]
                // Draw arc with gap
                // use gap/2 at start and end
                canvas.drawArc(rect, startAngle + gapAngle/2, animatedSweep - gapAngle, false, paint)
            }
            
            // Move start angle by the RAW sweep (to keep positions consistent during animation)
            // But we actually want to animate the fill, so usually we just multiply everything by progress?
            // If we multiply everything by progress, the chart 'spins' open. 
            // If we keep startAngle static but animate sweep, gaps appear.
            // Let's do the 'spin open' style: calculate startAngle based on previous raw totals, but sweep is animated.
            startAngle += sweepAngleRaw
        }
        
    }
}
