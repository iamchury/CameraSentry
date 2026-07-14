package com.chury.camerasentry.comparison

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.chury.camerasentry.R
import java.io.File
import kotlin.math.abs

class ImageDifferenceCalculator(private val context: Context) {
    fun differencePercent(previousPath: String, currentPath: String): Double {
        val previous = decodeScaled(previousPath)
        val current = decodeScaled(currentPath)
        val width = minOf(previous.width, current.width)
        val height = minOf(previous.height, current.height)
        var total = 0.0
        for (y in 0 until height) {
            for (x in 0 until width) {
                total += abs(luma(previous.getPixel(x, y)) - luma(current.getPixel(x, y))) / 255.0
            }
        }
        previous.recycle()
        current.recycle()
        return (total / (width * height)) * 100.0
    }

    private fun decodeScaled(path: String): Bitmap {
        require(File(path).exists()) { context.getString(R.string.image_file_missing, path) }
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight)
        options.inJustDecodeBounds = false
        val decoded = BitmapFactory.decodeFile(path, options)
            ?: error(context.getString(R.string.image_file_unreadable, path))
        return Bitmap.createScaledBitmap(decoded, SAMPLE_SIZE, SAMPLE_SIZE, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while ((width / sample) > SAMPLE_SIZE * 2 && (height / sample) > SAMPLE_SIZE * 2) {
            sample *= 2
        }
        return sample
    }

    private fun luma(pixel: Int): Int =
        ((Color.red(pixel) * 0.299) + (Color.green(pixel) * 0.587) + (Color.blue(pixel) * 0.114)).toInt()

    private companion object {
        const val SAMPLE_SIZE = 64
    }
}
