package com.jewelrypos.swarnakhatabook.View

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.jewelrypos.swarnakhatabook.R
import java.lang.Math.abs
import java.lang.Math.cos
import java.lang.Math.sin
import kotlin.math.pow
import kotlin.math.sign

class SquircleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var squircleColor = Color.BLUE
    private var squircleFactor = 4.0 // Default squircle factor (n=4.0 is a true squircle)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        // Read custom attributes from XML
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.SquircleView,
            0, 0
        ).apply {
            try {
                squircleColor = getColor(R.styleable.SquircleView_squircleColor, Color.BLUE)
                squircleFactor = getFloat(R.styleable.SquircleView_squircleFactor, 4.0f).toDouble()
            } finally {
                recycle()
            }
        }

        paint.color = squircleColor
    }

    /**
     * Set the squircle factor (n value)
     * 2.0 = Circle-like
     * 4.0 = True squircle
     * >4.0 = More square-like
     */
    fun setSquircleFactor(factor: Double) {
        this.squircleFactor = factor
        invalidate() // Redraw the view
    }

    fun setSquircleColor(color: Int) {
        this.squircleColor = color
        paint.color = color
        invalidate() // Redraw the view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val path = Path()
        val steps = 120 // Higher steps = smoother curve

        // Generate the squircle path
        for (i in 0..steps) {
            val theta = 2 * Math.PI * i / steps
            // Note: We use 2.0 to ensure double division
            val x = (w / 2) + (w / 2) * abs(cos(theta)).pow(2.0 / squircleFactor) * sign(cos(theta))
            val y = (h / 2) + (h / 2) * abs(sin(theta)).pow(2.0 / squircleFactor) * sign(sin(theta))

            if (i == 0) {
                path.moveTo(x.toFloat(), y.toFloat())
            } else {
                path.lineTo(x.toFloat(), y.toFloat())
            }
        }

        path.close()
        canvas.drawPath(path, paint)
    }
}