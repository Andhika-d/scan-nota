package com.example.scannota

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONObject
import java.io.File
import java.util.UUID

class EditActivity : AppCompatActivity() {

    private val store by lazy { NotaStore(this) }
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private lateinit var inputStore: EditText
    private lateinit var inputDate: EditText
    private lateinit var inputTotal: EditText
    private lateinit var itemList: LinearLayout

    private var notaId = ""
    private var date = Format.today()
    private var lastSum = 0L
    private var scanning = false
    private var original = ""
    private var photo: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        inputStore = findViewById(R.id.inputStore)
        inputDate = findViewById(R.id.inputDate)
        inputTotal = findViewById(R.id.inputTotal)
        itemList = findViewById(R.id.items)

        val saved = intent.getStringExtra(EXTRA_ID)?.let { store.find(it) }
        notaId = saved?.id ?: UUID.randomUUID().toString()

        findViewById<TextView>(R.id.title).text = if (saved == null) "Nota Baru" else "Edit Nota"
        findViewById<View>(R.id.btnDelete).visibility = if (saved == null) View.GONE else View.VISIBLE

        findViewById<View>(R.id.btnBack).setOnClickListener { leave() }
        findViewById<View>(R.id.btnDelete).setOnClickListener { confirmDelete() }
        findViewById<View>(R.id.btnSave).setOnClickListener { save() }
        findViewById<View>(R.id.btnAddItem).setOnClickListener { addRow(Item("", 1, 0)) }
        findViewById<View>(R.id.txtSumHint).setOnClickListener {
            inputTotal.setText(sum().toString())
        }
        inputDate.setOnClickListener { pickDate() }
        inputTotal.doAfterTextChanged { updateHint() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })

        val draft = savedInstanceState?.getString("draft")
        notaId = savedInstanceState?.getString("id") ?: notaId
        // Foto baru dari scan, atau foto yang sudah tersimpan bersama nota
        val incoming = intent.getStringExtra(EXTRA_PHOTO)?.let { File(it) }
        photo = (incoming ?: store.photo(notaId)).takeIf { it.exists() }?.let { Photos.load(it) }
        showPreview()
        if (incoming != null && photo == null) toast("Gambar tidak bisa dibuka")

        if (draft != null) {
            fill(NotaStore.fromJson(JSONObject(draft)))
            original = savedInstanceState.getString("original", "")
        } else {
            fill(saved ?: Nota(notaId, "", date, 0, emptyList()))
            original = snapshot()
            if (incoming != null) photo?.let { scan(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("id", notaId)
        // Saat scan belum selesai, biarkan diulang setelah layar dibuat lagi
        if (!scanning) {
            outState.putString("draft", snapshot())
            outState.putString("original", original)
        }
    }

    private fun scan(bitmap: Bitmap) {
        setScanning(true)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener(this) { text ->
                val receipt = ReceiptParser.parse(Ocr.toRows(text))
                fill(
                    Nota(
                        notaId, receipt.store, receipt.date.ifEmpty { Format.today() },
                        receipt.total, receipt.items
                    )
                )
                setScanning(false)
                if (receipt.total == 0L) {
                    toast("Total tidak terbaca. Coba foto ulang atau isi manual")
                } else if (receipt.date.isEmpty()) {
                    toast("Tanggal tidak terbaca, diisi hari ini")
                }
            }
            .addOnFailureListener(this) {
                setScanning(false)
                toast("Gagal membaca nota, isi manual ya")
            }
    }

    private fun setScanning(active: Boolean) {
        scanning = active
        findViewById<View>(R.id.progress).visibility = if (active) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnSave).isEnabled = !active
        updateHint()
    }

    private fun showPreview() {
        val bitmap = photo ?: return
        findViewById<ImageView>(R.id.preview).setImageBitmap(bitmap)
        findViewById<View>(R.id.previewCard).visibility = View.VISIBLE
        findViewById<View>(R.id.preview).setOnClickListener {
            val image = ImageView(this).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                contentDescription = "Foto nota"
            }
            val scroll = ScrollView(this).apply { addView(image) }
            MaterialAlertDialogBuilder(this).setView(scroll).setPositiveButton("Tutup", null).show()
        }
    }

    private fun fill(nota: Nota) {
        notaId = nota.id
        date = nota.date
        inputStore.setText(nota.store)
        inputDate.setText(Format.date(date))
        itemList.removeAllViews()
        nota.items.forEach { addRow(it) }
        lastSum = sum()
        inputTotal.setText(if (nota.total > 0) nota.total.toString() else "")
        updateHint()
    }

    private fun addRow(item: Item) {
        val row = layoutInflater.inflate(R.layout.row_item, itemList, false)
        val name = row.findViewById<EditText>(R.id.itemName)
        val qty = row.findViewById<EditText>(R.id.itemQty)
        val price = row.findViewById<EditText>(R.id.itemPrice)
        name.setText(item.name)
        qty.setText(item.qty.toString())
        price.setText(if (item.price > 0) item.price.toString() else "")
        showSubtotal(row)
        listOf(qty, price).forEach { field ->
            field.doAfterTextChanged {
                showSubtotal(row)
                itemsChanged()
            }
        }
        row.findViewById<View>(R.id.btnRemove).setOnClickListener {
            itemList.removeView(row)
            itemsChanged()
        }
        itemList.addView(row)
    }

    private fun readRow(row: View) = Item(
        row.findViewById<EditText>(R.id.itemName).text.toString().trim(),
        row.findViewById<EditText>(R.id.itemQty).text.toString().toIntOrNull() ?: 0,
        row.findViewById<EditText>(R.id.itemPrice).text.toString().toLongOrNull() ?: 0
    )

    private fun showSubtotal(row: View) {
        row.findViewById<TextView>(R.id.itemSubtotal).text = Format.rupiah(readRow(row).subtotal)
    }

    private fun rows() = (0 until itemList.childCount).map { readRow(itemList.getChildAt(it)) }

    private fun sum() = rows().sumOf { it.subtotal }

    /** Total mengikuti jumlah barang, kecuali sudah diisi berbeda (misalnya ada pajak). */
    private fun itemsChanged() {
        val newSum = sum()
        val total = inputTotal.text.toString().toLongOrNull()
        val follow = total == null || total == lastSum
        lastSum = newSum
        if (follow) inputTotal.setText(if (newSum > 0) newSum.toString() else "")
        updateHint()
    }

    private fun updateHint() {
        val sum = sum()
        val total = inputTotal.text.toString().toLongOrNull() ?: 0
        val hint = findViewById<TextView>(R.id.txtSumHint)
        hint.visibility = if (sum > 0 && sum != total) View.VISIBLE else View.GONE
        hint.text = "Jumlah barang ${Format.rupiah(sum)}, beda dengan total. " +
            "Cek pajak/diskon, atau ketuk untuk pakai jumlah barang."
        val match = findViewById<View>(R.id.txtMatch)
        match.visibility = if (!scanning && sum > 0 && sum == total) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.txtBigTotal).text = Format.rupiah(total)
    }

    private fun current() = Nota(
        notaId,
        inputStore.text.toString().trim(),
        date,
        inputTotal.text.toString().toLongOrNull() ?: 0,
        rows()
    )

    private fun snapshot() = NotaStore.toJson(current()).toString()

    private fun pickDate() {
        val (y, m, d) = date.split("-").map { it.toInt() }
        DatePickerDialog(this, { _, year, month, day ->
            date = Format.iso(year, month + 1, day)
            inputDate.setText(Format.date(date))
        }, y, m - 1, d).show()
    }

    private fun save() {
        val nota = current()
        if (nota.store.isEmpty() || nota.total <= 0) return toast("Nama toko dan total wajib diisi")
        if (store.findDuplicate(nota) != null) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Nota ini sepertinya sudah ada")
                .setMessage("Ada nota dengan toko, tanggal, dan total yang sama.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Tetap simpan") { _, _ -> write(nota) }
                .show()
        } else {
            write(nota)
        }
    }

    private fun write(nota: Nota) {
        // Baris yang benar-benar kosong tidak ikut disimpan
        store.save(nota.copy(items = nota.items.filter { it.name.isNotEmpty() || it.price > 0 }))
        if (intent.getStringExtra(EXTRA_PHOTO) != null) {
            photo?.let { Photos.save(it, store.photo(nota.id)) }
        }
        toast("Nota tersimpan")
        setResult(RESULT_OK, Intent().putExtra(EXTRA_MONTH, nota.month))
        finish()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus nota ini?")
            .setMessage("Data yang dihapus tidak bisa dikembalikan.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                store.delete { it.id == notaId }
                finish()
            }
            .show()
    }

    private fun leave() {
        if (!scanning && snapshot() == original) return finish()
        MaterialAlertDialogBuilder(this)
            .setTitle("Keluar tanpa menyimpan?")
            .setNegativeButton("Lanjut edit", null)
            .setPositiveButton("Keluar") { _, _ -> finish() }
            .show()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_PHOTO = "photo"
        const val EXTRA_MONTH = "month"
    }
}
