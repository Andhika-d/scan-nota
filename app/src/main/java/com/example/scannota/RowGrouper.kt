package com.example.scannota

import kotlin.math.abs
import kotlin.math.tan

/** Satu potongan teks: titik tengah, tinggi huruf, lebar, dan kemiringan (radian). */
data class Piece(
    val text: String,
    val x: Double,
    val y: Double,
    val height: Double,
    val width: Double,
    val angle: Double
)

/**
 * Menggabungkan potongan teks yang sebaris. Foto nota jarang lurus: bisa miring,
 * dan kalau difoto menyerong, kemiringan baris atas berbeda dengan baris bawah.
 */
object RowGrouper {
    fun group(pieces: List<Piece>): List<String> {
        if (pieces.isEmpty()) return emptyList()
        val angleAt = angleModel(pieces)
        val center = pieces.map { it.x }.average()
        // "level" = posisi tegak setelah kemiringan di tempat itu dibatalkan
        val leveled = pieces
            .map { it to it.y - (it.x - center) * tan(angleAt(it.y)) }
            .sortedBy { it.second }

        val rows = mutableListOf<MutableList<Pair<Piece, Double>>>()
        for ((piece, level) in leveled) {
            // Bandingkan dengan potongan terdekat secara mendatar, supaya sisa
            // lengkungan kertas tidak menumpuk di sepanjang baris
            val row = rows.lastOrNull()
            val near = row?.minByOrNull { abs(it.first.x - piece.x) }
            if (near != null && level - near.second < minOf(near.first.height, piece.height) * 0.6) {
                row.add(piece to level)
            } else {
                rows.add(mutableListOf(piece to level))
            }
        }
        return rows.map { row -> row.map { it.first }.sortedBy { it.x }.joinToString(" ") { it.text } }
    }

    /** Perkiraan kemiringan pada ketinggian tertentu (garis tahan pencilan Theil-Sen). */
    private fun angleModel(pieces: List<Piece>): (Double) -> Double {
        // Potongan panjang memberi sudut paling akurat
        val long = pieces.filter { it.width > it.height * 4 }.ifEmpty { pieces }
        val spread = long.maxOf { it.y } - long.minOf { it.y }
        val slopes = mutableListOf<Double>()
        if (long.size >= 5) {
            for (i in long.indices) for (j in i + 1 until long.size) {
                val dy = long[j].y - long[i].y
                if (abs(dy) > spread / 4) slopes.add((long[j].angle - long[i].angle) / dy)
            }
        }
        val slope = if (slopes.isEmpty()) 0.0 else median(slopes)
        val base = median(long.map { it.angle - slope * it.y })
        // Di luar jangkauan potongan panjang, jangan menebak lebih jauh
        val top = long.minOf { it.y }
        val bottom = long.maxOf { it.y }
        return { y -> base + slope * y.coerceIn(top - spread * 0.15, bottom + spread * 0.15) }
    }

    private fun median(values: List<Double>) = values.sorted()[values.size / 2]
}
