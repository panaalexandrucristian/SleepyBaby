package ro.pana.sleepybaby.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ro.pana.sleepybaby.data.SettingsRepository
import ro.pana.sleepybaby.domain.controller.DetectionServiceLauncher
import ro.pana.sleepybaby.domain.usecase.AutomationConfigUseCases
import ro.pana.sleepybaby.domain.usecase.MonitoringUseCases
import ro.pana.sleepybaby.domain.usecase.ShushUseCases
import ro.pana.sleepybaby.domain.usecase.TutorialUseCases

class SleepyBabyViewModelFactory(
    private val application: Application,
    private val repository: SettingsRepository,
    private val detectionServiceLauncher: DetectionServiceLauncher
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SleepyBabyViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        val viewModel = SleepyBabyViewModel(
            application = application,
            configUseCases = AutomationConfigUseCases(repository),
            monitoringUseCases = MonitoringUseCases(repository, detectionServiceLauncher),
            tutorialUseCases = TutorialUseCases(repository),
            shushUseCases = ShushUseCases()
        )

        return viewModel as T
    }
}
