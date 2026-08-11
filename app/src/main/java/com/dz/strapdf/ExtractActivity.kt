package com.dz.strapdf

import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.dz.strapdf.databinding.ActivityExtractBinding
import com.dz.strapdf.pdf.IoUtils
import com.dz.strapdf.pdf.PdfOps
import com.dz.strapdf.ui.PageThumbAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExtractActivity : AppCompatActivity() {

    private lateinit var b: ActivityExtractBinding
    private var src: File? = null
    private var out: File? = null
    private val selected = sortedSetOf<Int>()
    private val thumbs = mutableListOf<Bitmap?>()

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
        b = ActivityExtractBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.grid.layoutManager = GridLayoutManager(this, 2)
        b.btnSave.setOnClickListener { export() }
        pickPdf.launch(arrayOf("application/pdf"))
    }

    private fun load(uri: Uri) {
        val dialog = progress()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val f = IoUtils.uriToCache(this@ExtractActivity, uri, "in_extract.pdf")
                src = f
                val n = PdfOps.pageCount(f)
                for (i in 0 until n) thumbs.add(PdfOps.renderPage(f, i, 0.5f))
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    b.grid.adapter = PageThumbAdapter(thumbs, selected) {
                        b.btnSave.isEnabled = selected.isNotEmpty()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@ExtractActivity,
                        "Errore: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun export() {
        val f = src ?: return
        val dialog = progress()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val o = File(cacheDir, "out_extract.pdf")
                PdfOps.extractPages(f, selected.toList(), o)
                out = o
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    val name = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALY).format(Date())
                    createPdf.launch("Estratto_$name.pdf")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@ExtractActivity,
                        "Errore: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun progress(): AlertDialog = AlertDialog.Builder(this)
        .setTitle(R.string.working)
        .setView(ProgressBar(this))
        .setCancelable(false)
        .show()

    override fun onDestroy() {
        super.onDestroy()
        thumbs.forEach { it?.recycle() }
    }
}
