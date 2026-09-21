package com.leechanghyun.autobattler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 유닛 코스트별 색상. 명세서 2장 "아트 에셋은 색상/도형 placeholder" 방침을 따른다.
 *
 * 원작 관행대로 1코스트부터 회색 → 초록 → 파랑 → 보라 → 금색 순으로 올라간다.
 */
val CostColors: List<Color> = listOf(
    Color(0xFF9E9E9E),
    Color(0xFF4CAF50),
    Color(0xFF2196F3),
    Color(0xFF9C27B0),
    Color(0xFFFFC107),
)

fun costColor(cost: Int): Color = CostColors.getOrElse(cost - 1) { CostColors.first() }

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFFFFC107),
    background = Color(0xFF12141A),
    surface = Color(0xFF1C1F27),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    secondary = Color(0xFFB8860B),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun AutoBattlerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
