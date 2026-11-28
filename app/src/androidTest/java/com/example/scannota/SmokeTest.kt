package com.example.scannota

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.ColorMatrixColorFilter
import android.graphics.ColorMatrix
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val accounts = Accounts(context)
    private val store = NotaStore(context)

    @Before
    fun clear() {
        context.getSharedPreferences("akun", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.deleteRecursively()
        context.filesDir.mkdirs()
    }

    private fun signIn() = assertTrue(accounts.register("Andi Pratama", "081200000000", "123456"))

    private fun asset(name: String): Bitmap =
        instrumentation.context.assets.open(name).use { BitmapFactory.decodeStream(it) }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(1200)
        val out = instrumentation.uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/layar-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(out).use { it.readBytes() }
    }

    @Test
    fun daftarKeluarDanMasuk() {
        ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
            screenshot("1-daftar")
            scenario.onActivity {
                fun type(id: Int, value: String) = it.findViewById<EditText>(id).setText(value)
                type(R.id.inputName, "Andi Pratama")
                type(R.id.inputPhone, "081200000000")
                type(R.id.inputPin, "123456")
                type(R.id.inputConfirm, "123456")
                it.findViewById<View>(R.id.btnSubmit).performClick()
            }
        }
        assertEquals("Andi Pratama", accounts.current()?.name)
        accounts.logout()
        assertFalse(accounts.login("081200000000", "000000"))
        ActivityScenario.launch(LoginActivity::class.java).use { screenshot("2-masuk") }
        assertTrue(accounts.login("081200000000", "123456"))
        assertFalse(accounts.register("Lain", "081200000000", "111111"))
    }

    @Test
    fun bacaSemuaNotaLurusDanMiring() {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val cases = listOf(
            Triple("nota-dummy.png", 143000L, listOf("Beras", "Minyak", "Gula", "Telur", "Sabun")),
            Triple("nota-1-minimarket.png", 87800L, listOf("Mie", "Susu", "Roti", "Teh")),
            Triple("nota-2-warung.png", 64000L, listOf("Nasi", "Es", "Ayam")),
            Triple("nota-3-restoran.png", 137445L, listOf("Sate", "Sop", "Jus", "Air")),
            Triple("nota-4-apotek.png", 96500L, listOf("Vitamin", "Masker", "Minyak")),
            Triple("nota-5-toko-bangunan.png", 2755000L, listOf("Semen", "Cat", "Pipa")),
            Triple(
                "nota-6-tabel.jpg", 2997000L,
                listOf("Semen", "Pasir", "Batu", "Bata", "Besi", "Paku", "Cat", "Kabel", "Fitting", "Saklar")
            )
        )
        // Jumlah subtotal barang yang seharusnya (restoran dan apotek: sebelum pajak/diskon)
        val sums = listOf(143000L, 87800L, 64000L, 119000L, 101500L, 2755000L, 2727000L)
        val failures = mutableListOf<String>()
        cases.forEachIndexed { index, (name, total, items) ->
            val source = asset(name)
            val variants = listOf(0f, 4f, -3f).map { angle ->
                val turned = Bitmap.createBitmap(
                    source, 0, 0, source.width, source.height,
                    Matrix().apply { postRotate(angle) }, true
                )
                val page = Bitmap.createBitmap(turned.width, turned.height, Bitmap.Config.ARGB_8888)
                Canvas(page).apply { drawColor(Color.WHITE); drawBitmap(turned, 0f, 0f, null) }
                "$angle" to page
            }
            // Sama seperti jalur aplikasi: disimpan sebagai JPEG lalu dibuka lagi
            val jpeg = File(context.cacheDir, "uji.jpg")
            Photos.save(source, jpeg)
            for ((label, page) in variants + ("jpeg" to Photos.load(jpeg)!!)) {
                val rows = Ocr.toRows(Tasks.await(client.process(InputImage.fromBitmap(page, 0))))
                val result = ReceiptParser.parse(rows)
                val names = result.items.map { it.name.replace(" ", "") }
                val namesOk = names.size == items.size &&
                    names.zip(items).all { (found, expected) -> found.startsWith(expected) }
                val sum = result.items.sumOf { it.subtotal }
                if (result.total != total || !namesOk || sum != sums[index] || result.date.isEmpty()) {
                    failures.add(
                        "$name $label: ${result.date} ${result.total}/$sum $names | ${rows.joinToString(" / ")}"
                    )
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Nota patokan dalam kondisi foto yang kurang bagus. */
    @Test
    fun notaPatokanKondisiSulit() {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val source = asset("nota-6-tabel.jpg")
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        fun draw(matrix: Matrix, paint: Paint? = null): Bitmap {
            val area = RectF(0f, 0f, w, h).also { matrix.mapRect(it) }
            val page = Bitmap.createBitmap(
                area.width().toInt() + 80, area.height().toInt() + 80, Bitmap.Config.ARGB_8888
            )
            val shifted = Matrix(matrix).apply { postTranslate(40 - area.left, 40 - area.top) }
            Canvas(page).apply {
                drawColor(Color.rgb(90, 70, 55))
                drawBitmap(source, shifted, paint ?: Paint(Paint.FILTER_BITMAP_FLAG))
            }
            return page
        }
        fun rotate(degrees: Float) = draw(Matrix().apply { postRotate(degrees) })
        fun scale(factor: Float) =
            Bitmap.createScaledBitmap(source, (w * factor).toInt(), (h * factor).toInt(), true)
        // Sudut kertas digeser, seperti difoto dari bawah atau dari samping
        fun perspective(vararg corners: Float) = draw(Matrix().apply {
            setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, corners, 0, 4)
        })
        val dim = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.45f, 0.42f, 0.38f, 1f) })
        }

        val variants = listOf(
            "miring8" to rotate(8f),
            "miring-12" to rotate(-12f),
            "kecil70" to scale(0.7f),
            "kecil50" to scale(0.5f),
            "buram" to Bitmap.createScaledBitmap(scale(0.45f), w.toInt(), h.toInt(), true),
            "gelap" to draw(Matrix(), dim),
            "dariBawah" to perspective(w * 0.08f, 0f, w * 0.92f, 0f, w, h, 0f, h),
            "dariSamping" to perspective(0f, 0f, w, h * 0.06f, w, h * 0.94f, 0f, h),
            "menyerong" to perspective(w * 0.05f, h * 0.02f, w * 0.97f, 0f, w, h * 0.96f, 0f, h)
        )
        val names = listOf(
            "Semen", "Pasir", "Batu", "Bata", "Besi", "Paku", "Cat", "Kabel", "Fitting", "Saklar"
        )
        val failures = mutableListOf<String>()
        for ((label, page) in variants) {
            val rows = Ocr.toRows(Tasks.await(client.process(InputImage.fromBitmap(page, 0))))
            val result = ReceiptParser.parse(rows)
            val found = result.items.map { it.name.replace(" ", "") }
            val ok = result.store.contains("MAJU") && result.date == "2026-11-27" &&
                result.total == 2997000L && result.items.sumOf { it.subtotal } == 2727000L &&
                found.size == names.size && found.zip(names).all { (a, b) -> a.startsWith(b) }
            if (!ok) {
                // Cukup ringkas supaya semua kegagalan muat di laporan
                failures.add(
                    "[$label] ${result.store};${result.date};${result.total};" +
                        "${result.items.sumOf { it.subtotal }};${found.size} :: " +
                        rows.takeLast(14).joinToString("/")
                )
            }
        }
        assertTrue(
            "${failures.size} dari ${variants.size} gagal: " +
                failures.joinToString(" ") { it.substringBefore(" :: ") } + "\n" +
                failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    /** Nota berkolom satuan dan kode barang, dengan nama barang dua baris. */
    @Test
    fun notaKolomKodeBarang() {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val source = asset("nota-7-kode.jpg")
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        fun draw(matrix: Matrix): Bitmap {
            val area = RectF(0f, 0f, w, h).also { matrix.mapRect(it) }
            val page = Bitmap.createBitmap(
                area.width().toInt() + 80, area.height().toInt() + 80, Bitmap.Config.ARGB_8888
            )
            val shifted = Matrix(matrix).apply { postTranslate(40 - area.left, 40 - area.top) }
            Canvas(page).apply {
                drawColor(Color.rgb(90, 70, 55))
                drawBitmap(source, shifted, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            return page
        }
        fun rotate(degrees: Float) = draw(Matrix().apply { postRotate(degrees) })
        fun perspective(vararg corners: Float) = draw(Matrix().apply {
            setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, corners, 0, 4)
        })
        val jpeg = File(context.cacheDir, "uji.jpg")
        Photos.save(source, jpeg)

        val variants = listOf(
            "asli" to source,
            "jpeg" to Photos.load(jpeg)!!,
            "miring4" to rotate(4f),
            "miring-3" to rotate(-3f),
            "miring8" to rotate(8f),
            "kecil70" to Bitmap.createScaledBitmap(source, (w * 0.7f).toInt(), (h * 0.7f).toInt(), true),
            "dariBawah" to perspective(w * 0.08f, 0f, w * 0.92f, 0f, w, h, 0f, h),
            "menyerong" to perspective(w * 0.05f, h * 0.02f, w * 0.97f, 0f, w, h * 0.96f, 0f, h)
        )
        val names = listOf("Besi", "Atap", "Sekrup", "Semen", "Wiremesh")
        val numbers = listOf(12 to 105000L, 20 to 78000L, 10 to 300L, 5 to 72000L, 2 to 525000L)
        val failures = mutableListOf<String>()
        for ((label, page) in variants) {
            val rows = Ocr.toRows(Tasks.await(client.process(InputImage.fromBitmap(page, 0))))
            val result = ReceiptParser.parse(rows)
            val found = result.items.map { it.name }
            val ok = result.store.replace(" ", "").contains("CONTOH2") && result.date == "2024-05-26" &&
                result.total == 4233000L &&
                result.items.map { it.qty to it.price } == numbers &&
                found.zip(names).all { (a, b) -> a.startsWith(b) } &&
                // Baris kedua nama barang ikut tersambung
                found[1].contains("m") && found[4].contains("54")
            if (!ok) {
                failures.add(
                    "[$label] ${result.store};${result.date};${result.total};" +
                        result.items.joinToString(",") { "${it.name}=${it.qty}x${it.price}" } +
                        " :: " + rows.joinToString("/")
                )
            }
        }
        assertTrue(
            "${failures.size} dari ${variants.size} gagal: " +
                failures.joinToString(" ") { it.substringBefore("]") + "]" } + "\n" +
                failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun scanSimpanRekapDanEkspor() {
        signIn()
        val photo = File(context.cacheDir, "uji.jpg")
        Photos.save(asset("nota-dummy.png"), photo)
        val intent = Intent(context, EditActivity::class.java)
            .putExtra(EditActivity.EXTRA_PHOTO, photo.path)

        ActivityScenario.launch<EditActivity>(intent).use { scenario ->
            var total = ""
            for (attempt in 1..60) {
                scenario.onActivity { total = it.findViewById<EditText>(R.id.inputTotal).text.toString() }
                if (total.isNotEmpty()) break
                Thread.sleep(500)
            }
            screenshot("3-hasil-scan")
            scenario.onActivity {
                assertEquals("143000", total)
                assertEquals(5, it.findViewById<LinearLayout>(R.id.items).childCount)
                it.findViewById<View>(R.id.btnSave).performClick()
            }
        }
        val saved = store.all().single()
        assertEquals("2026-11-27", saved.date)
        assertTrue(store.photo(saved.id).exists())

        store.save(*SampleData.create(Format.today()).toTypedArray())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            screenshot("4-beranda")
            scenario.onActivity { it.findViewById<View>(R.id.btnScan).performClick() }
            screenshot("5-pilihan-scan")
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.findViewById<View>(R.id.tabSummary).performClick()
                assertTrue(it.findViewById<LinearLayout>(R.id.ranking).childCount > 0)
            }
            screenshot("6-rekap")
            scenario.recreate()
        }
        val edit = Intent(context, EditActivity::class.java).putExtra(EditActivity.EXTRA_ID, saved.id)
        ActivityScenario.launch<EditActivity>(edit).use { scenario ->
            scenario.recreate()
            screenshot("7-edit")
            scenario.onActivity { it.findViewById<View>(R.id.btnSave).performClick() }
        }
        assertEquals(saved, store.find(saved.id))

        val excel = File(context.cacheDir, "uji.xlsx")
        excel.outputStream().use { Xlsx.write(store.all(), it) }
        assertTrue(excel.length() > 1000)

        // Akun lain tidak melihat data akun ini
        assertTrue(accounts.register("Budi", "081300000000", "654321"))
        assertTrue(store.all().isEmpty())
    }
}
