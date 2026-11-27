package com.example.scannota

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File

class MainActivity : AppCompatActivity() {

    private val store by lazy { NotaStore(this) }
    private val photoUri: Uri by lazy { uriFor(File(cacheDir, "foto.jpg")) }
    private var month = Format.today().take(7)

    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) openEditor(image = photoUri)
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) openEditor(image = uri)
        }

    private val editor =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Lompat ke bulan nota yang baru disimpan supaya langsung terlihat
            result.data?.getStringExtra(EditActivity.EXTRA_MONTH)?.let { month = it }
            render()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        savedInstanceState?.getString("month")?.let { month = it }

        click(R.id.btnCamera) {
            try {
                takePhoto.launch(photoUri)
            } catch (e: Exception) {
                toast("Kamera tidak bisa dibuka")
            }
        }
        click(R.id.btnGallery) { pickImage.launch("image/*") }
        click(R.id.btnManual) { openEditor() }
        click(R.id.btnPrev) { month = Format.shiftMonth(month, -1); render() }
        click(R.id.btnNext) { month = Format.shiftMonth(month, 1); render() }
        click(R.id.btnMenu) { showMenu(it) }
        click(R.id.btnSample) { loadSample() }

        findViewById<BottomNavigationView>(R.id.nav).setOnItemSelectedListener {
            val history = it.itemId == R.id.tabHistory
            show(R.id.pageHistory, history)
            show(R.id.actions, history)
            show(R.id.pageSummary, !history)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        // Tab yang dipulihkan sistem harus cocok dengan halaman yang tampil
        val nav = findViewById<BottomNavigationView>(R.id.nav)
        nav.selectedItemId = nav.selectedItemId
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("month", month)
    }

    private fun render() {
        val all = store.all()
        val current = all.filter { it.month == month }
        val total = current.sumOf { it.total }

        text(R.id.txtMonth, Format.month(month))
        text(R.id.txtTotal, Format.rupiah(total))
        text(R.id.txtCount, if (current.isEmpty()) "Belum ada nota" else "${current.size} nota tersimpan")

        val list = findViewById<LinearLayout>(R.id.list)
        list.removeAllViews()
        current.forEach { nota ->
            val row = inflate(R.layout.row_nota, list)
            row.text(R.id.rowInitial, nota.store.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?")
            row.text(R.id.rowStore, nota.store)
            row.text(R.id.rowDetail, "${Format.date(nota.date)} • ${nota.items.size} barang")
            row.text(R.id.rowTotal, Format.rupiah(nota.total))
            row.setOnClickListener { openEditor(id = nota.id) }
            list.addView(row)
        }
        show(R.id.empty, current.isEmpty())
        show(R.id.btnSample, all.isEmpty())

        renderSummary(all, current, total)
    }

    private fun renderSummary(all: List<Nota>, current: List<Nota>, total: Long) {
        findViewById<BarChartView>(R.id.chart).data = (5 downTo 0).map { back ->
            val key = Format.shiftMonth(month, -back)
            Format.shortMonth(key) to all.filter { it.month == key }.sumOf { it.total }
        }

        val previous = all.filter { it.month == Format.shiftMonth(month, -1) }.sumOf { it.total }
        val difference = total - previous
        text(
            R.id.txtCompare, when {
                previous == 0L -> "Belum ada data bulan sebelumnya"
                difference > 0 -> "Naik ${Format.rupiah(difference)} dari bulan lalu"
                difference < 0 -> "Turun ${Format.rupiah(-difference)} dari bulan lalu"
                else -> "Sama dengan bulan lalu"
            }
        )
        text(R.id.txtAverage, Format.rupiah(if (current.isEmpty()) 0 else total / current.size))
        text(R.id.txtBiggest, Format.rupiah(current.maxOfOrNull { it.total } ?: 0))

        val ranking = findViewById<LinearLayout>(R.id.ranking)
        ranking.removeAllViews()
        current.groupBy { it.store.trim().lowercase() }
            .values
            .map { notas -> notas.first().store to notas.sumOf { it.total } }
            .sortedByDescending { it.second }
            .forEach { (name, sum) ->
                val row = inflate(R.layout.row_rank, ranking)
                row.text(R.id.rankStore, name)
                row.text(R.id.rankTotal, Format.rupiah(sum))
                row.findViewById<LinearProgressIndicator>(R.id.rankBar).progress =
                    if (total > 0) (sum * 100 / total).toInt() else 0
                ranking.addView(row)
            }
        show(R.id.rankEmpty, current.isEmpty())
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Bagikan data (CSV)")
            menu.add(0, 2, 1, "Muat data contoh")
            menu.add(0, 3, 2, "Hapus data contoh")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> export()
                    2 -> loadSample()
                    else -> {
                        store.delete { nota -> nota.id.startsWith(SampleData.PREFIX) }
                        render()
                    }
                }
                true
            }
            show()
        }
    }

    private fun loadSample() {
        store.save(*SampleData.create(Format.today()).toTypedArray())
        month = Format.today().take(7)
        render()
    }

    private fun export() {
        val notas = store.all()
        if (notas.isEmpty()) return toast("Belum ada data")

        val file = File(cacheDir, "nota.csv")
        file.bufferedWriter().use { out ->
            out.write("tanggal,toko,barang,jumlah,harga,subtotal,total_nota\n")
            notas.forEach { nota ->
                nota.items.ifEmpty { listOf(Item("", 0, 0)) }.forEach { item ->
                    val cells = listOf(
                        nota.date, nota.store, item.name, item.qty.toString(),
                        item.price.toString(), item.subtotal.toString(), nota.total.toString()
                    )
                    out.write(cells.joinToString(",") { csv(it) } + "\n")
                }
            }
        }
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uriFor(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Bagikan data nota"))
    }

    private fun csv(value: String): String {
        // Awalan ' mencegah teks dibaca sebagai rumus di Excel
        val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    private fun openEditor(id: String? = null, image: Uri? = null) {
        val intent = Intent(this, EditActivity::class.java)
            .putExtra(EditActivity.EXTRA_ID, id)
        if (image != null) {
            intent.setData(image).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        editor.launch(intent)
    }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.files", file)

    private fun inflate(layout: Int, parent: ViewGroup): View =
        layoutInflater.inflate(layout, parent, false)

    private fun click(id: Int, action: (View) -> Unit) =
        findViewById<View>(id).setOnClickListener(action)

    private fun show(id: Int, visible: Boolean) {
        findViewById<View>(id).visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun text(id: Int, value: String) {
        findViewById<TextView>(id).text = value
    }

    private fun View.text(id: Int, value: String) {
        findViewById<TextView>(id).text = value
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
