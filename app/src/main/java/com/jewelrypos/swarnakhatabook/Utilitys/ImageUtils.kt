package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Utility class for image processing and optimization
 */
object ImageUtils {
    
    /**
     * Compresses an image to reduce its file size
     * 
     * @param context Context for accessing content resolver
     * @param imageUri The URI of the image to compress
     * @param maxDimension Maximum width/height of the compressed image
     * @param quality JPEG compression quality (1-100)
     * @return Byte array of the compressed image
     */
    fun compressImage(context: Context, imageUri: Uri, maxDimension: Int = 1200, quality: Int = 75): ByteArray {
        // Get input stream from the image URI
        val inputStream = context.contentResolver.openInputStream(imageUri)
        
        // Decode bounds to calculate dimensions without loading the full image
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()
        
        // Calculate sample size for downsampling
        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxDimension)
        
        // Decode bitmap with calculated sample size
        val newInputStream = context.contentResolver.openInputStream(imageUri)
        val decodingOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565 // Uses less memory than ARGB_8888
        }
        
        val bitmap = BitmapFactory.decodeStream(newInputStream, null, decodingOptions)
        newInputStream?.close()
        
        if (bitmap == null) {
            return ByteArray(0)
        }
        
        // Further resize if necessary
        val resizedBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            resizeBitmap(bitmap, maxDimension)
        } else {
            bitmap
        }
        
        // Compress to JPEG
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        
        // Clean up if we created a new bitmap
        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle()
        }
        bitmap.recycle()
        
        return outputStream.toByteArray()
    }
    
    /**
     * Calculates appropriate sample size for BitmapFactory
     */
    fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        
        if (width > maxDimension || height > maxDimension) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfWidth / sampleSize) >= maxDimension || 
                  (halfHeight / sampleSize) >= maxDimension) {
                sampleSize *= 2
            }
        }
        
        return sampleSize
    }
    
    /**
     * Resizes a bitmap maintaining the aspect ratio
     */
    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        val ratio = min(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height
        )
        
        val newWidth = (width * ratio).roundToInt()
        val newHeight = (height * ratio).roundToInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Saves a compressed bitmap to a file
     */
    fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String, quality: Int = 75): File {
        val file = File(context.filesDir, fileName)
        
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        }
        
        return file
    }
} 