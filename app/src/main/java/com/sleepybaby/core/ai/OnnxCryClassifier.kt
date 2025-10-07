package com.sleepybaby.core.ai

import ai.onnxruntime.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ONNX Runtime based cry classifier
 */
class OnnxCryClassifier(
    private val context: Context,
    private val modelPath: String = "cry_cnn_int8.onnx",
    private val threshold: Float = 0.5f
) : CryClassifier {

    private val ortEnvironment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private var inputName: String = "input"
    private val smoother = CrySmoother()

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            ortSession?.close()
            val sessionOptions = OrtSession.SessionOptions()
            try {
                context.assets.open(modelPath).use { inputStream ->
                    val modelBytes = inputStream.readBytes()
                    ortSession = ortEnvironment.createSession(modelBytes, sessionOptions)
                }
            } finally {
                sessionOptions.close()
            }
            inputName = ortSession?.inputNames?.firstOrNull() ?: "input"

            Log.d("OnnxCryClassifier", "ONNX model loaded successfully")
            true
        } catch (e: Exception) {
            Log.w("OnnxCryClassifier", "Failed to load ONNX model: ${e.message}")
            false
        }
    }

    override suspend fun classify(features: Array<FloatArray>): ClassificationResult =
        withContext(Dispatchers.Default) {
            val session = ortSession ?: return@withContext createDefaultResult()

            try {
                // Prepare input tensor [1, timeFrames, melBins]
                val timeFrames = features.size
                val melBins = features.firstOrNull()?.size ?: 64
                val inputData = FloatArray(timeFrames * melBins)

                var idx = 0
                for (frame in features) {
                    for (value in frame) {
                        inputData[idx++] = value
                    }
                }

                // Convert to FloatBuffer for ONNX Runtime
                val inputBuffer = java.nio.FloatBuffer.wrap(inputData)
                val inputTensor = OnnxTensor.createTensor(
                    ortEnvironment,
                    inputBuffer,
                    longArrayOf(1, timeFrames.toLong(), melBins.toLong())
                )

                val probs = inputTensor.use {
                    session.run(mapOf(inputName to it)).use { outputs ->
                        val outputTensor = outputs[0].value as Array<FloatArray>
                        outputTensor[0]
                    }
                }

                val result = ClassificationResult(
                    silenceProb = probs[0],
                    noiseProb = probs[1],
                    cryProb = probs[2],
                    predictedClass = when {
                        probs[2] > threshold -> ClassificationResult.CryClass.BABY_CRY
                        probs[1] > probs[0] -> ClassificationResult.CryClass.NOISE
                        else -> ClassificationResult.CryClass.SILENCE
                    }
                )

                smoother.smooth(result)
            } catch (e: Exception) {
                Log.w("OnnxCryClassifier", "Classification failed: ${e.message}")
                createDefaultResult()
            }
        }

    override fun release() {
        ortSession?.close()
        ortSession = null
    }

    private fun createDefaultResult() = ClassificationResult(
        silenceProb = 1.0f,
        noiseProb = 0.0f,
        cryProb = 0.0f,
        predictedClass = ClassificationResult.CryClass.SILENCE
    )
}
