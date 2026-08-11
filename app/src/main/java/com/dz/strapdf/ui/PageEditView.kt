package com.dz.strapdf.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.dz.strapdf.pdf.Ann

class PageEditView(ctx: Context, attrs: AttributeSet?) : View(ctx, attrs) {

    enum class Mode { NONE, TEXT, HL, INK }

    var mode = Mode.NONE
    var onTextRequested: ((Float, Float) -> Unit)? = null

    private var bitmap: Bitmap? = null
    private var anns: MutableList<Ann> = mutableListOf()

    private var fitScale = 1f
    private var offX = 0f
    private var offY = 0f

    // stato gesto corrente
    private var dragL = 0f; private var dragT = 0f
    private var dragR = 0f; private var dragB = 0f
    private var dragging = false
    private var inkPts = mutableListOf<Pair<Float, Float>>()

    private val pBmp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pHl = Paint().apply { color = Color.argb(100, 255, 235, 59); style = Paint.Style.FILL }
    private val pInk = Paint().apply {
        color = Color.rgb(20, 20, 120); style = Paint.Style.STROKE
        strokeWidth = 4f; isAntiAlias = true
    }
    private val pTxt = Paint().apply { color = Color.BLACK; isAntiAlias = true }

    fun setPage(bmp: Bitmap, annotations: MutableList<Ann>) {
        bitmap?.recycle()
        bitmap = bmp
        anns = annotations
        computeFit()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        computeFit()
    }

    private fun computeFit() {
        val b = bitmap ?: return
        if (width == 0 || height == 0) return
        fitScale = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
        offX = (width - b.width * fitScale) / 2f
        offY = (height - b.height * fitScale) / 2f
    }

    private fun toBmpX(x: Float) = ((x - offX) / fitScale)
    private fun toBmpY(y: Float) = ((y - offY) / fitScale)

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        canvas.save()
        canvas.translate(offX, offY)
        canvas.scale(fitScale, fitScale)
        canvas.drawBitmap(b, 0f, 0f, pBmp)

        for (a in anns) when (a) {
            is Ann.HlAnn -> canvas.drawRect(a.l, a.t, a.r, a.b, pHl)
            is Ann.TextAnn -> {
                pTxt.textSize = a.size
                canvas.drawText(a.text, a.x, a.y, pTxt)
            }
            is Ann.InkAnn -> {
                if (a.points.size > 1) {
                    val path = Path()
                    path.moveTo(a.points[0].first, a.points[0].second)
                    for (i in 1 until a.points.size)
                        path.lineTo(a.points[i].first, a.points[i].second)
                    canvas.drawPath(path, pInk)
                }
            }
        }
        // anteprima gesto in corso
        if (dragging && mode == Mode.HL)
            canvas.drawRect(minOf(dragL, dragR), minOf(dragT, dragB),
                maxOf(dragL, dragR), maxOf(dragT, dragB), pHl)
        if (mode == Mode.INK && inkPts.size > 1) {
            val path = Path()
            path.moveTo(inkPts[0].first, inkPts[0].second)
            for (i in 1 until inkPts.size) path.lineTo(inkPts[i].first, inkPts[i].second)
            canvas.drawPath(path, pInk)
        }
        canvas.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bitmap == null || mode == Mode.NONE) return false
        val bx = toBmpX(e.x); val by = toBmpY(e.y)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> when (mode) {
                Mode.TEXT -> { onTextRequested?.invoke(bx, by); return true }
                Mode.HL -> { dragging = true; dragL = bx; dragT = by; dragR = bx; dragB = by }
                Mode.INK -> { inkPts = mutableListOf(bx to by) }
                else -> {}
            }
            MotionEvent.ACTION_MOVE -> when (mode) {
                Mode.HL -> { dragR = bx; dragB = by; invalidate() }
                Mode.INK -> { inkPts.add(bx to by); invalidate() }
                else -> {}
            }
            MotionEvent.ACTION_UP -> when (mode) {
                Mode.HL -> {
                    dragging = false
                    if (kotlin.math.abs(dragR - dragL) > 4 && kotlin.math.abs(dragB - dragT) > 4)
                        anns.add(Ann.HlAnn(minOf(dragL, dragR), minOf(dragT, dragB),
                            maxOf(dragL, dragR), maxOf(dragT, dragB)))
                    invalidate()
                }
                Mode.INK -> {
                    if (inkPts.size > 1) anns.add(Ann.InkAnn(inkPts.toList()))
                    inkPts = mutableListOf()
                    invalidate()
                }
                else -> {}
            }
        }
        return true
    }
}
