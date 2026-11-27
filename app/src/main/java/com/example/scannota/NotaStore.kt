package com.example.scannota

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Penyimpanan lokal dalam satu file JSON. Cukup untuk demo. */
class NotaStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "nota.json"))

    fun all(): List<Nota> {
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

    fun delete(predicate: (Nota) -> Boolean) = write(all().filterNot(predicate))

    private fun write(notas: List<Nota>) {
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
