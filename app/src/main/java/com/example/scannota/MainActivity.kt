package com.example.scannota

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File

class MainActivity : AppCompatActivity() {

    private val store by lazy { NotaStore(this) }
    private val accounts by lazy { Accounts(this) }
    private val cameraFile by lazy { File(cacheDir, "kamera.jpg") }
    private var month = Format.today().take(7)
    private var summaryTab = false

    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) openEditor(photo = cameraFile)
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) importPhoto(uri)
        }

    private val documentScan =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.let { importPhoto(it.imageUri) }
        }

    private val editor =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Lompat ke bulan nota yang baru disimpan supaya langsung terlihat di rekap
            result.data?.getStringExtra(EditActivity.EXTRA_MONTH)?.let { month = it }
            render()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (accounts.current() == null) return logout()
        setContentView(R.layout.activity_main)
        savedInstanceState?.getString("month")?.let { month = it }
        summaryTab = savedInstanceState?.getBoolean("rekap") ?: false

        click(R.id.tabHome) { showTab(summary = false) }
        click(R.id.tabSummary) { showTab(summary = true) }
        click(R.id.btnSeeAll) { showTab(summary = true) }
        click(R.id.btnScan) { showScanOptions() }
        click(R.id.btnPrev) { month = Format.shiftMonth(month, -1); render() }
        click(R.id.btnNext) { month = Format.shiftMonth(month, 1); render() }
        click(R.id.btnProfile) { showProfile() }
        click(R.id.btnExport) { export(store.all().filter { it.month == month }, "rekap-nota-$month") }
        showTab(summaryTab)
    }

    override fun onResume() {
        super.onResume()
        if (accounts.current() != null) render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("month", month)
        outState.putBoolean("rekap", summaryTab)
    }

    private fun showTab(summary: Boolean) {
        summaryTab = summary
        show(R.id.pageHome, !summary)
        show(R.id.pageSummary, summary)
        listOf(R.id.tabHome to !summary, R.id.tabSummary to summary).forEach { (id, active) ->
            val tab = findViewById<MaterialButton>(id)
            val color = ContextCompat.getColorStateList(
                this, if (active) R.color.primary else R.color.text_soft
            )
            tab.setTextColor(color)
            tab.iconTint = color
            tab.isSelected = active
        }
    }

    private fun render() {
        val all = store.all()
        val name = accounts.current()?.name.orEmpty()
        text(R.id.txtName, name)
        text(R.id.btnProfile, name.split(" ").filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.take(1).uppercase() })

        // Beranda: selalu bulan berjalan
        val now = Format.today().take(7)
        val thisMonth = all.filter { it.month == now }
        text(R.id.txtHeroLabel, "Belanja ${Format.month(now)}")
        text(R.id.txtHeroTotal, Format.rupiah(thisMonth.sumOf { it.total }))
        text(R.id.txtHeroCount, "${thisMonth.size} nota tercatat")
        findViewById<BarChartView>(R.id.chart).data = (5 downTo 0).map { back ->
            val key = Format.shiftMonth(now, -back)
            Format.shortMonth(key) to all.filter { it.month == key }.sumOf { it.total }
        }
        fillList(R.id.recent, all.take(5))
        show(R.id.cardRecent, all.isNotEmpty())
        show(R.id.empty, all.isEmpty())
        show(R.id.btnSeeAll, all.isNotEmpty())

        renderSummary(all.filter { it.month == month })
    }

    private fun renderSummary(current: List<Nota>) {
        val total = current.sumOf { it.total }
        text(R.id.txtMonth, Format.month(month))
        text(R.id.txtTotal, Format.rupiah(total))
        text(R.id.txtCount, current.size.toString())
        text(R.id.txtAverage, Format.rupiah(if (current.isEmpty()) 0 else total / current.size))
        findViewById<View>(R.id.btnExport).isEnabled = current.isNotEmpty()
        show(R.id.cardRanking, current.isNotEmpty())
        show(R.id.cardMonth, current.isNotEmpty())
        show(R.id.monthEmpty, current.isEmpty())
        fillList(R.id.list, current)

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
    }

    private fun fillList(id: Int, notas: List<Nota>) {
        val list = findViewById<LinearLayout>(id)
        list.removeAllViews()
        notas.forEachIndexed { index, nota ->
            val row = inflate(R.layout.row_nota, list)
            row.text(R.id.rowInitial, nota.store.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?")
            row.text(R.id.rowStore, nota.store)
            row.text(R.id.rowDetail, "${Format.date(nota.date)} • ${nota.items.size} barang")
            row.text(R.id.rowTotal, Format.rupiah(nota.total))
            row.findViewById<View>(R.id.rowDivider).visibility =
                if (index == notas.lastIndex) View.GONE else View.VISIBLE
            row.setOnClickListener { openEditor(id = nota.id) }
            list.addView(row)
        }
    }

    private fun showScanOptions() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_scan, null)
        fun option(id: Int, action: () -> Unit) = view.findViewById<View>(id).setOnClickListener {
            sheet.dismiss()
            action()
        }
        option(R.id.optScan) { startDocumentScan() }
        option(R.id.optCamera) { startCamera() }
        option(R.id.optGallery) { pickImage.launch("image/*") }
        option(R.id.optManual) { openEditor() }
        sheet.setContentView(view)
        sheet.show()
    }

    /** Pemindai Google: memotong dan meluruskan nota otomatis. Gagal? Pakai kamera biasa. */
    private fun startDocumentScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener(this) {
                documentScan.launch(IntentSenderRequest.Builder(it).build())
            }
            .addOnFailureListener(this) {
                toast("Pemindai belum tersedia, memakai kamera biasa")
                startCamera()
            }
    }

    private fun startCamera() {
        try {
            takePhoto.launch(uriFor(cameraFile))
        } catch (e: Exception) {
            toast("Kamera tidak bisa dibuka")
        }
    }

    private fun importPhoto(uri: Uri) {
        try {
            val file = File(cacheDir, "impor.jpg")
            contentResolver.openInputStream(uri)!!.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            openEditor(photo = file)
        } catch (e: Exception) {
            toast("Gambar tidak bisa dibuka")
        }
    }

    private fun showProfile() {
        val account = accounts.current() ?: return logout()
        val options = arrayOf(
            "Ekspor semua nota ke Excel", "Muat data contoh", "Hapus data contoh", "Keluar"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("${account.name}\n${account.phone}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> export(store.all(), "rekap-nota-semua")
                    1 -> {
                        store.save(*SampleData.create(Format.today()).toTypedArray())
                        month = Format.today().take(7)
                        render()
                    }
                    2 -> {
                        store.delete { it.id.startsWith(SampleData.PREFIX) }
                        render()
                    }
                    else -> logout()
                }
            }
            .show()
    }

    private fun logout() {
        accounts.logout()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun export(notas: List<Nota>, name: String) {
        if (notas.isEmpty()) return toast("Belum ada data")
        val file = File(cacheDir, "$name.xlsx")
        file.outputStream().use { Xlsx.write(notas, it) }
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .putExtra(Intent.EXTRA_STREAM, uriFor(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri(file.name, uriFor(file))
        startActivity(Intent.createChooser(intent, "Kirim rekap Excel"))
    }

    private fun openEditor(id: String? = null, photo: File? = null) {
        editor.launch(
            Intent(this, EditActivity::class.java)
                .putExtra(EditActivity.EXTRA_ID, id)
                .putExtra(EditActivity.EXTRA_PHOTO, photo?.path)
        )
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
