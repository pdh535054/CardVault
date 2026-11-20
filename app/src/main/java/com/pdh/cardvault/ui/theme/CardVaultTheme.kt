package com.pdh.cardvault.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF315B9C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E4FF),
    onPrimaryContainer = Color(0xFF142E58),
    inversePrimary = Color(0xFFAFC6FF),
    secondary = Color(0xFF555F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE3EE),
    onSecondaryContainer = Color(0xFF303A49),
    tertiary = Color(0xFF35665F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCECE3),
    onTertiaryContainer = Color(0xFF194B45),
    background = Color(0xFFF6F7FA),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFFAFBFE),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE1E5EC),
    onSurfaceVariant = Color(0xFF454B55),
    surfaceTint = Color(0xFF315B9C),
    inverseSurface = Color(0xFF2F3035),
    inverseOnSurface = Color(0xFFF1F1F6),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF757B85),
    outlineVariant = Color(0xFFC5CAD3),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFAFBFE),
    surfaceContainer = Color(0xFFEEEFF3),
    surfaceContainerHigh = Color(0xFFE8E9EE),
    surfaceContainerHighest = Color(0xFFE2E3E8),
    surfaceContainerLow = Color(0xFFF4F5F9),
    surfaceContainerLowest = Color.White,
    surfaceDim = Color(0xFFDADBE0),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB4CBFF),
    onPrimary = Color(0xFF0C244E),
    primaryContainer = Color(0xFF1F355C),
    onPrimaryContainer = Color(0xFFDCE7FF),
    inversePrimary = Color(0xFF315B9C),
    secondary = Color(0xFFC4CAD6),
    onSecondary = Color(0xFF262B35),
    secondaryContainer = Color(0xFF303744),
    onSecondaryContainer = Color(0xFFE2E6F0),
    tertiary = Color(0xFF9DE4DA),
    onTertiary = Color(0xFF063832),
    tertiaryContainer = Color(0xFF1E4640),
    onTertiaryContainer = Color(0xFFCCF6EF),
    background = Color(0xFF06080C),
    onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF0A0D13),
    onSurface = Color(0xFFF2F4F8),
    surfaceVariant = Color(0xFF1E2531),
    onSurfaceVariant = Color(0xFFB7C0CE),
    surfaceTint = Color(0xFFB4CBFF),
    inverseSurface = Color(0xFFE3E6ED),
    inverseOnSurface = Color(0xFF282C34),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF858D9B),
    outlineVariant = Color(0xFF313946),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2C3340),
    surfaceContainer = Color(0xFF121721),
    surfaceContainerHigh = Color(0xFF181F2C),
    surfaceContainerHighest = Color(0xFF202938),
    surfaceContainerLow = Color(0xFF0C1017),
    surfaceContainerLowest = Color(0xFF040609),
    surfaceDim = Color(0xFF06080C),
)

private val CardVaultShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val BaseTypography = Typography()

private val CardVaultTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.2).sp,
    ),
    displaySmall = BaseTypography.displaySmall.copy(
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.7).sp,
    ),
    headlineMedium = BaseTypography.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = BaseTypography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = BaseTypography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseTypography.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(lineHeight = 21.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun CardVaultTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = CardVaultTypography,
        shapes = CardVaultShapes,
        content = content,
    )
}
