package com.dz.strapdf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.File

/** Coordinate in pixel bitmap, render a RENDER_SCALE (origine in alto a sinistra). */
sealed class Ann {
    class TextAnn(val x: Float, val y: Float, val text: String, val size: Float) : Ann()
    class HlAnn(val l: Float, val t: Float, val r: Float, val b: Float) : Ann()
    class InkAnn(val points: List<Pair<Float, Float>>) : Ann()
}

object Overlay {

    const val RENDER_SCALE = 2f

    fun apply(src: File, annots: Map<Int, List<Ann>>, out: File) {
        PDDocument.load(src).use { doc ->
            for ((pageIdx, list) in annots) {
                if (list.isEmpty() || pageIdx >= doc.numberOfPages) continue
                val page = doc.getPage(pageIdx)
                val hPts = page.mediaBox.height
                val f = 1f / RENDER_SCALE
                val cs = PDPageContentStream(doc, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)

                val gsHl = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.4f }
                val gsFull = PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = 1f
                    strokingAlphaConstant = 1f
                }

                for (a in list) when (a) {
                    is Ann.HlAnn -> {
                        cs.setGraphicsStateParameters(gsHl)
                        cs.setNonStrokingColor(255, 235, 59)
                        val l = a.l * f; val r = a.r * f
                        val top = hPts - a.t * f; val bot = hPts - a.b * f
                        cs.addRect(l, bot, r - l, top - bot)
                        cs.fill()
                    }
                    is Ann.TextAnn -> {
                        cs.setGraphicsStateParameters(gsFull)
                        try {
                            cs.beginText()
                            cs.setNonStrokingColor(0, 0, 0)
                            cs.setFont(PDType1Font.HELVETICA, a.size * f)
                            cs.newLineAtOffset(a.x * f, hPts - a.y * f)
                            cs.showText(OcrHelper.sanitize(a.text))
                            cs.endText()
                        } catch (_: Exception) { }
                    }
                    is Ann.InkAnn -> {
                        if (a.points.size < 2) continue
                        cs.setGraphicsStateParameters(gsFull)
                        cs.setStrokingColor(20, 20, 120)
                        cs.setLineWidth(2f)
                        val p0 = a.points.first()
                        cs.moveTo(p0.first * f, hPts - p0.second * f)
                        for (i in 1 until a.points.size) {
                            val p = a.points[i]
                            cs.lineTo(p.first * f, hPts - p.second * f)
                        }
                        cs.stroke()
                    }
                }
                cs.close()
            }
            doc.save(out)
        }
    }
}
