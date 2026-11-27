package com.example.scannota

/** Data karangan untuk demo. Semua id diawali "contoh-" supaya bisa dihapus sekaligus. */
object SampleData {
    const val PREFIX = "contoh-"

    fun create(today: String): List<Nota> {
        val month = today.take(7)
        val day = today.substring(8).toInt()
        val stores = listOf("Toko Contoh Sejahtera", "Warung Contoh Berkah", "Minimarket Contoh")
        val goods = listOf(
            Item("Beras 5kg", 1, 75000), Item("Minyak Goreng 1L", 2, 18500),
            Item("Gula Pasir 1kg", 1, 17500), Item("Telur 10 butir", 1, 24000),
            Item("Sabun Mandi", 3, 4000), Item("Kopi Bubuk", 2, 12000),
            Item("Deterjen 800g", 1, 22000), Item("Air Mineral Dus", 1, 48000)
        )
        var n = 0
        return (0..5).flatMap { back ->
            val key = Format.shiftMonth(month, -back)
            (0 until 2 + (back * 2) % 3).map { i ->
                val items = (0 until 2 + (n + back) % 3).map { goods[(n * 3 + it) % goods.size] }
                val date = if (back == 0) maxOf(1, day - i * 3) else 5 + i * 7
                Nota(
                    PREFIX + n,
                    stores[n++ % stores.size],
                    "%s-%02d".format(java.util.Locale.US, key, date),
                    items.sumOf { it.subtotal },
                    items
                )
            }
        }
    }
}
