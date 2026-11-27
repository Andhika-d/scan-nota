package com.example.scannota

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Pembuat file Excel (.xlsx) sederhana: sheet "Rekap" dan "Rincian". */
object Xlsx {
    private const val HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml"

    // Gaya: 0 biasa, 1 judul kolom, 2 angka ribuan, 3 tanggal, 4 angka ribuan tebal
    private class Cell(val value: Any, val style: Int = 0)

    fun write(notas: List<Nota>, out: OutputStream) {
        val sorted = notas.sortedBy { it.date }

        val summary = mutableListOf(header("Tanggal", "Toko", "Jumlah Barang", "Total (Rp)"))
        sorted.forEach {
            summary.add(listOf(date(it.date), Cell(it.store), Cell(it.items.size), Cell(it.total, 2)))
        }
        summary.add(
            listOf(Cell(""), Cell("TOTAL", 1), Cell(""), Cell(sorted.sumOf { it.total }, 4))
        )

        val detail = mutableListOf(header("Tanggal", "Toko", "Barang", "Jumlah", "Harga", "Subtotal"))
        sorted.forEach { nota ->
            nota.items.forEach {
                detail.add(
                    listOf(
                        date(nota.date), Cell(nota.store), Cell(it.name), Cell(it.qty),
                        Cell(it.price, 2), Cell(it.subtotal, 2)
                    )
                )
            }
        }

        ZipOutputStream(out).use { zip ->
            fun entry(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write((HEAD + body).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val sheets = (1..2).joinToString("") {
                """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="$TYPE.worksheet+xml"/>"""
            }
            entry(
                "[Content_Types].xml",
                """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="$TYPE.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="$TYPE.styles+xml"/>$sheets</Types>"""
            )
            entry("_rels/.rels", rels(listOf("officeDocument" to "xl/workbook.xml")))
            entry(
                "xl/workbook.xml",
                """<workbook xmlns="$MAIN" xmlns:r="$REL"><sheets><sheet name="Rekap" sheetId="1" r:id="rId1"/><sheet name="Rincian" sheetId="2" r:id="rId2"/></sheets></workbook>"""
            )
            entry(
                "xl/_rels/workbook.xml.rels",
                rels(
                    listOf(
                        "worksheet" to "worksheets/sheet1.xml",
                        "worksheet" to "worksheets/sheet2.xml",
                        "styles" to "styles.xml"
                    )
                )
            )
            entry(
                "xl/styles.xml",
                """<styleSheet xmlns="$MAIN"><numFmts count="1"><numFmt numFmtId="164" formatCode="dd/mm/yyyy"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFDCEFE7"/></patternFill></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="5"><xf/><xf fontId="1" fillId="2" applyFont="1" applyFill="1"/><xf numFmtId="3" applyNumberFormat="1"/><xf numFmtId="164" applyNumberFormat="1"/><xf numFmtId="3" fontId="1" applyNumberFormat="1" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
            )
            entry("xl/worksheets/sheet1.xml", sheet(summary, listOf(14, 34, 16, 18)))
            entry("xl/worksheets/sheet2.xml", sheet(detail, listOf(14, 30, 34, 10, 14, 16)))
        }
    }

    private fun header(vararg names: String) = names.map { Cell(it, 1) }

    /** Excel menyimpan tanggal sebagai jumlah hari sejak 30 Desember 1899. */
    private fun date(iso: String): Cell {
        val (y, m, d) = iso.split("-").map { it.toInt() }
        val a = (14 - m) / 12
        val yy = y + 4800 - a
        val julian = d + (153 * (m + 12 * a - 3) + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045
        return Cell(julian - 2415019, 3)
    }

    private fun rels(targets: List<Pair<String, String>>) =
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            targets.mapIndexed { i, (type, target) ->
                """<Relationship Id="rId${i + 1}" Type="$REL/$type" Target="$target"/>"""
            }.joinToString("") + "</Relationships>"

    private fun sheet(rows: List<List<Cell>>, widths: List<Int>): String {
        val cols = widths.mapIndexed { i, w ->
            """<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>"""
        }.joinToString("")
        val data = rows.mapIndexed { r, row ->
            val cells = row.mapIndexed { c, cell ->
                val ref = "${'A' + c}${r + 1}"
                val value = cell.value
                // Teks disimpan sebagai teks, jadi tidak pernah dijalankan sebagai rumus
                if (value is Number) """<c r="$ref" s="${cell.style}"><v>$value</v></c>"""
                else """<c r="$ref" s="${cell.style}" t="inlineStr"><is><t>${escape(value.toString())}</t></is></c>"""
            }.joinToString("")
            """<row r="${r + 1}">$cells</row>"""
        }.joinToString("")
        return """<worksheet xmlns="$MAIN"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" state="frozen"/></sheetView></sheetViews><cols>$cols</cols><sheetData>$data</sheetData></worksheet>"""
    }

    private fun escape(text: String) = text
        .filter { it >= ' ' && it != '￾' && it != '￿' }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
