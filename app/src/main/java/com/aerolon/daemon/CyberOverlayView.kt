package com.aerolon.daemon

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withSave
import kotlin.math.max

class CyberOverlayView(context: Context) : View(context) {

    private var statusText = ""
    private var isListening = false
    private var isTransitioning = false
    private var targetRms = 10f
    private var currentRms = 10f

    private var edgeProgress = 0f
    private var edgeAlpha = 255
    private var fadeDelay = 40
    private var transitionProgress = 0f

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#D500F9".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 15f
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.SOLID)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#00E5FF".toColorInt()
        textSize = 55f
        textAlign = Paint.Align.CENTER
        setShadowLayer(15f, 0f, 0f, "#D500F9".toColorInt())
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#00E5FF".toColorInt()
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.SOLID)
    }

    private var barHeights = floatArrayOf(10f, 10f, 10f, 10f, 10f)

    fun startAnimation() {
        isListening = true
        isTransitioning = false
        statusText = ""
        edgeProgress = 0f
        edgeAlpha = 255
        fadeDelay = 40
        transitionProgress = 0f
        postInvalidateOnAnimation()
    }

    fun updateText(text: String) {
        statusText = text
        isListening = false
        isTransitioning = true
        postInvalidateOnAnimation()
    }

    fun updateVolume(rmsdB: Float) {
        val normalized = max(10f, (rmsdB + 2f) * 15f)
        targetRms = normalized
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        var needsInvalidate = false

        if (isListening || isTransitioning) {
            if (edgeProgress < 1f) {
                edgeProgress += 0.04f
                needsInvalidate = true
            } else if (!isListening) {
                if (fadeDelay > 0) {
                    fadeDelay--
                    needsInvalidate = true
                } else if (edgeAlpha > 0) {
                    edgeAlpha -= 8
                    if (edgeAlpha < 0) edgeAlpha = 0
                    needsInvalidate = true
                }
            }
        }

        if (edgeProgress > 0 && edgeAlpha > 0) {
            edgePaint.alpha = edgeAlpha
            val drawH = h * edgeProgress
            canvas.drawLine(10f, h, 10f, h - drawH, edgePaint)
            canvas.drawLine(w - 10f, 0f, w - 10f, drawH, edgePaint)
            canvas.drawLine(0f, h - 10f, w * edgeProgress, h - 10f, edgePaint)
        }

        if (isListening) {
            currentRms += (targetRms - currentRms) * 0.3f
            val startX = w / 2 - 60f

            barHeights[0] = currentRms * 0.5f
            barHeights[1] = currentRms * 0.8f
            barHeights[2] = currentRms * 1.2f
            barHeights[3] = currentRms * 0.8f
            barHeights[4] = currentRms * 0.5f

            wavePaint.alpha = 255
            for (i in 0..4) {
                val cx = startX + (i * 30f)
                canvas.drawRoundRect(
                    cx, h - 120f - barHeights[i],
                    cx + 10f, h - 120f + barHeights[i],
                    8f, 8f, wavePaint
                )
            }
            needsInvalidate = true
        } else if (isTransitioning) {
            transitionProgress += 0.05f
            if (transitionProgress > 1f) transitionProgress = 1f

            val waveAlpha = ((1f - transitionProgress) * 255).toInt()
            val textAlpha = (transitionProgress * 255).toInt()

            if (waveAlpha > 0) {
                val startX = w / 2 - 60f
                wavePaint.alpha = waveAlpha
                for (i in 0..4) {
                    val cx = startX + (i * 30f)
                    canvas.drawRoundRect(
                        cx, h - 120f - barHeights[i],
                        cx + 10f, h - 120f + barHeights[i],
                        8f, 8f, wavePaint
                    )
                }
            }

            if (statusText.isNotEmpty()) {
                textPaint.alpha = textAlpha
                val scale = 0.5f + (0.5f * transitionProgress)
                canvas.withSave {
                    translate(w / 2, h - 100f)
                    scale(scale, scale)
                    drawText(statusText, 0f, 0f, textPaint)
                }
            }

            if (transitionProgress < 1f || edgeAlpha > 0) {
                needsInvalidate = true
            } else {
                isTransitioning = false
            }
        } else if (statusText.isNotEmpty()) {
            textPaint.alpha = 255
            canvas.drawText(statusText, w / 2, h - 100f, textPaint)
            if (edgeAlpha > 0) {
                needsInvalidate = true
            }
        }

        if (needsInvalidate) {
            postInvalidateOnAnimation()
        }
    }
}