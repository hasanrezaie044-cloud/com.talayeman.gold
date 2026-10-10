package com.talayeman.gold.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.talayeman.gold.domain.model.ThemeMode

// ---- Brand palette: deep navy + gold (matches the app icon and login screen) ----
val GoldPrimary = Color(0xFF9C6D00)
val GoldPrimaryDark = Color(0xFFE8BE55)
val GoldSecondary = Color(0xFF3B4A6B)
val GoldContainer = Color(0xFFFFE4A8)
val GoldContainerDark = Color(0xFF4A3600)

val NavyDeep = Color(0xFF060D1B)
val NavyCard = Color(0xFF111D31)
val GoldBright = Color(0xFFF6D77A)
val GoldMid = Color(0xFFE2B33F)
val GoldDeep = Color(0xFFB98A1F)

private val LightColorScheme = lightColorScheme(
    primary = GoldPrimary,
    onPrimary = Color.White,
    primaryContainer = GoldContainer,
    onPrimaryContainer = Color(0xFF2B1D00),
    secondary = GoldSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE4F7),
    onSecondaryContainer = Color(0xFF0F1A33),
    tertiary = Color(0xFF00796B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFF4EC),
    onTertiaryContainer = Color(0xFF00201B),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF5F6FA),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE9ECF4),
    onSurfaceVariant = Color(0xFF5B6475),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF0F7),
    surfaceContainerHigh = Color(0xFFE7EAF3),
    surfaceContainerHighest = Color(0xFFDFE3EE),
    outline = Color(0xFF8A93A6),
    outlineVariant = Color(0xFFDDE1EA),
    error = Color(0xFFD93025),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1DE),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimaryDark,
    onPrimary = Color(0xFF2B1D00),
    primaryContainer = GoldContainerDark,
    onPrimaryContainer = GoldContainer,
    secondary = Color(0xFFB7C6EA),
    onSecondary = Color(0xFF14213D),
    secondaryContainer = Color(0xFF2A3A5C),
    onSecondaryContainer = Color(0xFFDCE4F7),
    tertiary = Color(0xFF6FD6C5),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005046),
    onTertiaryContainer = Color(0xFFCFF4EC),
    background = NavyDeep,
    onBackground = Color(0xFFE7EBF5),
    surface = NavyDeep,
    onSurface = Color(0xFFE7EBF5),
    surfaceVariant = Color(0xFF1C2B45),
    onSurfaceVariant = Color(0xFFA9B3C7),
    surfaceContainerLowest = Color(0xFF040A15),
    surfaceContainerLow = Color(0xFF0C1626),
    surfaceContainer = NavyCard,
    surfaceContainerHigh = Color(0xFF16243B),
    surfaceContainerHighest = Color(0xFF1C2B45),
    outline = Color(0xFF6F7A90),
    outlineVariant = Color(0xFF2A3650),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF5C1A17),
    onErrorContainer = Color(0xFFFFDAD6)
)

/** Positive / negative amounts. Contrast-checked for both light and dark backgrounds. */
@Composable
fun profitColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF5BD98B) else Color(0xFF1B8A4B)

@Composable
fun lossColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFF7B72) else Color(0xFFD93025)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Vazirmatn (OFL) is used automatically if the TTF files are present in assets/fonts
 * (scripts/fetch-fonts.sh downloads them; the CI workflow runs it). Otherwise the system font,
 * which already renders Persian, is used.
 */
fun loadAppFontFamily(context: Context): FontFamily = try {
    val files = context.assets.list("fonts")?.toList().orEmpty()
    fun find(weight: String): String? =
        files.firstOrNull { it.contains(weight, ignoreCase = true) && it.endsWith(".ttf", ignoreCase = true) }
            ?.takeIf { name -> context.assets.open("fonts/$name").use { it.available() } > 20_000 }
    val regular = find("Regular")
    if (regular == null) {
        FontFamily.Default
    } else {
        val list = mutableListOf(Font("fonts/$regular", context.assets, FontWeight.Normal))
        find("Medium")?.let { list += Font("fonts/$it", context.assets, FontWeight.Medium) }
        find("Bold")?.let {
            list += Font("fonts/$it", context.assets, FontWeight.SemiBold)
            list += Font("fonts/$it", context.assets, FontWeight.Bold)
        }
        FontFamily(list)
    }
} catch (e: Exception) {
    FontFamily.Default
}

/**
 * Persian text must NOT use letter-spacing (it breaks the joining of letters), so every style is
 * defined explicitly with letterSpacing = 0 and slightly taller line heights for Persian script.
 */
fun buildTypography(f: FontFamily): Typography {
    fun s(size: Int, line: Int, w: FontWeight) = TextStyle(
        fontFamily = f, fontSize = size.sp, lineHeight = line.sp, fontWeight = w, letterSpacing = 0.sp
    )
    return Typography(
        displayLarge = s(44, 54, FontWeight.Bold),
        displayMedium = s(38, 48, FontWeight.Bold),
        displaySmall = s(32, 42, FontWeight.Bold),
        headlineLarge = s(30, 40, FontWeight.Bold),
        headlineMedium = s(26, 36, FontWeight.Bold),
        headlineSmall = s(22, 32, FontWeight.Bold),
        titleLarge = s(20, 30, FontWeight.Bold),
        titleMedium = s(16, 26, FontWeight.SemiBold),
        titleSmall = s(14, 22, FontWeight.SemiBold),
        bodyLarge = s(16, 26, FontWeight.Normal),
        bodyMedium = s(14, 23, FontWeight.Normal),
        bodySmall = s(12, 19, FontWeight.Normal),
        labelLarge = s(14, 22, FontWeight.Medium),
        labelMedium = s(12, 18, FontWeight.Medium),
        labelSmall = s(11, 16, FontWeight.Medium)
    )
}

@Composable
fun GoldTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val context = LocalContext.current
    val typography = remember { buildTypography(loadAppFontFamily(context)) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = AppShapes,
        content = content
    )
}

val VazirmatnFamily: FontFamily = FontFamily.Default
val Typography: Typography = buildTypography(FontFamily.Default)
