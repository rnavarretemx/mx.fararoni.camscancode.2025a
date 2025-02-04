package com.example.camscancode

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import android.Manifest
import android.net.Uri
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File
import java.util.concurrent.Executor


class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var startScanButton: Button
    private var imageCapture: ImageCapture? = null
    private var barcodeCount = 0
    private var cameraProvider: ProcessCameraProvider? = null

    private var isScanning = false  // Variable para controlar si el escaneo está en progreso

    private lateinit var cameraExecutor: Executor
    private val detectedBarcodes = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar las vistas
        previewView = findViewById(R.id.previewView)
        startScanButton = findViewById(R.id.startScanButton)


        cameraExecutor = ContextCompat.getMainExecutor(this)

        // Configurar el clic del botón "Iniciar escaneo"
        startScanButton.setOnClickListener {
            when {
                startScanButton.text == "Detectar códigos" && !isScanning -> {
                    // Iniciar el escaneo
                    startScanButton.text = "Capturando..."
                    isScanning = true
                    captureImage()
                }
                startScanButton.text == "Volver a capturar" && !isScanning -> {
                    // Reactivar la cámara y cambiar el texto del botón
                    startScanButton.text = "Detectar códigos"
                    //isScanning = false
                    startCamera()  // Reactivamos la cámara
                }
                else -> {
                    // Si el texto es "Procesando...", no hacer nada
                }
            }
        }

        // Verificar permisos y empezar la cámara si se tienen
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            // Desvinculamos cualquier cámara previamente conectada
            cameraProvider?.unbindAll()
            // Vinculamos la cámara y la vista previa
            cameraProvider?.bindToLifecycle(this, cameraSelector, preview)

            // Configuramos el ImageCapture para capturar una imagen
            imageCapture = ImageCapture.Builder().build()
            // Vinculamos el ImageCapture
            cameraProvider?.bindToLifecycle(this, cameraSelector, imageCapture)

            /*if (scanningStarted) {
            startBarcodeAnalysis(cameraProvider!!)}*/

        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        // Crear un archivo de salida para guardar la imagen
        val photoFile = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Imagen capturada, ahora procesarla
                    Toast.makeText(this@MainActivity, "Foto capturada. Procesando...", Toast.LENGTH_SHORT).show()
                    analyzeCapturedImage(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Error al capturar la imagen.", Toast.LENGTH_SHORT).show()
                    resetScanState()  // Reiniciar estado en caso de error
                }
            }
        )
    }

    private fun analyzeCapturedImage(photoFile: File) {
        val image = InputImage.fromFilePath(this, Uri.fromFile(photoFile))

        val scanner = BarcodeScanning.getClient()

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodeCount = barcodes.size
                Log.d("BarcodeAnalyzer", "Cantidad de códigos detectados: $barcodeCount")

                // Limpiar la lista de códigos detectados antes de agregar los nuevos
                detectedBarcodes.clear()

                // Mostrar cada código detectado
                for (barcode in barcodes) {
                    val barcodeValue = barcode.rawValue
                    detectedBarcodes.add(barcodeValue ?: "Desconocido")
                    Log.e("BarcodeAnalyzer", "Código detectado: ${barcode.rawValue}")
                }

                // Mostrar cantidad de códigos detectados
                Toast.makeText(this, "Códigos detectados: $barcodeCount", Toast.LENGTH_SHORT).show()

                // Mostrar el AlertDialog con la lista de códigos
                showDetectedBarcodesDialog()

                // Detener la cámara después de capturar y analizar la imagen
                stopCamera()
                // Restaurar el texto del botón
                startScanButton.text = "Volver a capturar"
                isScanning = false

            }
            .addOnFailureListener { exception ->
                Log.e("BarcodeAnalyzer", "Error en el análisis de códigos: ${exception.message}", exception)
                Toast.makeText(this, "Error en el análisis de códigos.", Toast.LENGTH_SHORT).show()

                // Detener la cámara en caso de error
                stopCamera()

                // Restaurar el texto del botón
                startScanButton.text = "Volver a capturar"
                isScanning = false
            }
    }

    // Método para detener la cámara
    private fun stopCamera() {
        cameraProvider?.unbindAll() // Detener la cámara
    }

    private fun resetScanState() {
        startScanButton.text = "Detectar códigos"
        isScanning = false
    }

    private fun requestCameraPermission() {
        requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1
    }

    private fun showDetectedBarcodesDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Códigos detectados")

        val items = detectedBarcodes.map { "$it - Validar" }.toTypedArray()
        builder.setItems(items) { dialog, which ->
            // Acción de validación del código
            Log.d("BarcodeValidation", "Validando el código: ${detectedBarcodes[which]}")
            Toast.makeText(this, "Código ${detectedBarcodes[which]} validado", Toast.LENGTH_SHORT).show()
        }

        builder.setPositiveButton("Cerrar") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

}