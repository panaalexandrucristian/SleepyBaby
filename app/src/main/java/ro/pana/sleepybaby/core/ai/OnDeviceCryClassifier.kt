package ro.pana.sleepybaby.core.ai

import android.util.Log

/**
 * Simplified detector wrapper that uses the heuristic energy-based classifier.
 */
class OnDeviceCryClassifier : CryClassifier {

    private var activeClassifier: CryClassifier = EnergyCryClassifier()
    private var backend: Backend = Backend.UNINITIALIZED

    enum class Backend {
        ENERGY,
        UNINITIALIZED
    }

    override suspend fun initialize(): Boolean {
        activeClassifier.release()
        val energyClassifier = EnergyCryClassifier()
        energyClassifier.initialize()
        activeClassifier = energyClassifier
        backend = Backend.ENERGY
        Log.d("OnDeviceCryClassifier", "Using energy-based classifier")
        return true
    }

    override suspend fun classify(features: Array<FloatArray>): ClassificationResult {
        return activeClassifier.classify(features)
    }

    override fun release() {
        activeClassifier.release()
        backend = Backend.UNINITIALIZED
    }

    fun currentBackend(): Backend = backend
}
