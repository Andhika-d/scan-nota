package com.example.scannota

data class Item(val name: String, val qty: Int, val price: Long) {
    val subtotal: Long get() = qty * price
}

/** date disimpan dalam format yyyy-MM-dd supaya mudah diurutkan dan dikelompokkan. */
data class Nota(
    val id: String,
    val store: String,
    val date: String,
    val total: Long,
    val items: List<Item>
) {
    val month: String get() = date.take(7)
}
