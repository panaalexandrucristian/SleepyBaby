package ro.pana.sleepybaby.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SleepyBlue,
    onPrimary = Color.White,
    primaryContainer = SleepyBlueDeep,
    onPrimaryContainer = Color.White,
    secondary = SleepyBlueSoft,
    onSecondary = Midnight900,
    tertiary = SleepyBlue,
    onTertiary = Color.White,
    background = Midnight900,
    onBackground = Color(0xFFEFF4FF),
    surface = Midnight800,
    onSurface = Color(0xFFE1E8FF),
    surfaceVariant = Midnight800,
    onSurfaceVariant = Mist200,
    outline = SleepyBlue.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = SleepyBlue,
    onPrimary = Color.White,
    primaryContainer = SleepyBlueDeep,
    onPrimaryContainer = Color.White,
    secondary = SleepyBlueDeep,
    onSecondary = Color.White,
    tertiary = SleepyBlue,
    onTertiary = Color.White,
    background = Mist100,
    onBackground = Color(0xFF101828),
    surface = Color.White,
    onSurface = Color(0xFF101828),
    surfaceVariant = Mist200,
    onSurfaceVariant = Color(0xFF475467),
    outline = Mist200
)

@Composable
fun SleepyBabyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
