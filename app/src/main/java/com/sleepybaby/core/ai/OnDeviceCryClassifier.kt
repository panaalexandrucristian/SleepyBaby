package com.sleepybaby.core.ai

import android.content.Context
import android.util.Log

/**
 * On-device cry classifier with ONNX and TFLite fallback
 */
class OnDeviceCryClassifier(private val context: Context) : CryClassifier {

    private var activeClassifier: CryClassifier? = null
    private var backend: Backend = Backend.UNINITIALIZED

    enum class Backend {
        ONNX,
        TFLITE,
        ENERGY,
        UNINITIALIZED
    }

    override suspend fun initialize(): Boolean {
        // Try ONNX first
        val onnxClassifier = OnnxCryClassifier(context)
        if (onnxClassifier.initialize()) {
            activeClassifier = onnxClassifier
            backend = Backend.ONNX
            Log.d("OnDeviceCryClassifier", "Using ONNX classifier")
            return true
        }

        // Fallback to TFLite
        val tfliteClassifier = TfliteCryClassifier(context)
        if (tfliteClassifier.initialize()) {
            activeClassifier = tfliteClassifier
            backend = Backend.TFLITE
            Log.d("OnDeviceCryClassifier", "Using TFLite classifier")
            return true
        }

        // Final fallback: heuristic energy-based classifier
        val energyClassifier = EnergyCryClassifier()
        energyClassifier.initialize()
        activeClassifier = energyClassifier
        backend = Backend.ENERGY
        Log.w("OnDeviceCryClassifier", "Using energy-based heuristic classifier")
        return true
    }

    override suspend fun classify(features: Array<FloatArray>): ClassificationResult {
        return activeClassifier?.classify(features) ?: ClassificationResult(
            silenceProb = 1.0f,
            noiseProb = 0.0f,
            cryProb = 0.0f,
            predictedClass = ClassificationResult.CryClass.SILENCE
        )
    }

    override fun release() {
        activeClassifier?.release()
    }

    fun currentBackend(): Backend = backend
}
