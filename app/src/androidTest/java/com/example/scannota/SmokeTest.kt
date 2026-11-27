package com.example.scannota

import android.content.Intent
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = NotaStore(context)

    @Before
    fun clear() = store.delete { true }

    @Test
    fun scanNotaDummyLaluSimpan() {
        val file = File(context.cacheDir, "uji.png")
        InstrumentationRegistry.getInstrumentation().context.assets.open("nota-dummy.png").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(context, EditActivity::class.java).setData(uri)

        ActivityScenario.launch<EditActivity>(intent).use { scenario ->
            var total = ""
            for (attempt in 1..60) {
                scenario.onActivity { total = it.findViewById<EditText>(R.id.inputTotal).text.toString() }
                if (total.isNotEmpty()) break
                Thread.sleep(500)
            }
            scenario.onActivity {
                val name = it.findViewById<EditText>(R.id.inputStore).text.toString()
                assertTrue("toko: $name", name.contains("SEJAHTERA", ignoreCase = true))
                assertEquals("143000", total)
                assertEquals(5, it.findViewById<LinearLayout>(R.id.items).childCount)
                it.findViewById<android.view.View>(R.id.btnSave).performClick()
            }
        }
        val saved = store.all().single()
        assertEquals("2026-11-27", saved.date)
        assertEquals(143000L, saved.items.sumOf { it.subtotal })
    }

    @Test
    fun riwayatRekapDanEdit() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.findViewById<android.view.View>(R.id.btnSample).performClick()
                assertTrue(it.findViewById<LinearLayout>(R.id.list).childCount > 0)
                it.findViewById<android.view.View>(R.id.tabSummary).performClick()
                assertTrue(it.findViewById<LinearLayout>(R.id.ranking).childCount > 0)
            }
            scenario.recreate()
        }
        val nota = store.all().first()
        val intent = Intent(context, EditActivity::class.java).putExtra(EditActivity.EXTRA_ID, nota.id)
        ActivityScenario.launch<EditActivity>(intent).use { scenario ->
            scenario.recreate()
            scenario.onActivity { it.findViewById<android.view.View>(R.id.btnSave).performClick() }
        }
        assertEquals(nota, store.find(nota.id))
    }
}
