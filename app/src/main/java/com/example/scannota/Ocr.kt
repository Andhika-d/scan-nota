package com.example.scannota

import com.google.mlkit.vision.text.Text
import kotlin.math.atan2
import kotlin.math.hypot

object Ocr {
    /** Gabungkan teks yang sejajar (nama barang di kiri, harga di kanan) jadi satu baris. */
    fun toRows(text: Text): List<String> = RowGrouper.group(
        text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            // Urutan titik: kiri atas, kanan atas, kanan bawah, kiri bawah
            val p = line.cornerPoints?.takeIf { it.size == 4 } ?: return@mapNotNull null
            val dx = (p[1].x - p[0].x).toDouble()
            val dy = (p[1].y - p[0].y).toDouble()
            Piece(
                text = line.text,
                x = p.sumOf { it.x } / 4.0,
                y = p.sumOf { it.y } / 4.0,
                height = hypot((p[3].x - p[0].x).toDouble(), (p[3].y - p[0].y).toDouble()),
                width = hypot(dx, dy),
                angle = atan2(dy, dx)
            )
        }
    )
}
