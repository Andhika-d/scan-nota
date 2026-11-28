package com.example.scannota

/** date kosong dan total 0 artinya tidak ditemukan di nota. */
data class Receipt(val store: String, val date: String, val total: Long, val items: List<Item>)

/** Menebak isi nota dari baris teks hasil OCR. Ubah di sini kalau format nota beda. */
object ReceiptParser {
    // 12.500 / 12,500 / 12500, boleh diakhiri ,00
    private const val MONEY = """(?:\d{1,3}(?:[.,]\d{3})+|\d{3,})(?:[.,]\d{2})?"""

    // Boleh ada satu spasi di sekitar pemisah: OCR kadang membaca "27/11/ 2026"
    private val dmy = Regex("""\b(\d{1,2}) ?([-/.]) ?(\d{1,2}) ?\2 ?(\d{4}|\d{2})\b""")
    private val ymd = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val rupiah = Regex("""\brp\.?\s*""", RegexOption.IGNORE_CASE)
    private val totalWord = Regex("""\b(grand\s*total|total|jumlah)\b""", RegexOption.IGNORE_CASE)
    private val notTotal = Regex(
        """sub\s*total|total\s*(item|qty|disc|diskon|hemat|ppn|pajak)""",
        RegexOption.IGNORE_CASE
    )
    private val notItem = Regex(
        """\b(total|subtotal|jumlah|tunai|cash|kembali|kembalian|ppn|pajak|diskon|disc|hemat|debit|kredit|qris|npwp|telp|telepon|jalan|tanggal|tgl|kasir|bayar|change|struk|invoice|trx|service|servis|tax|dpp|biaya|admin|ongkir|ongkos|pembulatan|voucher|member|poin|gopay|ovo|dana|shopeepay|tendered)\b|\bjl\.""",
        RegexOption.IGNORE_CASE
    )

    private val paidWord = Regex("""\b(tunai|cash|bayar)\b""", RegexOption.IGNORE_CASE)
    private val changeWord = Regex("""\b(kembali|kembalian|change)\b""", RegexOption.IGNORE_CASE)

    // "Nama 2 x 9.000 18.000" atau "2 x 9.000 18.000" (nama di baris sebelumnya)
    private val qtyTimes =
        Regex("""^(.*?)\s*\b(\d{1,3})\s*(?:pcs\s*)?[xX@×]\s*($MONEY)(?:\s+($MONEY))?$""")
    // "Nama 2 9.000 18.000"
    private val qtyColumns = Regex("""^(.*?)\s+(\d{1,3})\s+($MONEY)\s+($MONEY)$""")
    // Baris berisi angka saja: "4 4.500 18.000" atau "18.000", namanya di baris sebelumnya
    private val numbersOnly = Regex("""^(?:(\d{1,3})\s+)?($MONEY)(?:\s+($MONEY))?$""")
    // Huruf yang sering tertukar dengan angka, hanya di dalam bentuk harga
    private val looseMoney = Regex("""(?<![A-Za-z])[0-9][0-9OoIl]{0,2}(?:[.,][0-9OoIl]{3})+(?![A-Za-z])""")
    private val looseDate = Regex(
        """(?<![A-Za-z0-9])[0-9Oo][0-9OoIl]?([-/.])[0-9OoIl]{1,2}\1[0-9OoIl]{2,4}(?![A-Za-z0-9])"""
    )
    // Harga yang terpotong, misalnya "17.50" (angka terakhir tidak terbaca)
    private val brokenPrice = Regex("""^(.*?)\s+(\d{1,3}[.,]\d{1,2})$""")
    private val spacedThousand = Regex("""(\d)(?:\s+([.,])|([.,])\s+)(?=[0-9OoIl]{3}(?![0-9A-Za-z]))""")

