package com.example.scannota

import kotlin.math.cos
import kotlin.math.sin

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
 * Menggabungkan potongan teks yang sebaris. Foto nota jarang lurus, jadi posisi
 * diputar balik dulu sebesar kemiringan rata-rata sebelum dikelompokkan.
 */
object RowGrouper {
    fun group(pieces: List<Piece>): List<String> {
        if (pieces.isEmpty()) return emptyList()
        // Potongan panjang memberi sudut paling akurat
        val long = pieces.filter { it.width > it.height * 4 }.ifEmpty { pieces }
        val skew = long.map { it.angle }.sorted()[long.size / 2]
        val c = cos(skew)
        val s = sin(skew)

        val straight = pieces
            .map { it.copy(x = it.x * c + it.y * s, y = -it.x * s + it.y * c) }
            .sortedBy { it.y }

        val rows = mutableListOf<MutableList<Piece>>()
        for (piece in straight) {
            // Bandingkan dengan potongan terdekat secara mendatar, supaya sisa
            // lengkungan kertas tidak menumpuk di sepanjang baris
            val row = rows.lastOrNull()
            val near = row?.minByOrNull { kotlin.math.abs(it.x - piece.x) }
            if (near != null && piece.y - near.y < minOf(near.height, piece.height) * 0.6) {
                row.add(piece)
            } else {
                rows.add(mutableListOf(piece))
            }
        }
        return rows.map { row -> row.sortedBy { it.x }.joinToString(" ") { it.text } }
    }
}
