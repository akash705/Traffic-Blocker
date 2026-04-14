package com.vedtechnologies.trafficblocker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vedtechnologies.trafficblocker.data.PrefsManager

private val DarkColorScheme = darkColorScheme(
    primary = Red500,
    secondary = Blue500,
    tertiary = Green500
)

private val LightColorScheme = lightColorScheme(
    primary = Red700,
    secondary = Blue500,
    tertiary = Green700
)

/** Global observable theme mode so changes apply immediately across the app */
object ThemeState {
    var themeMode by mutableStateOf(PrefsManager.THEME_SYSTEM)
}

@Composable
fun AppTrafficBlockerTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (ThemeState.themeMode) {
        PrefsManager.THEME_DARK -> true
        PrefsManager.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