    // "18x Nama 35.000 630.000" (jumlah di depan, umum di nota toko)
    private val qtyFirst =
        Regex("""^(\d{1,3})\s*[xX×]\s+(.*?)\s+($MONEY)\s+($MONEY)$""")
    // Judul tabel barang. Kalau ada, barang hanya dicari di bawahnya sampai baris total
    private val tableHeader = Regex("""\bnama\s*barang\b""", RegexOption.IGNORE_CASE)
    private val tableEnd = Regex("""^(sub\s*total|grand\s*total|total|jumlah)\b""", RegexOption.IGNORE_CASE)
    private val rowNumber = Regex("""^\d{1,3}[.)]?\s+(?=.*[A-Za-z]{2})""")

    // "Nama 18.000"
    private val priceOnly = Regex("""^(.*?)\s+($MONEY)$""")

    fun parse(lines: List<String>): Receipt {
        val clean = lines.map { tidy(it) }.filter { it.isNotEmpty() }

        val store = clean.firstOrNull { hasName(it) } ?: ""
        val date = clean.firstNotNullOfOrNull { findDate(it) } ?: ""

        // "Grand total" didahulukan, selain itu baris total pertama yang ada angkanya
        val total = clean
            .filter { totalWord.containsMatchIn(it) && !notTotal.containsMatchIn(it) }
            .sortedByDescending { it.contains("grand", ignoreCase = true) }
            .firstNotNullOfOrNull { lastAmount(it) }

        val items = repair(parseItems(clean, store), total)
        val sum = items.sumOf { it.subtotal }

        // OCR kadang salah baca satu angka. Kalau jumlah barang cocok dengan
        // (tunai - kembali), angka itu lebih bisa dipercaya daripada baris total.
        val paid = clean.firstOrNull { paidWord.containsMatchIn(it) }?.let { lastAmount(it) }
        val change = clean.firstOrNull { changeWord.containsMatchIn(it) }?.let { lastAmount(it) }
        val confirmed = paid != null && change != null && sum > 0 && paid - change == sum

        return Receipt(store, date, if (confirmed) sum else total ?: sum, items)
    }

