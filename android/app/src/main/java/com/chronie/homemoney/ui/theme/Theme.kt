package com.chronie.homemoney.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 创建主题设置的LocalContext
val LocalThemeSettings = staticCompositionLocalOf<MutableState<ThemeSettings>> {
    error("No ThemeSettings provided")
}

// 主题设置数据类
class ThemeSettings(
    val useDynamicColor: Boolean,
    val primaryColor: Int
)

// 从SharedPreferences加载主题设置
fun loadThemeSettings(context: Context): ThemeSettings {
    val prefs: SharedPreferences = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
    val useDynamicColor = prefs.getBoolean("use_dynamic_color", true)
    val primaryColor = prefs.getInt("primary_color", 0xFF6750A4.toInt()) // 默认紫色
    return ThemeSettings(useDynamicColor, primaryColor)
}

// 更新主题设置到SharedPreferences
fun updateThemeSettings(context: Context, settings: ThemeSettings) {
    val prefs: SharedPreferences = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
    prefs.edit().apply {
        putBoolean("use_dynamic_color", settings.useDynamicColor)
        putInt("primary_color", settings.primaryColor)
    }.apply()
}

// 根据主题和颜色设置创建颜色方案
fun createColorScheme(
    context: Context,
    darkTheme: Boolean,
    dynamicColor: Boolean,
    primaryColor: Int
): ColorScheme {
    val userPrimaryColor = Color(primaryColor)
    
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // 使用系统生成的完整动态颜色方案
        // 当启用动态颜色时，应该完全使用系统生成的颜色方案
        return if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else {
        // 使用静态颜色方案，根据用户选择的primaryColor动态生成所有相关颜色
        return if (darkTheme) {
            // 深色模式：根据用户选择的primaryColor动态生成所有颜色
            // 使用RGB亮度公式判断颜色是否为深色
            val brightness = (userPrimaryColor.red * 0.299 + userPrimaryColor.green * 0.587 + userPrimaryColor.blue * 0.114)
            // 确保onPrimary与primary有足够的对比度
            val onPrimary = if (brightness < 0.5f) Color.White else Color.Black
            val primaryContainer = userPrimaryColor.copy(alpha = 0.2f)
            val onPrimaryContainer = userPrimaryColor
            
            // 从primaryColor派生出其他颜色
            val secondary = userPrimaryColor.copy(red = userPrimaryColor.red * 0.8f, green = userPrimaryColor.green * 0.8f, blue = userPrimaryColor.blue * 0.8f)
            val onSecondary = if (brightness < 0.5f) Color.White else Color.Black
            val secondaryContainer = secondary.copy(alpha = 0.2f)
            val onSecondaryContainer = secondary
            
            val tertiary = userPrimaryColor.copy(red = userPrimaryColor.red * 0.6f, green = userPrimaryColor.green * 0.6f, blue = userPrimaryColor.blue * 0.6f)
            val onTertiary = if (brightness < 0.5f) Color.White else Color.Black
            val tertiaryContainer = tertiary.copy(alpha = 0.2f)
            val onTertiaryContainer = tertiary
            
            // 保持错误颜色不变，因为它需要保持醒目
            val error = ErrorDark
            val onError = OnErrorDark
            val errorContainer = ErrorContainerDark
            val onErrorContainer = OnErrorContainerDark
            
            // 背景和表面颜色使用深色主题的默认值，但可以根据需要调整
            val background = Color(0xFF1C1B1F)
            val onBackground = Color(0xFFE6E1E5)
            val surface = Color(0xFF1C1B1F)
            val onSurface = Color(0xFFE6E1E5)
            
            // 基于用户选择的primaryColor生成次要颜色
            val surfaceVariant = userPrimaryColor.copy(alpha = 0.15f)
            val onSurfaceVariant = userPrimaryColor.copy(alpha = 0.8f)
            val outline = userPrimaryColor.copy(alpha = 0.3f)
            
            darkColorScheme(
                primary = userPrimaryColor,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = onSecondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                tertiary = tertiary,
                onTertiary = onTertiary,
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = onTertiaryContainer,
                error = error,
                onError = onError,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                // 使用基于用户primaryColor生成的次要颜色
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline
            )
        } else {
            // 浅色模式：根据用户选择的primaryColor动态生成所有颜色
            // 使用RGB亮度公式判断颜色是否为深色
            val brightness = (userPrimaryColor.red * 0.299 + userPrimaryColor.green * 0.587 + userPrimaryColor.blue * 0.114)
            // 确保onPrimary与primary有足够的对比度
            val onPrimary = if (brightness < 0.5f) Color.White else Color.Black
            val primaryContainer = userPrimaryColor.copy(alpha = 0.1f)
            val onPrimaryContainer = userPrimaryColor
            
            // 从primaryColor派生出其他颜色
            val secondary = userPrimaryColor.copy(red = userPrimaryColor.red * 0.8f, green = userPrimaryColor.green * 0.8f, blue = userPrimaryColor.blue * 0.8f)
            val onSecondary = if (brightness < 0.5f) Color.White else Color.Black
            val secondaryContainer = secondary.copy(alpha = 0.1f)
            val onSecondaryContainer = secondary
            
            val tertiary = userPrimaryColor.copy(red = userPrimaryColor.red * 0.6f, green = userPrimaryColor.green * 0.6f, blue = userPrimaryColor.blue * 0.6f)
            val onTertiary = if (brightness < 0.5f) Color.White else Color.Black
            val tertiaryContainer = tertiary.copy(alpha = 0.1f)
            val onTertiaryContainer = tertiary
            
            // 保持错误颜色不变，因为它需要保持醒目
            val error = Error
            val onError = OnError
            val errorContainer = ErrorContainer
            val onErrorContainer = OnErrorContainer
            
            // 背景和表面颜色使用浅色主题的默认值，但可以根据需要调整
            val background = Color(0xFFFDFBFF)
            val onBackground = Color(0xFF1C1B1F)
            val surface = Color(0xFFFDFBFF)
            val onSurface = Color(0xFF1C1B1F)
            
            // 基于用户选择的primaryColor生成次要颜色
            val surfaceVariant = userPrimaryColor.copy(alpha = 0.08f)
            val onSurfaceVariant = userPrimaryColor.copy(alpha = 0.7f)
            val outline = userPrimaryColor.copy(alpha = 0.2f)
            
            lightColorScheme(
                primary = userPrimaryColor,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = onSecondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                tertiary = tertiary,
                onTertiary = onTertiary,
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = onTertiaryContainer,
                error = error,
                onError = onError,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                // 使用基于用户primaryColor生成的次要颜色
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline
            )
        }
    }
}

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark
)

@Composable
fun HomeMoneyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // 创建可观察的主题设置
    val themeSettings = remember {
        mutableStateOf(loadThemeSettings(context))
    }
    
    // 当主题设置变化时，自动重新计算颜色方案
    val colorScheme = remember(themeSettings.value) {
        createColorScheme(
            context = context,
            darkTheme = darkTheme,
            dynamicColor = themeSettings.value.useDynamicColor,
            primaryColor = themeSettings.value.primaryColor
        )
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // 将主题设置提供给所有子组件
    CompositionLocalProvider(
        LocalThemeSettings provides themeSettings
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
