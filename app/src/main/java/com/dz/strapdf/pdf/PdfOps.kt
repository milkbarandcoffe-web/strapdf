package com.dz.strapdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

object PdfOps {

    fun merge(inputs: List<File>, out: File) {
        val merger = PDFMergerUtility()
        inputs.forEach { merger.addSource(it) }
        out.outputStream().use { os ->
            merger.destinationStream = os
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        }
    }

    fun extractPages(src: File, pageIdx: List<Int>, out: File) {
        PDDocument.load(src).use { srcDoc ->
            PDDocument().use { newDoc ->
                pageIdx.sorted().forEach { i -> newDoc.importPage(srcDoc.getPage(i)) }
                newDoc.save(out)
            }
        }
    }

    fun pageCount(src: File): Int {
        ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { return it.pageCount }
        }
    }

    /** Rende la pagina come bitmap (scale = punti→pixel). */
    fun renderPage(src: File, index: Int, scale: Float): Bitmap {
        ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(index).use { page ->
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    val m = Matrix().apply { setScale(scale, scale) }
                    page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bmp
                }
            }
        }
    }

    /** Estrae testo; se vuoto e ocrFallback=true fa OCR pagina per pagina. */
    suspend fun toText(ctx: Context, src: File, ocrFallback: Boolean): String {
        val direct = PDDocument.load(src).use { PDFTextStripper().getText(it) }
        if (direct.isNotBlank() || !ocrFallback) return direct
        val sb = StringBuilder()
        val n = pageCount(src)
        for (i in 0 until n) {
            val bmp = renderPage(src, i, 2f)
            val t = OcrHelper.recognize(bmp)
            sb.append(t.text).append("\n\n")
            bmp.recycle()
        }
        return sb.toString()
    }
}
