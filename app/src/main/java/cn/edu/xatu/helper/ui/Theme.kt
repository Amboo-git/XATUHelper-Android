package cn.edu.xatu.helper.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable fun HelperTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) darkColorScheme(primary = Color(0xFFACC7FF), secondary = Color(0xFFBEC7DC))
    else lightColorScheme(primary = Color(0xFF315DA8), secondary = Color(0xFF555F74), background = Color(0xFFF9F9FF), surface = Color(0xFFF9F9FF))
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
