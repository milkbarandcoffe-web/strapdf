package com.dz.strapdf.pdf

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrHelper {

    private val client by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognize(bmp: Bitmap): Text = suspendCancellableCoroutine { cont ->
        client.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }

    /** Solo caratteri codificabili in WinAnsi (Helvetica); gli altri diventano spazio. */
    fun sanitize(s: String): String = buildString {
        for (c in s) append(if (c.code in 32..126 || c.code in 160..255) c else ' ')
    }
}
