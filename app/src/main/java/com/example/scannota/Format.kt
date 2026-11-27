package com.example.scannota

import java.util.Calendar

object Format {
    private val months = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    fun rupiah(value: Long): String =
        "Rp " + value.toString().reversed().chunked(3).joinToString(".").reversed()

    /** "2026-11-27" -> "27 November 2026" */
    fun date(iso: String): String {
        val (y, m, d) = iso.split("-").map { it.toInt() }
        return "$d ${months[m - 1]} $y"
    }

    /** "2026-11" -> "November 2026" */
    fun month(key: String): String = "${months[key.substring(5).toInt() - 1]} ${key.take(4)}"

    fun shortMonth(key: String): String = months[key.substring(5).toInt() - 1].take(3)

    fun today(): String = Calendar.getInstance().let {
        iso(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }

    fun iso(year: Int, month: Int, day: Int) = "%04d-%02d-%02d".format(java.util.Locale.US, year, month, day)

    fun shiftMonth(key: String, delta: Int): String {
        val index = key.take(4).toInt() * 12 + key.substring(5).toInt() - 1 + delta
        return "%04d-%02d".format(java.util.Locale.US, index / 12, index % 12 + 1)
    }
}
