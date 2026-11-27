package com.example.scannota

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

class BarChartView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    /** Pasangan label dan nilai. Batang terakhir (bulan terpilih) diberi warna utama. */
    var data: List<Pair<String, Long>> = emptyList()
        set(value) {
            field = value
            contentDescription = value.joinToString { "${it.first} ${Format.rupiah(it.second)}" }
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11 * density
        color = ContextCompat.getColor(context, R.color.on_hero_soft)
        typeface = ResourcesCompat.getFont(context, R.font.inter_regular)
    }

    override fun onDraw(canvas: Canvas) {
        if (data.isEmpty()) return
        val max = maxOf(1L, data.maxOf { it.second })
        val slot = width.toFloat() / data.size
        val bottom = height - 20 * density
        val space = bottom - 18 * density
        data.forEachIndexed { i, (name, value) ->
            val x = slot * i + slot / 2
            val top = bottom - maxOf(3 * density, space * value / max)
            bar.color = ContextCompat.getColor(
                context, if (i == data.lastIndex) R.color.bar_now else R.color.bar_past
            )
            canvas.drawRoundRect(x - slot * 0.28f, top, x + slot * 0.28f, bottom, 4 * density, 4 * density, bar)
            if (value > 0) canvas.drawText(compact(value), x, top - 5 * density, label)
            canvas.drawText(name, x, height - 4 * density, label)
        }
    }

    private fun compact(value: Long) = when {
        value >= 1_000_000 -> "%.1fjt".format(value / 1_000_000.0)
        value >= 1_000 -> "${value / 1_000}rb"
        else -> value.toString()
    }
}