    /** Rapikan salah baca yang umum: "Rp", "75. 000", dan "18.5OO". */
    private fun tidy(line: String): String =
        line.replace(rupiah, "")
            .replace(spacedThousand) { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] }
            .replace(looseDate) { digits(it.value) }
            .replace(looseMoney) { digits(it.value) }
            .trim()

    private fun digits(text: String) =
        text.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1')

    private fun lastAmount(line: String): Long? =
        Regex(MONEY).findAll(line).lastOrNull()?.let { amount(it.value) }

    private fun parseItems(lines: List<String>, store: String): List<Pair<Item, String>> {
        // Pasangan barang dan angka harga yang terpotong (kosong kalau harganya utuh)
        val items = mutableListOf<Pair<Item, String>>()
        var pendingName = ""
        val header = lines.indexOfFirst { tableHeader.containsMatchIn(it) }
        val numbered = header >= 0 && lines[header].startsWith("no", ignoreCase = true)
        for (raw in lines.drop(header + 1)) {
            if (header >= 0 && items.isNotEmpty() && tableEnd.containsMatchIn(raw)) break
            val line = if (numbered) raw.replaceFirst(rowNumber, "") else raw
            if (line == store || notItem.containsMatchIn(line) || findDate(line) != null) {
                pendingName = ""
                continue
            }
            val item = parseLine(line, pendingName)
            val broken = if (item == null) brokenPrice.find(line) else null
            val brokenName = broken?.let { cleanName(it.groupValues[1]) }.orEmpty()
            if (item != null) {
                items.add(item to "")
            } else if (hasName(brokenName)) {
                items.add(Item(brokenName, 1, 0) to broken!!.groupValues[2].filter { it.isDigit() })
            }
            pendingName = if (item == null && broken == null && hasName(line)) line else ""
        }
        return items
    }

    /**
     * Harga yang terpotong hanya dilengkapi kalau terbukti: cuma satu yang kosong,
     * dan selisih dengan total diawali angka yang memang terbaca. Selain itu
     * harganya dibiarkan kosong supaya diisi pengguna.
     */
    private fun repair(found: List<Pair<Item, String>>, total: Long?): List<Item> {
        val items = found.map { it.first }
        // Tanpa satu pun harga utuh, potongan angka itu kemungkinan bukan barang
        if (items.none { it.price > 0 }) return emptyList()
        val broken = found.indices.filter { found[it].second.isNotEmpty() }
        if (total == null || broken.size != 1) return items
        val index = broken.single()
        val missing = total - items.sumOf { it.subtotal }
        if (missing <= 0 || !missing.toString().startsWith(found[index].second)) return items
        return items.mapIndexed { i, item -> if (i == index) item.copy(price = missing) else item }
    }

    private fun parseLine(line: String, pendingName: String): Item? {
        numbersOnly.find(line)?.let { m ->
            if (pendingName.isEmpty()) return null
            val qty = m.groupValues[1].toIntOrNull() ?: 1
            val first = amount(m.groupValues[2])
            val last = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { amount(it) }
            val unit = when {
                last == null -> if (qty > 1 && first % qty == 0L) first / qty else first
                qty * first == last -> first
                else -> return Item(cleanName(pendingName), 1, last)
            }
            return if (qty > 0) Item(cleanName(pendingName), qty, unit) else null
        }
        qtyFirst.find(line)?.let { m ->
            val qty = m.groupValues[1].toInt()
            val name = cleanName(m.groupValues[2])
            val unit = amount(m.groupValues[3])
            if (hasName(name) && qty * unit == amount(m.groupValues[4])) return Item(name, qty, unit)
        }
        qtyTimes.find(line)?.let { m ->
            val name = cleanName(m.groupValues[1]).takeIf { hasName(it) } ?: pendingName
            val qty = m.groupValues[2].toInt()
            return if (hasName(name) && qty > 0) Item(name, qty, amount(m.groupValues[3])) else null
        }
        qtyColumns.find(line)?.let { m ->
            val name = cleanName(m.groupValues[1])
            val qty = m.groupValues[2].toInt()
            val unit = amount(m.groupValues[3])
            val sum = amount(m.groupValues[4])
            if (hasName(name) && qty > 0 && qty * unit == sum) return Item(name, qty, unit)
        }
        val m = priceOnly.find(line) ?: return null
        // Buang angka harga lain yang tersisa di ujung nama, juga angka 1 yang terbaca "l"
        val before = m.groupValues[1]
        val name = cleanName(
            before.replace(Regex("""(\s+$MONEY)+$"""), "").replace(Regex("""\s+[lI|]$"""), "")
        )
        if (!hasName(name)) return null
        val total = amount(m.groupValues[2])
        // Kolom jumlah sering tidak terbaca (cuma satu angka), hitung dari harga satuan
        val unit = Regex("""\s($MONEY)$""").find(before)?.let { amount(it.groupValues[1]) } ?: 0
        val qty = if (unit > 0 && total % unit == 0L) total / unit else 0
        return if (qty in 2..999) Item(name, qty.toInt(), unit) else Item(name, 1, total)
    }

    private fun hasName(text: String) = text.count { it.isLetter() } >= 3

    private fun cleanName(text: String) = text.trim().trimEnd(':', '-', '=').trim()

    private fun amount(text: String): Long =
        text.replace(Regex("""[.,]\d{2}$"""), "").filter { it.isDigit() }.take(12).toLong()

    private fun findDate(line: String): String? {
        ymd.find(line)?.let { m ->
            val (y, mo, d) = m.destructured
            return isoDate(y.toInt(), mo.toInt(), d.toInt())
        }
        val (d, _, mo, y) = dmy.find(line)?.destructured ?: return null
        return isoDate(if (y.length == 2) 2000 + y.toInt() else y.toInt(), mo.toInt(), d.toInt())
    }

    private fun isoDate(year: Int, month: Int, day: Int): String? {
        if (month !in 1..12) return null
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (day !in 1..days[month - 1]) return null
        return "%04d-%02d-%02d".format(year, month, day)
    }
}
