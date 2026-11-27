package com.example.scannota

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

object Photos {
    /** Buka foto dengan ukuran wajar dan arah yang benar. Null kalau bukan gambar. */
    fun load(file: File, maxSide: Int = 2560): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        val side = maxOf(options.outWidth, options.outHeight)
        if (side <= 0) return null
        var sample = 1
        while (side / sample > maxSide) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            file.path, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val exif = ExifInterface(file)
        val matrix = Matrix()
        if (exif.isFlipped) matrix.postScale(-1f, 1f)
        matrix.postRotate(exif.rotationDegrees.toFloat())
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun save(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }
}
