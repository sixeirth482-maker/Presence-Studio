package com.presencestudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0F0F13)
val Surface = Color(0xFF1A1A22)
val SurfaceVariant = Color(0xFF24242E)
val Primary = Color(0xFF7C5CFF)
val PrimaryVariant = Color(0xFF5B3FD4)
val OnPrimary = Color.White
val TextPrimary = Color(0xFFE8E8ED)
val TextSecondary = Color(0xFFA0A0B0)
val TextTertiary = Color(0xFF6B6B7B)
val Success = Color(0xFF3DDC84)
val Error = Color(0xFFFF5C5C)
val Border = Color(0xFF2E2E3A)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = PrimaryVariant,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = Error
)

@Composable
fun PresenceStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
