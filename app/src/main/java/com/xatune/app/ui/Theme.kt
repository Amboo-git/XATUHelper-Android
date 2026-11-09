package com.xatune.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable fun HelperTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) darkColorScheme(
        primary = Color(0xFFACC7FF), onPrimary = Color(0xFF002F64), primaryContainer = Color(0xFF234675), onPrimaryContainer = Color(0xFFD6E3FF),
        secondary = Color(0xFFBCC7DC), onSecondary = Color(0xFF273141), secondaryContainer = Color(0xFF3D4758), onSecondaryContainer = Color(0xFFD8E3F8),
        tertiary = Color(0xFFBACCB0), tertiaryContainer = Color(0xFF3C4B34), onTertiaryContainer = Color(0xFFD6E8CC),
        background = Color(0xFF101318), surface = Color(0xFF101318), onSurface = Color(0xFFE0E2E9), onSurfaceVariant = Color(0xFFC3C6D0),
        surfaceContainerLowest = Color(0xFF0B0E13), surfaceContainerLow = Color(0xFF191C22), surfaceContainer = Color(0xFF1D2026),
        surfaceContainerHigh = Color(0xFF282A31), surfaceContainerHighest = Color(0xFF32353C), outlineVariant = Color(0xFF434751))
    else lightColorScheme(
        primary = Color(0xFF315DA8), onPrimary = Color.White, primaryContainer = Color(0xFFD9E5FF), onPrimaryContainer = Color(0xFF12315F),
        secondary = Color(0xFF555F74), secondaryContainer = Color(0xFFDDE5F7), onSecondaryContainer = Color(0xFF182336),
        tertiary = Color(0xFF526448), tertiaryContainer = Color(0xFFD5E9C7), onTertiaryContainer = Color(0xFF172B10),
        background = Color(0xFFF8F9FF), surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C22), onSurfaceVariant = Color(0xFF434751),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F3FA), surfaceContainer = Color(0xFFEAEEF6),
        surfaceContainerHigh = Color(0xFFE4E8F0), surfaceContainerHighest = Color(0xFFDEE2EB), outlineVariant = Color(0xFFC3C8D3))
    MaterialTheme(colorScheme = colors, typography = Typography(), shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp)), content = content)
}
