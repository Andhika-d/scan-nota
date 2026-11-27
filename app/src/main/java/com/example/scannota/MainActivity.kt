package com.example.scannota

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class MainActivity : AppCompatActivity() {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val photoFile by lazy { File(cacheDir, "nota.jpg") }
    private val csvFile by lazy { File(filesDir, "nota.csv") }
    private val photoUri: Uri by lazy { uriFor(photoFile) }

    private lateinit var preview: ImageView
    private lateinit var inputStore: EditText
    private lateinit var inputDate: EditText
    private lateinit var inputTotal: EditText
    private lateinit var inputItems: EditText

    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) scan(photoUri)
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) scan(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preview = findViewById(R.id.preview)
        inputStore = findViewById(R.id.inputStore)
        inputDate = findViewById(R.id.inputDate)
        inputTotal = findViewById(R.id.inputTotal)
        inputItems = findViewById(R.id.inputItems)

        findViewById<Button>(R.id.btnCamera).setOnClickListener {
            try {
                takePhoto.launch(photoUri)
            } catch (e: Exception) {
                toast("Kamera tidak bisa dibuka")
            }
        }
        findViewById<Button>(R.id.btnGallery).setOnClickListener { pickImage.launch("image/*") }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnExport).setOnClickListener { export() }
    }

    private fun scan(uri: Uri) {
        clearForm()

        try {
            showPreview(uri)
            recognizer.process(InputImage.fromFilePath(this, uri))
                .addOnSuccessListener { fill(ReceiptParser.parse(toRows(it))) }
                .addOnFailureListener { toast("Gagal membaca nota: ${it.message}") }
        } catch (e: Exception) {
            toast("Gambar tidak bisa dibuka")
        }
    }

    /** Foto kamera bisa sangat besar, jadi diperkecil dulu agar aplikasi tidak crash. */
    private fun showPreview(uri: Uri) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 1024)
        options.inJustDecodeBounds = false
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        preview.setImageBitmap(bitmap)
        preview.visibility = View.VISIBLE
    }

    /** Gabungkan teks yang sejajar (nama barang di kiri, harga di kanan) jadi satu baris. */
    private fun toRows(text: Text): List<String> {
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

    private fun fill(receipt: Receipt) {
        inputStore.setText(receipt.store)
        inputDate.setText(receipt.date)
        inputTotal.setText(receipt.total)
        inputItems.setText(receipt.items)
        toast("Cek lagi hasilnya sebelum disimpan")
    }

    private fun save() {
        if (inputStore.text.isBlank() || inputTotal.text.isBlank()) {
            toast("Nama toko dan total wajib diisi")
            return
        }
        if (!csvFile.exists()) csvFile.writeText("toko,tanggal,total,barang\n")
        val row = listOf(inputStore, inputDate, inputTotal, inputItems)
            .joinToString(",") { csv(it.text.toString()) }
        csvFile.appendText(row + "\n")
        clearForm()
        preview.visibility = View.GONE
        toast("Tersimpan")
    }

    private fun csv(value: String): String {
        // Awalan ' mencegah teks dibaca sebagai rumus di Excel
        val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    private fun export() {
        if (!csvFile.exists()) {
            toast("Belum ada data")
            return
        }
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uriFor(csvFile))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Bagikan data nota"))
    }

    private fun clearForm() {
        listOf(inputStore, inputDate, inputTotal, inputItems).forEach { it.text.clear() }
    }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.files", file)

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
