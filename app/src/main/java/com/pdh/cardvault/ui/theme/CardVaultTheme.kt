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
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF102A56),
    primaryContainer = Color(0xFF243A62),
    onPrimaryContainer = Color(0xFFD9E4FF),
    inversePrimary = Color(0xFF315B9C),
    secondary = Color(0xFFC2C7D0),
    onSecondary = Color(0xFF292E36),
    secondaryContainer = Color(0xFF343A44),
    onSecondaryContainer = Color(0xFFE0E3EA),
    tertiary = Color(0xFFA7D8D0),
    onTertiary = Color(0xFF073731),
    tertiaryContainer = Color(0xFF254B46),
    onTertiaryContainer = Color(0xFFD2F4ED),
    background = Color(0xFF07090C),
    onBackground = Color(0xFFEFF1F5),
    surface = Color(0xFF0B0E12),
    onSurface = Color(0xFFEFF1F5),
    surfaceVariant = Color(0xFF202630),
    onSurfaceVariant = Color(0xFFC2C8D2),
    surfaceTint = Color(0xFFAFC6FF),
    inverseSurface = Color(0xFFE2E5EB),
    inverseOnSurface = Color(0xFF2B2F35),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8C929D),
    outlineVariant = Color(0xFF3B424D),
    scrim = Color.Black,
    surfaceBright = Color(0xFF30343B),
    surfaceContainer = Color(0xFF14181E),
    surfaceContainerHigh = Color(0xFF1A1F27),
    surfaceContainerHighest = Color(0xFF222833),
    surfaceContainerLow = Color(0xFF0F1217),
    surfaceContainerLowest = Color(0xFF05070A),
    surfaceDim = Color(0xFF07090C),
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
