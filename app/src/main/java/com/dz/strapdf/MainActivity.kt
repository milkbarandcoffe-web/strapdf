package com.dz.strapdf

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.dz.strapdf.databinding.ActivityMainBinding
import com.dz.strapdf.pdf.IoUtils
import com.dz.strapdf.pdf.PdfOps
import com.dz.strapdf.pdf.SearchablePdfBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private enum class Action { IMG2PDF, MERGE, PDF2IMG, PDF2TXT }
    private var action = Action.MERGE
    private var pendingOut: File? = null
    private var pendingText: String? = null
    private var srcPdf: File? = null
    private var ocrWanted = false

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALY).format(Date())

    // --- launchers ---

    private val pickMany = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onPicked(uris) }

    private val pickOne = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onPicked(listOf(uri)) }

    private val createPdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> saveOut(uri) }

    private val createTxt = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val txt = pendingText
        if (uri != null && txt != null) {
            contentResolver.openOutputStream(uri, "wt")!!.use {
                OutputStreamWriter(it, Charsets.UTF_8).use { w -> w.write(txt) }
            }
            toast(getString(R.string.done))
        }
        pendingText = null
    }

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) exportImages(uri) }

    // --- UI ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        IoUtils.clearScanCache(this)

        b.btnScan.setOnClickListener { startActivity(Intent(this, ScanActivity::class.java)) }

        b.btnImg2Pdf.setOnClickListener {
            askOcr { action = Action.IMG2PDF; pickMany.launch(arrayOf("image/*")) }
        }
        b.btnMerge.setOnClickListener {
            action = Action.MERGE; pickMany.launch(arrayOf("application/pdf"))
        }
        b.btnExtract.setOnClickListener {
            startActivity(Intent(this, ExtractActivity::class.java))
        }
        b.btnEdit.setOnClickListener {
            startActivity(Intent(this, EditActivity::class.java))
        }
        b.btnPdf2Img.setOnClickListener {
            action = Action.PDF2IMG; pickOne.launch(arrayOf("application/pdf"))
        }
        b.btnPdf2Txt.setOnClickListener {
            action = Action.PDF2TXT; pickOne.launch(arrayOf("application/pdf"))
        }
    }

    private fun askOcr(then: () -> Unit) {
        val cb = CheckBox(this).apply { text = getString(R.string.ocr_check); setPadding(40, 20, 40, 20) }
        AlertDialog.Builder(this)
            .setView(cb)
            .setPositiveButton("OK") { _, _ -> ocrWanted = cb.isChecked; then() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // --- flussi ---

    private fun onPicked(uris: List<Uri>) = when (action) {
        Action.IMG2PDF -> runJob {
            val files = uris.mapIndexed { i, u -> IoUtils.uriToCache(this, u, "in_img_$i") }
            val out = File(cacheDir, "out_${stamp()}.pdf")
            SearchablePdfBuilder.build(this, files, ocrWanted, out)
            pendingOut = out
            withContext(Dispatchers.Main) { createPdf.launch("Immagini_${stamp()}.pdf") }
        }
        Action.MERGE -> runJob {
            if (uris.size < 2) { toastBg("Seleziona almeno 2 PDF"); return@runJob }
            val files = uris.mapIndexed { i, u -> IoUtils.uriToCache(this, u, "in_pdf_$i.pdf") }
            val out = File(cacheDir, "out_${stamp()}.pdf")
            PdfOps.merge(files, out)
            pendingOut = out
            withContext(Dispatchers.Main) { createPdf.launch("Unito_${stamp()}.pdf") }
        }
        Action.PDF2IMG -> {
            srcPdf?.delete()
            runJob {
                srcPdf = IoUtils.uriToCache(this, uris[0], "in_src.pdf")
                withContext(Dispatchers.Main) { pickFolder.launch(null) }
            }
        }
        Action.PDF2TXT -> runJob {
            val f = IoUtils.uriToCache(this, uris[0], "in_src.pdf")
            pendingText = PdfOps.toText(this, f, ocrFallback = true)
            withContext(Dispatchers.Main) { createTxt.launch("Testo_${stamp()}.txt") }
        }
    }

    private fun exportImages(folder: Uri) = runJob {
        val src = srcPdf ?: return@runJob
        val dir = DocumentFile.fromTreeUri(this, folder) ?: return@runJob
        val n = PdfOps.pageCount(src)
        for (i in 0 until n) {
            val bmp = PdfOps.renderPage(src, i, 2f)
            val doc = dir.createFile("image/png", "Pagina_${i + 1}_${stamp()}.png") ?: continue
            contentResolver.openOutputStream(doc.uri)!!.use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bmp.recycle()
        }
        toastBg("$n immagini salvate")
    }

    private fun saveOut(uri: Uri?) {
        val out = pendingOut
        if (uri != null && out != null) {
            IoUtils.cacheToUri(this, out, uri)
            toast(getString(R.string.done))
        }
        pendingOut = null
    }

    // --- helpers ---

    private var dialog: AlertDialog? = null

    private fun runJob(block: suspend () -> Unit) {
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.working)
            .setView(ProgressBar(this))
            .setCancelable(false)
            .show()
        lifecycleScope.launch(Dispatchers.IO) {
            try { block() }
            catch (e: Exception) { toastBg("${getString(R.string.error)}: ${e.message}") }
            finally { withContext(Dispatchers.Main) { dialog?.dismiss() } }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private suspend fun toastBg(s: String) = withContext(Dispatchers.Main) { toast(s) }
}
