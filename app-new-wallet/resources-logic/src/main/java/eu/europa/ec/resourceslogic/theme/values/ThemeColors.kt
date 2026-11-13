/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.resourceslogic.theme.values

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import eu.europa.ec.resourceslogic.theme.AppearanceMode
import eu.europa.ec.resourceslogic.theme.ThemeAppearance
import eu.europa.ec.resourceslogic.theme.templates.ThemeColorsTemplate

class ThemeColors {
    companion object {
        private const val white: Long = 0xFFFFFFFF
        private const val black: Long = 0xFF000000

        // Light theme base colors palette.
        private const val eudiw_theme_light_primary: Long = 0xFFFFFFFF // botones principales: blanco sobre marino
        private const val eudiw_theme_light_onPrimary: Long = 0xFF0F1F3D
        private const val eudiw_theme_light_primaryContainer: Long = 0xFFE3E8F2
        private const val eudiw_theme_light_onPrimaryContainer: Long = 0xFF0F1F3D
        private const val eudiw_theme_light_secondary: Long = 0xFFD5DCEA
        private const val eudiw_theme_light_onSecondary: Long = 0xFF0F1F3D
        private const val eudiw_theme_light_secondaryContainer: Long = 0xFFC9D3E8
        private const val eudiw_theme_light_onSecondaryContainer: Long = 0xFF0F1F3D
        private const val eudiw_theme_light_tertiary: Long = 0xFF3E7BFA
        private const val eudiw_theme_light_onTertiary: Long = 0xFFFFFFFF
        private const val eudiw_theme_light_tertiaryContainer: Long = 0xFF1F3A73
        private const val eudiw_theme_light_onTertiaryContainer: Long = 0xFFDCE6FF
        private const val eudiw_theme_light_error: Long = 0xFFFF8A80
        private const val eudiw_theme_light_onError: Long = 0xFF3B0A07
        private const val eudiw_theme_light_errorContainer: Long = 0xFF5C1A17
        private const val eudiw_theme_light_onErrorContainer: Long = 0xFFFFDAD6
        private const val eudiw_theme_light_surface: Long = 0xFF0F1F3D // fondo de la app: azul marino
        private const val eudiw_theme_light_onSurface: Long = 0xFFFFFFFF
        private const val eudiw_theme_light_background: Long = eudiw_theme_light_surface
        private const val eudiw_theme_light_onBackground: Long =
            eudiw_theme_light_onSurface
        private const val eudiw_theme_light_surfaceVariant: Long = 0xFF1F3463
        private const val eudiw_theme_light_onSurfaceVariant: Long = 0xFFC9D3EA
        private const val eudiw_theme_light_outline: Long = 0xFF7B8DB5
        private const val eudiw_theme_light_outlineVariant: Long = 0xFF33498A
        private const val eudiw_theme_light_scrim: Long = black
        private const val eudiw_theme_light_inverseSurface: Long = 0xFFE3E8F2
        private const val eudiw_theme_light_inverseOnSurface: Long = 0xFF0F1F3D
        private const val eudiw_theme_light_inversePrimary: Long = 0xFF0F1F3D
        private const val eudiw_theme_light_surfaceDim: Long = 0xFF0A1630
        private const val eudiw_theme_light_surfaceBright: Long = 0xFF2A417A
        internal const val eudiw_theme_light_surfaceContainerLowest: Long = 0xFF1A2F5E // tarjetas: marino más claro
        private const val eudiw_theme_light_surfaceContainerLow: Long = 0xFF162A55
        private const val eudiw_theme_light_surfaceContainer: Long = 0xFF1F3463
        private const val eudiw_theme_light_surfaceContainerHigh: Long = 0xFF263D70
        private const val eudiw_theme_light_surfaceContainerHighest: Long = 0xFF2E4680
        private const val eudiw_theme_light_surfaceTint: Long = eudiw_theme_light_surface

        // Light theme fixed accent roles (identical in dark as well).
        private const val eudiw_theme_light_primaryFixed: Long = 0xFFE3E8F2
        private const val eudiw_theme_light_primaryFixedDim: Long = 0xFFC9D3E8
        private const val eudiw_theme_light_onPrimaryFixed: Long = 0xFF0F1F3D
        private const val eudiw_theme_light_onPrimaryFixedVariant: Long = 0xFF22386A

        private const val eudiw_theme_light_secondaryFixed: Long = 0xFFD5DCEA
        private const val eudiw_theme_light_secondaryFixedDim: Long = 0xFFB8C4DE
        private const val eudiw_theme_light_onSecondaryFixed: Long = 0xFF0F1F3D
        private const val eudiw_theme_light_onSecondaryFixedVariant: Long = 0xFF22386A

        private const val eudiw_theme_light_tertiaryFixed: Long = 0xFFDCE6FF
        private const val eudiw_theme_light_tertiaryFixedDim: Long = 0xFFB5C9FF
        private const val eudiw_theme_light_onTertiaryFixed: Long = 0xFF001B4D
        private const val eudiw_theme_light_onTertiaryFixedVariant: Long = 0xFF1F3A73

        // Light theme extra colors palette.
        internal const val eudiw_theme_light_success: Long = 0xFF7EDC8A
        internal const val eudiw_theme_light_warning: Long = 0xFFFFB689
        internal const val eudiw_theme_light_pending: Long = 0xFFFFC46B
        internal const val eudiw_theme_light_divider: Long = 0xFF2C4278

        // Dark theme base colors palette.
        // Dark palette kept light-pastel too, so any isSystemInDarkTheme() path never goes black.
        private const val eudiw_theme_dark_primary: Long = 0xFFFFFFFF // botones principales: blanco sobre marino
        private const val eudiw_theme_dark_onPrimary: Long = 0xFF0F1F3D
        private const val eudiw_theme_dark_primaryContainer: Long = 0xFFE3E8F2
        private const val eudiw_theme_dark_onPrimaryContainer: Long = 0xFF0F1F3D
        private const val eudiw_theme_dark_secondary: Long = 0xFFD5DCEA
        private const val eudiw_theme_dark_onSecondary: Long = 0xFF0F1F3D
        private const val eudiw_theme_dark_secondaryContainer: Long = 0xFFC9D3E8
        private const val eudiw_theme_dark_onSecondaryContainer: Long = 0xFF0F1F3D
        private const val eudiw_theme_dark_tertiary: Long = 0xFF3E7BFA
        private const val eudiw_theme_dark_onTertiary: Long = 0xFFFFFFFF
        private const val eudiw_theme_dark_tertiaryContainer: Long = 0xFF1F3A73
        private const val eudiw_theme_dark_onTertiaryContainer: Long = 0xFFDCE6FF
        private const val eudiw_theme_dark_error: Long = 0xFFFF8A80
        private const val eudiw_theme_dark_onError: Long = 0xFF3B0A07
        private const val eudiw_theme_dark_errorContainer: Long = 0xFF5C1A17
        private const val eudiw_theme_dark_onErrorContainer: Long = 0xFFFFDAD6
        private const val eudiw_theme_dark_surface: Long = 0xFF0F1F3D // gris pastel
        private const val eudiw_theme_dark_onSurface: Long = 0xFFFFFFFF
        private const val eudiw_theme_dark_background: Long = eudiw_theme_dark_surface
        private const val eudiw_theme_dark_onBackground: Long = eudiw_theme_dark_onSurface
        private const val eudiw_theme_dark_surfaceVariant: Long = 0xFF1F3463
        private const val eudiw_theme_dark_onSurfaceVariant: Long = 0xFFC9D3EA
        private const val eudiw_theme_dark_outline: Long = 0xFF7B8DB5
        private const val eudiw_theme_dark_outlineVariant: Long = 0xFF33498A
        private const val eudiw_theme_dark_scrim: Long = black
        private const val eudiw_theme_dark_inverseSurface: Long = 0xFFE3E8F2
        private const val eudiw_theme_dark_inverseOnSurface: Long = 0xFF0F1F3D
        private const val eudiw_theme_dark_inversePrimary: Long = 0xFF0F1F3D
        private const val eudiw_theme_dark_surfaceDim: Long = 0xFF0A1630
        private const val eudiw_theme_dark_surfaceBright: Long = 0xFF2A417A
        private const val eudiw_theme_dark_surfaceContainerLowest: Long = 0xFF1A2F5E
        private const val eudiw_theme_dark_surfaceContainerLow: Long = 0xFF162A55
        internal const val eudiw_theme_dark_surfaceContainer: Long = 0xFF1F3463
        private const val eudiw_theme_dark_surfaceContainerHigh: Long = 0xFF263D70
        private const val eudiw_theme_dark_surfaceContainerHighest: Long = 0xFF2E4680
        private const val eudiw_theme_dark_surfaceTint: Long = eudiw_theme_dark_surface

        // Dark theme fixed accent roles (same values as light).
        private const val eudiw_theme_dark_primaryFixed: Long = 0xFFE3E8F2
        private const val eudiw_theme_dark_primaryFixedDim: Long = 0xFFC9D3E8
        private const val eudiw_theme_dark_onPrimaryFixed: Long = 0xFF0F1F3D
        private const val eudiw_theme_dark_onPrimaryFixedVariant: Long = 0xFF22386A

        private const val eudiw_theme_dark_secondaryFixed: Long = 0xFFD5DCEA
        private const val eudiw_theme_dark_secondaryFixedDim: Long = 0xFFB8C4DE
        private const val eudiw_theme_dark_onSecondaryFixed: Long = 0xFF0F1F3D
        private const val eudiw_theme_dark_onSecondaryFixedVariant: Long = 0xFF22386A

        private const val eudiw_theme_dark_tertiaryFixed: Long = 0xFFDCE6FF
        private const val eudiw_theme_dark_tertiaryFixedDim: Long = 0xFFB5C9FF
        private const val eudiw_theme_dark_onTertiaryFixed: Long = 0xFF001B4D
        private const val eudiw_theme_dark_onTertiaryFixedVariant: Long = 0xFF1F3A73

        // Dark theme extra colors palette.
        internal const val eudiw_theme_dark_success: Long = 0xFF7EDC8A
        internal const val eudiw_theme_dark_warning: Long = 0xFFFFB689
        internal const val eudiw_theme_dark_pending: Long = 0xFFFFC46B
        internal const val eudiw_theme_dark_divider: Long = 0xFF2C4278

        const val eudiw_theme_light_background_preview: Long =
            eudiw_theme_light_surface
        const val eudiw_theme_dark_background_preview: Long =
            eudiw_theme_dark_surface

        internal val lightColors = ThemeColorsTemplate(
            primary = eudiw_theme_light_primary,
            onPrimary = eudiw_theme_light_onPrimary,
            primaryContainer = eudiw_theme_light_primaryContainer,
            onPrimaryContainer = eudiw_theme_light_onPrimaryContainer,
            secondary = eudiw_theme_light_secondary,
            onSecondary = eudiw_theme_light_onSecondary,
            secondaryContainer = eudiw_theme_light_secondaryContainer,
            onSecondaryContainer = eudiw_theme_light_onSecondaryContainer,
            tertiary = eudiw_theme_light_tertiary,
            onTertiary = eudiw_theme_light_onTertiary,
            tertiaryContainer = eudiw_theme_light_tertiaryContainer,
            onTertiaryContainer = eudiw_theme_light_onTertiaryContainer,
            error = eudiw_theme_light_error,
            errorContainer = eudiw_theme_light_errorContainer,
            onError = eudiw_theme_light_onError,
            onErrorContainer = eudiw_theme_light_onErrorContainer,
            background = eudiw_theme_light_background,
            onBackground = eudiw_theme_light_onBackground,
            surface = eudiw_theme_light_surface,
            onSurface = eudiw_theme_light_onSurface,
            surfaceVariant = eudiw_theme_light_surfaceVariant,
            onSurfaceVariant = eudiw_theme_light_onSurfaceVariant,
            outline = eudiw_theme_light_outline,
            inverseOnSurface = eudiw_theme_light_inverseOnSurface,
            inverseSurface = eudiw_theme_light_inverseSurface,
            inversePrimary = eudiw_theme_light_inversePrimary,
            surfaceTint = eudiw_theme_light_surfaceTint,
            outlineVariant = eudiw_theme_light_outlineVariant,
            scrim = eudiw_theme_light_scrim,
            surfaceBright = eudiw_theme_light_surfaceBright,
            surfaceDim = eudiw_theme_light_surfaceDim,
            surfaceContainer = eudiw_theme_light_surfaceContainer,
            surfaceContainerHigh = eudiw_theme_light_surfaceContainerHigh,
            surfaceContainerHighest = eudiw_theme_light_surfaceContainerHighest,
            surfaceContainerLow = eudiw_theme_light_surfaceContainerLow,
            surfaceContainerLowest = eudiw_theme_light_surfaceContainerLowest,
            primaryFixed = eudiw_theme_light_primaryFixed,
            primaryFixedDim = eudiw_theme_light_primaryFixedDim,
            onPrimaryFixed = eudiw_theme_light_onPrimaryFixed,
            onPrimaryFixedVariant = eudiw_theme_light_onPrimaryFixedVariant,
            secondaryFixed = eudiw_theme_light_secondaryFixed,
            secondaryFixedDim = eudiw_theme_light_secondaryFixedDim,
            onSecondaryFixed = eudiw_theme_light_onSecondaryFixed,
            onSecondaryFixedVariant = eudiw_theme_light_onSecondaryFixedVariant,
            tertiaryFixed = eudiw_theme_light_tertiaryFixed,
            tertiaryFixedDim = eudiw_theme_light_tertiaryFixedDim,
            onTertiaryFixed = eudiw_theme_light_onTertiaryFixed,
            onTertiaryFixedVariant = eudiw_theme_light_onTertiaryFixedVariant,
        )

        internal val darkColors = ThemeColorsTemplate(
            primary = eudiw_theme_dark_primary,
            onPrimary = eudiw_theme_dark_onPrimary,
            primaryContainer = eudiw_theme_dark_primaryContainer,
            onPrimaryContainer = eudiw_theme_dark_onPrimaryContainer,
            secondary = eudiw_theme_dark_secondary,
            onSecondary = eudiw_theme_dark_onSecondary,
            secondaryContainer = eudiw_theme_dark_secondaryContainer,
            onSecondaryContainer = eudiw_theme_dark_onSecondaryContainer,
            tertiary = eudiw_theme_dark_tertiary,
            onTertiary = eudiw_theme_dark_onTertiary,
            tertiaryContainer = eudiw_theme_dark_tertiaryContainer,
            onTertiaryContainer = eudiw_theme_dark_onTertiaryContainer,
            error = eudiw_theme_dark_error,
            errorContainer = eudiw_theme_dark_errorContainer,
            onError = eudiw_theme_dark_onError,
            onErrorContainer = eudiw_theme_dark_onErrorContainer,
            background = eudiw_theme_dark_background,
            onBackground = eudiw_theme_dark_onBackground,
            surface = eudiw_theme_dark_surface,
            onSurface = eudiw_theme_dark_onSurface,
            surfaceVariant = eudiw_theme_dark_surfaceVariant,
            onSurfaceVariant = eudiw_theme_dark_onSurfaceVariant,
            outline = eudiw_theme_dark_outline,
            inverseOnSurface = eudiw_theme_dark_inverseOnSurface,
            inverseSurface = eudiw_theme_dark_inverseSurface,
            inversePrimary = eudiw_theme_dark_inversePrimary,
            surfaceTint = eudiw_theme_dark_surfaceTint,
            outlineVariant = eudiw_theme_dark_outlineVariant,
            scrim = eudiw_theme_dark_scrim,
            surfaceBright = eudiw_theme_dark_surfaceBright,
            surfaceDim = eudiw_theme_dark_surfaceDim,
            surfaceContainer = eudiw_theme_dark_surfaceContainer,
            surfaceContainerHigh = eudiw_theme_dark_surfaceContainerHigh,
            surfaceContainerHighest = eudiw_theme_dark_surfaceContainerHighest,
            surfaceContainerLow = eudiw_theme_dark_surfaceContainerLow,
            surfaceContainerLowest = eudiw_theme_dark_surfaceContainerLowest,
            primaryFixed = eudiw_theme_dark_primaryFixed,
            primaryFixedDim = eudiw_theme_dark_primaryFixedDim,
            onPrimaryFixed = eudiw_theme_dark_onPrimaryFixed,
            onPrimaryFixedVariant = eudiw_theme_dark_onPrimaryFixedVariant,
            secondaryFixed = eudiw_theme_dark_secondaryFixed,
            secondaryFixedDim = eudiw_theme_dark_secondaryFixedDim,
            onSecondaryFixed = eudiw_theme_dark_onSecondaryFixed,
            onSecondaryFixedVariant = eudiw_theme_dark_onSecondaryFixedVariant,
            tertiaryFixed = eudiw_theme_dark_tertiaryFixed,
            tertiaryFixedDim = eudiw_theme_dark_tertiaryFixedDim,
            onTertiaryFixed = eudiw_theme_dark_onTertiaryFixed,
            onTertiaryFixedVariant = eudiw_theme_dark_onTertiaryFixedVariant,
        )
    }
}

