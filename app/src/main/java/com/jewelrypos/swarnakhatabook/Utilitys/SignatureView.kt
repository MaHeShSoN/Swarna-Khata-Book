package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * A custom view for capturing handwritten signatures.
 */
class SignatureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Drawing path
    private val path = Path()

    // Paint object for drawing the signature
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 8f
        color = Color.BLACK
    }

    // Coordinates for tracking drawing
    private var lastX = 0f
    private var lastY = 0f

    // Bitmap for saving signature
    private var signatureBitmap: Bitmap? = null
    private var canvas: Canvas? = null

    // Flag to track if signature has been started
    private var hasSignature = false

    init {
        // Set view background to white
        setBackgroundColor(Color.WHITE)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Create new bitmap and canvas when size changes
        signatureBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(signatureBitmap!!)
        canvas?.drawColor(Color.WHITE)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                lastX = x
                lastY = y
                hasSignature = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Calculate the distance moved
                val dx = Math.abs(x - lastX)
                val dy = Math.abs(y - lastY)

                // Only draw if moved more than touch tolerance
                if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                    // Use quadratic bezier to create smooth lines
                    path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                    lastX = x
                    lastY = y

                    // Draw to the canvas
                    canvas?.drawPath(path, paint)
                }
            }
            MotionEvent.ACTION_UP -> {
                // Connect the last point
                path.lineTo(lastX, lastY)

                // Draw to the canvas
                canvas?.drawPath(path, paint)
            }
        }

        // Trigger redraw
        invalidate()
        return true
    }

    /**
     * Check if the signature pad has a signature
     */
    fun hasSignature(): Boolean {
        return hasSignature
    }

    /**
     * Clear the signature
     */
    fun clear() {
        path.reset()
        hasSignature = false

        // Reset the bitmap
        signatureBitmap?.eraseColor(Color.WHITE)

        // Trigger redraw
        invalidate()
    }

    /**
     * Save the signature to a file
     */
    fun saveSignature(file: File): Boolean {
        if (!hasSignature) return false

        return try {
            val bitmap = signatureBitmap ?: return false

            // Convert to PNG and save to file
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Get the signature as a bitmap
     */
    fun getSignatureBitmap(): Bitmap? {
        return if (hasSignature) signatureBitmap else null
    }

    /**
     * Set stroke width
     */
    fun setStrokeWidth(width: Float) {
        paint.strokeWidth = width
    }

    /**
     * Set stroke color
     */
    fun setStrokeColor(color: Int) {
        paint.color = color
    }

    companion object {
        // Minimum distance to consider for drawing
        private const val TOUCH_TOLERANCE = 4f
    }
}