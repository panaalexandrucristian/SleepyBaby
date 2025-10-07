package com.sleepybaby.core.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TensorFlow Lite based cry classifier (fallback)
 */
class TfliteCryClassifier(
    private val context: Context,
    private val modelPath: String = "cry_cnn_int8.tflite",
    private val threshold: Float = 0.5f
) : CryClassifier {

    private var interpreter: Interpreter? = null
    private val smoother = CrySmoother()

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            interpreter?.close()
            context.assets.open(modelPath).use { inputStream ->
                val modelBytes = inputStream.readBytes()
                val byteBuffer = ByteBuffer.allocateDirect(modelBytes.size)
                byteBuffer.order(ByteOrder.nativeOrder())
                byteBuffer.put(modelBytes)
                byteBuffer.rewind()

                interpreter = Interpreter(byteBuffer)
            }

            Log.d("TfliteCryClassifier", "TFLite model loaded successfully")
            true
        } catch (e: Exception) {
            Log.w("TfliteCryClassifier", "Failed to load TFLite model: ${e.message}")
            false
        }
    }

    override suspend fun classify(features: Array<FloatArray>): ClassificationResult =
        withContext(Dispatchers.Default) {
            val interp = interpreter ?: return@withContext createDefaultResult()

            try {
                val timeFrames = features.size
                val melBins = features.firstOrNull()?.size ?: 64

                // Prepare input
                val inputArray = Array(1) { Array(timeFrames) { FloatArray(melBins) } }
                for (i in features.indices) {
                    inputArray[0][i] = features[i]
                }

                // Prepare output
                val outputArray = Array(1) { FloatArray(3) }

                interp.run(inputArray, outputArray)

                val probs = outputArray[0] // [silence, noise, cry]

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
                Log.w("TfliteCryClassifier", "Classification failed: ${e.message}")
                createDefaultResult()
            }
        }

    override fun release() {
        interpreter?.close()
    }

    private fun createDefaultResult() = ClassificationResult(
        silenceProb = 1.0f,
        noiseProb = 0.0f,
        cryProb = 0.0f,
        predictedClass = ClassificationResult.CryClass.SILENCE
    )
}
