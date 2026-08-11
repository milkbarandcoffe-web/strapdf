package com.dz.strapdf

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dz.strapdf.databinding.ActivityScanBinding
import com.dz.strapdf.pdf.IoUtils
import com.dz.strapdf.pdf.SearchablePdfBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanActivity : AppCompatActivity() {

    private lateinit var b: ActivityScanBinding
    private var imageCapture: ImageCapture? = null
    private val shots = mutableListOf<File>()
    private var built: File? = null

    private val askCam = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCamera() else finish() }

    private val createPdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val f = built
        if (uri != null && f != null) {
            IoUtils.cacheToUri(this, f, uri)
            Toast.makeText(this, R.string.done, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityScanBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnShot.setOnClickListener { takeShot() }
        b.btnDone.setOnClickListener { buildPdf() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) startCamera()
        else askCam.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(b.preview.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                preview, imageCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takeShot() {
        val cap = imageCapture ?: return
        b.btnShot.isEnabled = false
        val f = File(cacheDir, "scan_${shots.size}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(f).build()
        cap.takePicture(opts, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    shots.add(f)
                    b.txtCount.text = "Pagine: ${shots.size}"
                    b.btnDone.isEnabled = true
                    b.btnShot.isEnabled = true
                }
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(this@ScanActivity,
                        "Errore scatto: ${e.message}", Toast.LENGTH_LONG).show()
                    b.btnShot.isEnabled = true
                }
            })
    }

    private fun buildPdf() {
        if (shots.isEmpty()) return
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.working)
            .setView(ProgressBar(this))
            .setCancelable(false)
            .show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val out = File(cacheDir, "out_scan.pdf")
                SearchablePdfBuilder.build(this@ScanActivity, shots, ocr = true, out = out)
                built = out
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    val name = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALY).format(Date())
                    createPdf.launch("Scansione_$name.pdf")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@ScanActivity,
                        "Errore: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
