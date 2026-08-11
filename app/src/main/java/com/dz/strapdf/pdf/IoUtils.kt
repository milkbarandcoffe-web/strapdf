package com.dz.strapdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File

object IoUtils {

    fun uriToCache(ctx: Context, uri: Uri, name: String): File {
        val f = File(ctx.cacheDir, name)
        ctx.contentResolver.openInputStream(uri)!!.use { inp ->
            f.outputStream().use { out -> inp.copyTo(out) }
        }
        return f
    }

    fun cacheToUri(ctx: Context, file: File, uri: Uri) {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
            file.inputStream().use { inp -> inp.copyTo(out) }
        }
    }

    /** Decodifica con downsample (~maxSide px) e correzione rotazione EXIF. */
    fun decodeScaled(file: File, maxSide: Int): Bitmap {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        var side = maxOf(opts.outWidth, opts.outHeight)
        while (side / 2 >= maxSide) { sample *= 2; side /= 2 }
        val bmp = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IllegalArgumentException("Immagine non leggibile: ${file.name}")

        val rot = when (ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rot == 0f) return bmp
        val m = Matrix().apply { postRotate(rot) }
        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (r != bmp) bmp.recycle()
        return r
    }

    fun clearScanCache(ctx: Context) {
        ctx.cacheDir.listFiles()?.forEach {
            if (it.name.startsWith("scan_") || it.name.startsWith("out_") || it.name.startsWith("in_"))
                it.delete()
        }
    }
}
