package com.example.textrecognizer

import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.textrecognizer.databinding.FragmentTextRecognizerBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@ExperimentalGetImage
class TextRecognizerFragment : Fragment() {

    private val viewModel: TextRecognizeViewModel by viewModels()

    private var _binding: FragmentTextRecognizerBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textAnalyzer: TextAnalyzer

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextRecognizerBinding.inflate(inflater, container, false)

        cameraExecutor = Executors.newSingleThreadExecutor()

        return binding.root
    }

    private var detectedCouponNumberReceive: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setTextAnalyzer()

        viewModel.textNumberSet.observe(viewLifecycleOwner) { items ->
            detectedCouponNumberReceive = items.isEmpty()
            binding.textScanResult.text = "Recognized Number: $items"
        }
    }

    private inner class TextAnalyzer(
        val onTextFound: (String) -> Unit,
        val formatFilter: ((String) -> String)? = null
    ) : ImageAnalysis.Analyzer {
        private var lastAnalyzedTime = 0L
        private var processingFrequency = 2

        override fun analyze(imageProxy: ImageProxy) {
            val interval = 1000 / processingFrequency
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAnalyzedTime < interval) {
                imageProxy.close()
                return
            }
            lastAnalyzedTime = currentTime

            val mediaImage = imageProxy.image ?: run {
                imageProxy.close()
                return
            }

            val inputImage =
                InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    // formatFilter 함수가 있으면 적용, 없으면 원본 사용
                    val filteredText = formatFilter?.invoke(rawText) ?: rawText
                    if (filteredText.isNotEmpty()) {
                        onTextFound(filteredText)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("TextRecognizer", "Text recognition failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }

        fun setProcessingFrequency(frequency: Int) {
            processingFrequency = frequency.coerceAtLeast(1) // Ensure at least 1 time per second
        }
    }

    private fun setTextAnalyzer() {
        textAnalyzer = TextAnalyzer(
            onTextFound = { resultText ->
                activity?.runOnUiThread {
                    viewModel.updateCouponNumber(resultText)
                }
            },
            formatFilter = { rawText ->
                val regex = Regex("\\d{4}") // num 4 digits
                regex.findAll(rawText).joinToString(separator = "\n") { it.value }
            }
        )
        textAnalyzer.setProcessingFrequency(1) // Process 3 times per second

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, textAnalyzer)
                }


            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner, cameraSelector, preview, imageAnalysis
            )

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll() // Unbind all use cases to release resources
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    override fun onResume() {
        super.onResume()
        startCamera() // Start the camera when the fragment becomes visible
    }

    override fun onPause() {
        super.onPause()
        stopCamera() // Stop the camera when the fragment is no longer visible
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}