private fun extra(blue: Long, white: Long, black: Long): Color = Color(
    when (ThemeAppearance.mode) {
        AppearanceMode.BLUE -> blue
        AppearanceMode.WHITE -> white
        AppearanceMode.BLACK -> black
    }
)

// Colores de apoyo según la apariencia: sobre blanco necesitan tonos más oscuros para leerse.
val ColorScheme.success: Color
    @Composable get() = extra(ThemeColors.eudiw_theme_light_success, 0xFF1E7B34, 0xFF7EDC8A)

val ColorScheme.warning: Color
    @Composable get() = extra(ThemeColors.eudiw_theme_light_warning, 0xFFB45309, 0xFFFFB689)

val ColorScheme.pending: Color
    @Composable get() = extra(ThemeColors.eudiw_theme_light_pending, 0xFFB26A00, 0xFFFFC46B)

val ColorScheme.divider: Color
    @Composable get() = extra(ThemeColors.eudiw_theme_light_divider, 0xFFD8E0F0, 0xFF2E2E2E)

val ColorScheme.surfaceAtElevation1: Color
    @Composable get() = extra(ThemeColors.eudiw_theme_light_surfaceContainerLowest, 0xFFF1F4FA, 0xFF121212)

/** Esquema de color de cada apariencia; el azul es el original. */
internal fun ColorScheme.forAppearance(mode: AppearanceMode): ColorScheme = when (mode) {
    AppearanceMode.BLUE -> this
    AppearanceMode.WHITE -> copy(
        primary = Color(0xFF0F1F3D), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE3E8F2), onPrimaryContainer = Color(0xFF0F1F3D),
        secondary = Color(0xFFD5DCEA), onSecondary = Color(0xFF0F1F3D),
        secondaryContainer = Color(0xFFE3E8F2), onSecondaryContainer = Color(0xFF0F1F3D),
        tertiaryContainer = Color(0xFFDCE6FF), onTertiaryContainer = Color(0xFF1F3A73),
        background = Color(0xFFFFFFFF), onBackground = Color(0xFF0F1F3D),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0F1F3D),
        surfaceVariant = Color(0xFFE9EEF7), onSurfaceVariant = Color(0xFF44506B),
        surfaceTint = Color(0xFFFFFFFF),
        inverseSurface = Color(0xFF0F1F3D), inverseOnSurface = Color(0xFFFFFFFF), inversePrimary = Color(0xFFFFFFFF),
        error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410E0B),
        outline = Color(0xFF7B8DB5), outlineVariant = Color(0xFFC9D3E8),
        surfaceBright = Color(0xFFFFFFFF), surfaceDim = Color(0xFFD8DCE6),
        surfaceContainerLowest = Color(0xFFF1F4FA), surfaceContainerLow = Color(0xFFF5F7FB),
        surfaceContainer = Color(0xFFE9EEF7), surfaceContainerHigh = Color(0xFFE1E8F4),
        surfaceContainerHighest = Color(0xFFD8E0F0),
    )

    AppearanceMode.BLACK -> copy(
        onPrimary = Color(0xFF000000),
        background = Color(0xFF000000), surface = Color(0xFF000000), surfaceTint = Color(0xFF000000),
        surfaceVariant = Color(0xFF1C1C1C), onSurfaceVariant = Color(0xFFCFCFCF),
        inverseSurface = Color(0xFFE6E6E6), inverseOnSurface = Color(0xFF000000), inversePrimary = Color(0xFF000000),
        outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFF3A3A3A),
        surfaceBright = Color(0xFF2E2E2E), surfaceDim = Color(0xFF000000),
        surfaceContainerLowest = Color(0xFF121212), surfaceContainerLow = Color(0xFF161616),
        surfaceContainer = Color(0xFF1C1C1C), surfaceContainerHigh = Color(0xFF242424),
        surfaceContainerHighest = Color(0xFF2C2C2C),
    )
}
