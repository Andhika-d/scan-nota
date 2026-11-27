package com.example.scannota

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Penyimpanan lokal: satu file JSON per akun, foto nota di folder "foto". */
class NotaStore(private val context: Context) {
    private val file: AtomicFile
        get() {
            val owner = Accounts(context).current()?.phone ?: "tamu"
            return AtomicFile(File(context.filesDir, "nota-$owner.json"))
        }

    fun photo(id: String) = File(context.filesDir, "foto/$id.jpg")

    fun all(): List<Nota> {
        val file = file
        if (!file.baseFile.exists()) return emptyList()
        val array = JSONArray(String(file.readFully()))
        return (0 until array.length())
            .map { fromJson(array.getJSONObject(it)) }
            .sortedByDescending { it.date }
    }

    fun find(id: String) = all().firstOrNull { it.id == id }

    fun save(vararg notas: Nota) {
        val ids = notas.map { it.id }.toSet()
        write(all().filterNot { it.id in ids } + notas)
    }

    fun delete(predicate: (Nota) -> Boolean) {
        val (removed, kept) = all().partition(predicate)
        write(kept)
        removed.forEach { photo(it.id).delete() }
    }

    /** Nota lain dengan toko, tanggal, dan total yang sama. */
    fun findDuplicate(nota: Nota) = all().firstOrNull {
        it.id != nota.id && it.date == nota.date && it.total == nota.total &&
            it.store.equals(nota.store, ignoreCase = true)
    }

    private fun write(notas: List<Nota>) {
        val file = file
        val bytes = JSONArray(notas.map { toJson(it) }).toString().toByteArray()
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    companion object {
        fun toJson(nota: Nota): JSONObject = JSONObject()
            .put("id", nota.id)
            .put("store", nota.store)
            .put("date", nota.date)
            .put("total", nota.total)
            .put("items", JSONArray(nota.items.map {
                JSONObject().put("name", it.name).put("qty", it.qty).put("price", it.price)
            }))

        fun fromJson(json: JSONObject): Nota {
            val items = json.getJSONArray("items")
            return Nota(
                json.getString("id"),
                json.getString("store"),
                json.getString("date"),
                json.getLong("total"),
                (0 until items.length()).map {
                    val item = items.getJSONObject(it)
                    Item(item.getString("name"), item.getInt("qty"), item.getLong("price"))
                }
            )
        }
    }
}
