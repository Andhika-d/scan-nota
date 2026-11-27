package com.example.scannota

import com.google.mlkit.vision.text.Text

object Ocr {
    /** Gabungkan teks yang sejajar (nama barang di kiri, harga di kanan) jadi satu baris. */
    fun toRows(text: Text): List<String> {
        val lines = text.textBlocks.flatMap { it.lines }
            .filter { it.boundingBox != null }
            .sortedBy { it.boundingBox!!.centerY() }

        val rows = mutableListOf<MutableList<Text.Line>>()
        for (line in lines) {
            val box = line.boundingBox!!
            val first = rows.lastOrNull()?.first()?.boundingBox
            if (first != null && box.centerY() - first.centerY() < first.height() * 0.6) {
                rows.last().add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }
        return rows.map { row ->
            row.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text }
        }
    }
}
