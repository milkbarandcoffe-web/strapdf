package com.dz.strapdf.pdf

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import java.io.File

object SearchablePdfBuilder {

    private const val PAGE_W_PTS = 595f // larghezza A4

    /**
     * Crea un PDF dalle immagini. Ogni pagina = immagine originale;
     * se ocr=true aggiunge sotto un livello di testo invisibile alle coordinate rilevate.
     */
    suspend fun build(ctx: Context, images: List<File>, ocr: Boolean, out: File) {
        val doc = PDDocument()
        try {
            for (imgFile in images) {
                val bmp = IoUtils.decodeScaled(imgFile, 2200)
                val scale = PAGE_W_PTS / bmp.width
                val hPts = bmp.height * scale
                val page = PDPage(PDRectangle(PAGE_W_PTS, hPts))
                doc.addPage(page)

                val pdImg = JPEGFactory.createFromImage(doc, bmp)
                val cs = PDPageContentStream(doc, page)
                cs.drawImage(pdImg, 0f, 0f, PAGE_W_PTS, hPts)

                if (ocr) {
                    val text = OcrHelper.recognize(bmp)
                    for (block in text.textBlocks) for (line in block.lines) {
                        val box = line.boundingBox ?: continue
                        val txt = OcrHelper.sanitize(line.text).trim()
                        if (txt.isEmpty()) continue
                        val fs = (box.height() * scale * 0.75f).coerceIn(4f, 60f)
                        try {
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA, fs)
                            cs.setRenderingMode(RenderingMode.NEITHER)
                            cs.newLineAtOffset(box.left * scale, hPts - box.bottom * scale)
                            cs.showText(txt)
                            cs.endText()
                        } catch (_: Exception) { /* riga non codificabile: salta */ }
                    }
                }
                cs.close()
                bmp.recycle()
            }
            doc.save(out)
        } finally {
            doc.close()
        }
    }
}
