package cloud.kosch.keyswiper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import cloud.kosch.keyswiper.voice.VoicePhase
import kotlin.math.sin

/** An animated envelope driven by the recognizer's measured RMS level. */
class VoiceLevelView(context: Context, private val theme: KeyboardThemeProfile) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var phase = VoicePhase.IDLE
    private var target = 0f
    private var level = 0f
    private var recordingStart = 0L
    private val frame = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || !isShown || phase == VoicePhase.IDLE) return
            level += (target - level) * 0.25f
            invalidate()
            postOnAnimation(this)
        }
    }
    fun setLevel(value: Float) { target = value.coerceIn(0f, 1f); if (!isAttachedToWindow) level = target }
    fun setPhase(next: VoicePhase) {
        if (next == VoicePhase.LISTENING && phase != VoicePhase.LISTENING) recordingStart = SystemClock.elapsedRealtime()
        phase = next
        if (next != VoicePhase.LISTENING) target = 0f
        removeCallbacks(frame)
        if (next != VoicePhase.IDLE && isAttachedToWindow) postOnAnimation(frame)
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val time = SystemClock.elapsedRealtime() / 260.0
        val centerY = (height - 14 * density) / 2f
        val gap = 4 * density
        val count = 24
        val barWidth = ((width - (count - 1) * gap) / count).coerceAtLeast(1f)
        for (index in 0 until count) {
            val envelope = if (phase == VoicePhase.LISTENING) level * (0.35f + 0.65f * kotlin.math.abs(sin(time + index * 0.65)).toFloat())
                else if (phase == VoicePhase.IDLE) 0.03f else 0.12f + 0.12f * sin(time + index * 0.3).toFloat()
            val half = (3 * density + envelope * centerY * 0.78f).coerceAtMost(centerY.coerceAtLeast(1f))
            paint.color = if (phase == VoicePhase.IDLE) theme.border else theme.accent
            val x = index * (barWidth + gap)
            canvas.drawRoundRect(x, centerY - half, x + barWidth, centerY + half, barWidth / 2, barWidth / 2, paint)
        }
        if (phase == VoicePhase.LISTENING) {
            val seconds = (SystemClock.elapsedRealtime() - recordingStart) / 1000
            paint.color = theme.textSecondary; paint.textSize = 10 * resources.displayMetrics.scaledDensity
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("%02d:%02d".format(seconds / 60, seconds % 60), width.toFloat(), height - 2 * density, paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (phase != VoicePhase.IDLE) postOnAnimation(frame) }
    override fun onDetachedFromWindow() { removeCallbacks(frame); super.onDetachedFromWindow() }
}
