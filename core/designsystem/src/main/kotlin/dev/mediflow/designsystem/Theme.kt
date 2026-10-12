package dev.mediflow.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp

object MediColors {
    val Purple = Color(0xFF6D5795)
    val Lavender = Color(0xFFEDE7F5)
    val Ink = Color(0xFF302A3E)
    val Muted = Color(0xFF686171)
    val Ivory = Color(0xFFF8F7F3)
    val Sage = Color(0xFF487462)
    val SageSoft = Color(0xFFE5EFE7)
    val Peach = Color(0xFF92572F)
    val PeachSoft = Color(0xFFFAEDDF)
    val Blue = Color(0xFF567B9B)
    val BlueSoft = Color(0xFFE6EEF6)

    fun accent(index: Int) = listOf(Purple, Sage, Peach, Blue)[index.coerceIn(0, 3)]

    fun soft(index: Int) = listOf(Lavender, SageSoft, PeachSoft, BlueSoft)[index.coerceIn(0, 3)]
}

private val light =
    lightColorScheme(
        primary = MediColors.Purple,
        onPrimary = Color.White,
        primaryContainer = MediColors.Lavender,
        onPrimaryContainer = MediColors.Ink,
        secondary = MediColors.Sage,
        secondaryContainer = MediColors.SageSoft,
        onSecondaryContainer = Color(0xFF29483C),
        background = MediColors.Ivory,
        onBackground = MediColors.Ink,
        surface = Color(0xFFFFFEFC),
        onSurface = MediColors.Ink,
        surfaceVariant = Color(0xFFF0EDF3),
        onSurfaceVariant = MediColors.Muted,
        outline = Color(0xFFB9B2C3),
        outlineVariant = Color(0xFFE5E0E9),
        error = Color(0xFFA13E47),
    )
private val dark =
    darkColorScheme(
        primary = Color(0xFFD0BDF1),
        onPrimary = Color(0xFF352445),
        primaryContainer = Color(0xFF453554),
        onPrimaryContainer = Color(0xFFF0E6FF),
        secondary = Color(0xFFACCFBC),
        secondaryContainer = Color(0xFF284236),
        onSecondaryContainer = Color(0xFFD5EBDF),
        background = Color(0xFF1B1921),
        onBackground = Color(0xFFF2EDF6),
        surface = Color(0xFF25212D),
        onSurface = Color(0xFFF2EDF6),
        surfaceVariant = Color(0xFF332D3C),
        onSurfaceVariant = Color(0xFFBBB2C8),
        outline = Color(0xFF938B9F),
        outlineVariant = Color(0xFF463D50),
    )

@Composable
fun MediFlowTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val family =
        FontFamily(
            Font(R.font.manrope_regular),
            Font(R.font.manrope_semibold, FontWeight.SemiBold),
            Font(R.font.manrope_bold, FontWeight.Bold),
        )
    val typography =
        Typography(
            displaySmall =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 34.sp,
                    lineHeight = 42.sp,
                    letterSpacing = (-1).sp,
                ),
            headlineLarge =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    lineHeight = 38.sp,
                    letterSpacing = (-0.8).sp,
                ),
            headlineMedium =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    letterSpacing = (-0.5).sp,
                ),
            headlineSmall =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                ),
            titleLarge =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                ),
            titleMedium =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                ),
            titleSmall =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
            bodyLarge =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 25.sp,
                ),
            bodyMedium =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                ),
            bodySmall =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
            labelLarge =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
            labelMedium =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
            labelSmall =
                TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                ),
        )
    MaterialTheme(
        colorScheme = if (darkTheme) dark else light,
        typography = typography,
        content = content,
    )
}
