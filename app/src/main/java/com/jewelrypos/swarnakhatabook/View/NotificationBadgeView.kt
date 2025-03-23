package com.jewelrypos.swarnakhatabook.View



import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.jewelrypos.swarnakhatabook.R

/**
 * A custom view to display notification count badge
 */
class NotificationBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var count = 0
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textBounds = Rect()

    init {
        // Badge background color
        badgePaint.color = context.getColor(R.color.status_unpaid)

        // Text settings
        textPaint.color = context.getColor(R.color.white)
        textPaint.textSize = resources.getDimensionPixelSize(R.dimen.badge_text_size).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
    }

    /**
     * Set the notification count
     */
    fun setCount(count: Int) {
        this.count = count
        visibility = if (count > 0) VISIBLE else GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (count <= 0) return

        val width = width.toFloat()
        val height = height.toFloat()

        // Draw the badge circle
        val radius = Math.min(width, height) / 2f
        canvas.drawCircle(width / 2, height / 2, radius, badgePaint)

        // Draw the count text
        val text = if (count > 99) "99+" else count.toString()
        textPaint.getTextBounds(text, 0, text.length, textBounds)

        val xPos = width / 2
        val yPos = height / 2 - (textBounds.top + textBounds.bottom) / 2

        canvas.drawText(text, xPos, yPos, textPaint)
    }
}