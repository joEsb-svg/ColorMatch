package it.joesb.colormatch

import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Export {
    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.ITALY)
    private val fileFmt = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ITALY)

    private fun dir(ctx: Context): File = File(ctx.cacheDir, "exports").apply { mkdirs() }

    private fun num(v: Double, d: Int = 2) = fmt(v, d)

    /** CSV con separatore ";" e virgola decimale: si apre direttamente in Excel italiano. */
    fun csv(ctx: Context, checks: List<Check>): File {
        val sb = StringBuilder("﻿")
        sb.append(
            listOf(
                "Data", "Ora", "Lotto", "Tipo tasto", "Materiale", "Colore", "Esito",
                "DeltaE00", "Soglia", "L", "a", "b", "L campione", "a campione", "b campione", "Indicazioni",
            ).joinToString(";")
        ).append("\r\n")
        checks.forEach { c ->
            val row = listOf(
                dateFmt.format(Date(c.time)),
                timeFmt.format(Date(c.time)),
                c.lot,
                c.buttonType,
                c.material,
                c.color,
                if (c.ok) "CONFORME" else "NON CONFORME",
                num(c.deltaE),
                num(c.threshold, 1),
                num(c.lab.l), num(c.lab.a), num(c.lab.b),
                num(c.refLab.l), num(c.refLab.a), num(c.refLab.b),
                differenceHints(c.refLab, c.lab).joinToString(", "),
            ).joinToString(";") { cell(it) }
            sb.append(row).append("\r\n")
        }
        val f = File(dir(ctx), "verifiche-${fileFmt.format(Date())}.csv")
        f.writeText(sb.toString())
        return f
    }

    private fun cell(s: String): String =
        if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** Report PDF A4 con riepilogo e tabella delle verifiche. */
    fun pdf(ctx: Context, checks: List<Check>, periodLabel: String): File {
        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 36f

        val title = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val text = Paint().apply { textSize = 9.5f; isAntiAlias = true; color = AColor.rgb(30, 30, 30) }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val grey = Paint(text).apply { color = AColor.rgb(110, 110, 110) }
        val line = Paint().apply { color = AColor.rgb(210, 210, 210); strokeWidth = 0.6f }
        val okP = Paint().apply { color = AColor.rgb(46, 125, 50); isAntiAlias = true }
        val badP = Paint().apply { color = AColor.rgb(198, 40, 40); isAntiAlias = true }

        // colonne: x di partenza
        val cols = floatArrayOf(margin, 100f, 140f, 215f, 305f, 380f, 455f, 495f)
        val headers = listOf("Data", "Ora", "Lotto", "Tipo tasto", "Materiale", "Colore", "ΔE00", "Esito")

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun clip(s: String, width: Float, p: Paint): String {
            if (p.measureText(s) <= width) return s
            var t = s
            while (t.isNotEmpty() && p.measureText("$t…") > width) t = t.dropLast(1)
            return "$t…"
        }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            page = p
            val c = p.canvas
            y = margin + 10f
            if (pageNo == 1) {
                c.drawText("Report controllo colore", margin, y + 8f, title)
                y += 28f
                val ok = checks.count { it.ok }
                val bad = checks.size - ok
                c.drawText("Periodo: $periodLabel   ·   Generato il ${dateFmt.format(Date())} alle ${timeFmt.format(Date())}", margin, y, grey)
                y += 16f
                c.drawText("Verifiche: ${checks.size}    Conformi: $ok    Non conformi: $bad", margin, y, bold)
                if (checks.isNotEmpty()) {
                    val pct = ok * 100.0 / checks.size
                    c.drawText("Conformità: ${fmt(pct, 1)}%", 380f, y, bold)
                }
                y += 22f
            }
            headers.forEachIndexed { i, h -> c.drawText(h, cols[i], y, bold) }
            y += 5f
            c.drawLine(margin, y, pageW - margin, y, line)
            y += 13f
        }

        newPage()
        if (checks.isEmpty()) {
            page!!.canvas.drawText("Nessuna verifica nel periodo selezionato.", margin, y, grey)
        }
        checks.forEach { ch ->
            if (y > pageH - margin - 20f) newPage()
            val c = page!!.canvas
            c.drawText(dateFmt.format(Date(ch.time)), cols[0], y, text)
            c.drawText(timeFmt.format(Date(ch.time)), cols[1], y, text)
            c.drawText(clip(ch.lot, cols[3] - cols[2] - 6f, text), cols[2], y, text)
            c.drawText(clip(ch.buttonType, cols[4] - cols[3] - 6f, text), cols[3], y, text)
            c.drawText(clip(ch.material, cols[5] - cols[4] - 6f, text), cols[4], y, text)
            c.drawText(clip(ch.color, cols[6] - cols[5] - 6f, text), cols[5], y, text)
            c.drawText(fmt(ch.deltaE, 2), cols[6], y, text)
            val p = if (ch.ok) okP else badP
            c.drawCircle(cols[7] + 4f, y - 3.2f, 3.6f, p)
            c.drawText(if (ch.ok) "OK" else "NON CONF.", cols[7] + 12f, y, if (ch.ok) text else bold)
            y += 6f
            c.drawLine(margin, y, pageW - margin, y, line)
            y += 12f
        }
        page?.let { doc.finishPage(it) }

        val f = File(dir(ctx), "report-colore-${fileFmt.format(Date())}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    /** Apre il menu di condivisione di Android (email, WhatsApp, Drive…). */
    fun share(ctx: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            clipData = android.content.ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Condividi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
