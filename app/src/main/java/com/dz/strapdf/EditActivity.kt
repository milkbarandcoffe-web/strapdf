package com.dz.strapdf

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dz.strapdf.databinding.ActivityEditBinding
import com.dz.strapdf.pdf.Ann
import com.dz.strapdf.pdf.IoUtils
import com.dz.strapdf.pdf.Overlay
import com.dz.strapdf.pdf.PdfOps
import com.dz.strapdf.ui.PageEditView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditActivity : AppCompatActivity() {

    private lateinit var b: ActivityEditBinding
    private var src: File? = null
    private var out: File? = null
    private var pageCount = 0
    private var current = 0
    private val annots = mutableMapOf<Int, MutableList<Ann>>()

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri == null) finish() else load(uri) }

    private val createPdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val f = out
        if (uri != null && f != null) {
            IoUtils.cacheToUri(this, f, uri)
            Toast.makeText(this, R.string.done, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnText.setOnClickListener { b.editView.mode = PageEditView.Mode.TEXT }
        b.btnHl.setOnClickListener { b.editView.mode = PageEditView.Mode.HL }
        b.btnInk.setOnClickListener { b.editView.mode = PageEditView.Mode.INK }
        b.btnUndo.setOnClickListener {
            annots[current]?.let { if (it.isNotEmpty()) it.removeAt(it.size - 1) }
            b.editView.invalidate()
        }
        b.btnPrev.setOnClickListener { if (current > 0) showPage(current - 1) }
        b.btnNext.setOnClickListener { if (current < pageCount - 1) showPage(current + 1) }
        b.btnSave.setOnClickListener { save() }

        b.editView.onTextRequested = { x, y -> askText(x, y) }

        pickPdf.launch(arrayOf("application/pdf"))
    }

    private fun load(uri: Uri) {
        val dialog = progress()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val f = IoUtils.uriToCache(this@EditActivity, uri, "in_edit.pdf")
                src = f
                pageCount = PdfOps.pageCount(f)
                withContext(Dispatchers.Main) { dialog.dismiss(); showPage(0) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@EditActivity, "Errore: ${e.message}",
                        Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun showPage(idx: Int) {
        val f = src ?: return
        current = idx
        b.txtPage.text = "${idx + 1}/$pageCount"
        lifecycleScope.launch(Dispatchers.IO) {
            val bmp = PdfOps.renderPage(f, idx, Overlay.RENDER_SCALE)
            withContext(Dispatchers.Main) {
                b.editView.setPage(bmp, annots.getOrPut(idx) { mutableListOf() })
            }
        }
    }

    private fun askText(x: Float, y: Float) {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.mode_text)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val t = input.text.toString()
                if (t.isNotBlank()) {
                    annots.getOrPut(current) { mutableListOf() }
                        .add(Ann.TextAnn(x, y, t, 32f))
                    b.editView.invalidate()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun save() {
        val f = src ?: return
        val dialog = progress()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val o = File(cacheDir, "out_edit.pdf")
                Overlay.apply(f, annots, o)
                out = o
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    val name = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALY).format(Date())
                    createPdf.launch("Modificato_$name.pdf")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@EditActivity, "Errore: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun progress(): AlertDialog = AlertDialog.Builder(this)
        .setTitle(R.string.working)
        .setView(ProgressBar(this))
        .setCancelable(false)
        .show()
}
