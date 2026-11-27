package com.example.scannota

data class Receipt(
    val store: String,
    val date: String,
    val total: String,
    val items: String
)

/** Menebak isi nota dari baris teks hasil OCR. Ubah di sini kalau format nota beda. */
object ReceiptParser {
    private val datePattern =
        Regex("""\b(\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}|\d{4}-\d{2}-\d{2})\b""")

    // Angka harga: 12.500 / 12,500 / 12500, boleh diakhiri ,00
    private val moneyPattern =
        Regex("""(\d{1,3}(?:[.,]\d{3})+|\d{3,})(?:[.,]\d{2})?(?!\d)""")

    private val totalWord = Regex("""\b(grand\s*total|total|jumlah)\b""", RegexOption.IGNORE_CASE)
    private val notTotal = Regex(
        """sub\s*total|total\s*(item|qty|disc|diskon|hemat|ppn|pajak)""",
        RegexOption.IGNORE_CASE
    )
    private val notItem = Regex(
        """total|jumlah|tunai|cash|kembali|ppn|pajak|diskon|hemat|debit|kredit|qris|npwp|telp|jl\.|jalan""",
        RegexOption.IGNORE_CASE
    )

    fun parse(lines: List<String>): Receipt {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }

        val store = clean.firstOrNull { line -> line.count { it.isLetter() } >= 3 } ?: ""
        val date = clean.firstNotNullOfOrNull { datePattern.find(it)?.value } ?: ""

        val totalLines = clean.filter { totalWord.containsMatchIn(it) && !notTotal.containsMatchIn(it) }
        // "Grand total" didahulukan, selain itu ambil baris total pertama yang ada angkanya
        val ordered = totalLines.sortedByDescending { it.contains("grand", ignoreCase = true) }
        val total = ordered.firstNotNullOfOrNull { lastAmount(it) } ?: ""

        val items = clean.filter { line ->
            line != store &&
                !notItem.containsMatchIn(line) &&
                !datePattern.containsMatchIn(line) &&
                line.count { it.isLetter() } >= 3 &&
                moneyPattern.containsMatchIn(line)
        }.joinToString("\n")

        return Receipt(store, date, total, items)
    }

    /** Angka terakhir di baris, hanya digit (tanpa titik/koma dan desimal). */
    private fun lastAmount(line: String): String? =
        moneyPattern.findAll(line).lastOrNull()?.groupValues?.get(1)?.filter { it.isDigit() }
}
