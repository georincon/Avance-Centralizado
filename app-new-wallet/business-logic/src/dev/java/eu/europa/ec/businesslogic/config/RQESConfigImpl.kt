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

package eu.europa.ec.businesslogic.config

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import eu.europa.ec.businesslogic.BuildConfig
import eu.europa.ec.eudi.rqes.HashAlgorithmOID
import eu.europa.ec.eudi.rqesui.domain.entities.localization.LocalizableKey
import eu.europa.ec.eudi.rqesui.domain.extension.toUriOrEmpty
import eu.europa.ec.eudi.rqesui.infrastructure.config.DocumentRetrievalConfig
import eu.europa.ec.eudi.rqesui.infrastructure.config.EudiRQESUiConfig
import eu.europa.ec.eudi.rqesui.infrastructure.config.data.QtspData
import eu.europa.ec.eudi.rqesui.infrastructure.theme.ThemeManager
import eu.europa.ec.eudi.rqesui.infrastructure.theme.templates.ThemeColorsTemplate
import eu.europa.ec.eudi.rqesui.infrastructure.theme.templates.ThemeTextStyle
import eu.europa.ec.eudi.rqesui.infrastructure.theme.templates.ThemeTypographyTemplate
import eu.europa.ec.eudi.rqesui.infrastructure.theme.templates.structures.ThemeFont
import eu.europa.ec.eudi.rqesui.infrastructure.theme.templates.structures.ThemeFontStyle
import eu.europa.ec.eudi.rqesui.infrastructure.theme.templates.structures.ThemeFontWeight
import eu.europa.ec.eudi.rqesui.infrastructure.theme.templates.structures.ThemeTextAlign
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.ThemeManager as WalletThemeManager
import java.net.URI

class RQESConfigImpl(val context: Context) : EudiRQESUiConfig {

    override val qtsps: List<QtspData>
        get() = listOf(
            // srv-andes-scd: simulador del prestador de firma remota del ecosistema Avance.
            // Su sello de tiempo (RFC 3161) lo llama el telefono directamente.
            QtspData(
                name = "Andes SCD (simulador)",
                endpoint = "${BuildConfig.NW_RQES_URL}/csc/v2".toUriOrEmpty(),
                tsaUrl = "${BuildConfig.NW_RQES_URL}/tsa",
                clientId = BuildConfig.NW_RQES_CLIENT_ID,
                clientSecret = BuildConfig.NW_RQES_CLIENT_SECRET,
                authFlowRedirectionURI = URI.create(BuildConfig.RQES_DEEPLINK),
                hashAlgorithm = HashAlgorithmOID.SHA_256,
            )
        )

    override val printLogs: Boolean get() = BuildConfig.DEBUG

    override val documentRetrievalConfig: DocumentRetrievalConfig
        get() = DocumentRetrievalConfig.X509Certificates(
            context,
            listOf(
                R.raw.pidissuerca02_cz,
                R.raw.pidissuerca02_ee,
                R.raw.pidissuerca02_eu,
                R.raw.pidissuerca02_lu,
                R.raw.pidissuerca02_nl,
                R.raw.pidissuerca02_pt,
                R.raw.pidissuerca02_ut,
                // Raiz del ecosistema Avance: los portales que piden una firma (p. ej. Contratacion) firman su
                // solicitud con un certificado de acceso emitido por la "Civica RP Access CA", que cuelga de ella.
                R.raw.civica_root_ca,
            )
        )

    // Las pantallas del SDK de firma usan la paleta clara de la wallet tambien con el telefono en modo
    // oscuro: la del SDK es negra, y la de la wallet esta pensada para no irse nunca a negro.
    override val themeManager: ThemeManager
        get() {
            val colores = WalletThemeManager.instance.set.lightColors.toRqesColors()
            return ThemeManager.Builder()
                .withLightColors(colores)
                .withDarkColors(colores)
                .withTypography(rqesTypography)
                .build()
        }

    // El SDK elige el idioma por el del telefono (`Locale.getDefault().language`) y solo trae ingles.
    override val translations: Map<String, Map<LocalizableKey, String>>
        get() = mapOf(
            "en" to LocalizableKey.entries.associateWith { it.defaultTranslation() },
            "es" to LocalizableKey.entries.associateWith { it.traduccionEs() },
        )
}

private fun LocalizableKey.traduccionEs(): String = when (this) {
    LocalizableKey.SignDocument -> "Firmar documento"
    LocalizableKey.CancelSignProcessTitle -> "¿Cancelar el proceso de firma?"
    LocalizableKey.CancelSignProcessSubtitle -> "Si cancelas, volverás a la lista de documentos sin firmar tu documento"
    LocalizableKey.CancelSignProcessSecondaryText -> "Cancelar firma"
    LocalizableKey.CancelSignProcessPrimaryText -> "Seguir firmando"
    LocalizableKey.SelectDocumentTitle -> "Documento"
    LocalizableKey.SelectDocumentSubtitle -> "Elige un documento de tu dispositivo para firmarlo electrónicamente."
    LocalizableKey.SigningService -> "Servicio de firma"
    LocalizableKey.SelectSigningService -> "Elige el servicio de firma"
    LocalizableKey.SelectSigningServiceSubtitle -> "El servicio de firma remota permite firmar documentos en línea de forma segura."
    LocalizableKey.SelectServiceTitle -> "Servicios de firma"
    LocalizableKey.SelectServiceSubtitle -> "Elige el servicio de firma que emitirá tu certificado digital"
    LocalizableKey.SelectSigningCertificateTitle -> "Elige el certificado de firma"
    LocalizableKey.SelectCertificateSubtitle -> "El certificado de firma sirve para verificar tu identidad y está vinculado a tu firma electrónica."
    LocalizableKey.SuccessDescription -> "Has firmado tu documento correctamente."
    LocalizableKey.View -> "VER"
    LocalizableKey.Close -> "Cerrar"
    LocalizableKey.Cancel -> "Cancelar"
    LocalizableKey.Continue -> "Continuar"
    LocalizableKey.Done -> "Listo"
    LocalizableKey.Share -> "Compartir"
    LocalizableKey.SharingDocument -> "¿Compartir el documento?"
    LocalizableKey.CloseSharingMessage -> "Si cierras, volverás al inicio sin guardar ni compartir el documento."
    LocalizableKey.GenericErrorButtonRetry -> "REINTENTAR"
    LocalizableKey.GenericErrorMessage -> "¡Ups! Algo salió mal"
    LocalizableKey.GenericServiceErrorMessage -> "El servicio de firma no está disponible. Inténtalo de nuevo más tarde."
    LocalizableKey.GenericErrorDescription -> "Si el problema continúa, comunícate con soporte"
    LocalizableKey.GenericErrorDocumentNotFound -> "No se encontraron datos del documento"
    LocalizableKey.GenericErrorDocumentMultipleNotSupported -> "Por ahora no se admite firmar varios documentos"
    LocalizableKey.GenericErrorQtspNotFound -> "No se encontró el servicio de firma elegido"
    LocalizableKey.GenericErrorCertificatesNotFound -> "No se encontraron certificados"
    LocalizableKey.SigningCertificate -> "Certificado de firma"
    LocalizableKey.SigningCertificates -> "Certificados de firma"
    LocalizableKey.SelectSigningCertificateSubtitle -> "Elige un certificado digital para firmar tu documento"
    LocalizableKey.Certificate -> "Certificado ${LocalizableKey.ARGUMENTS_SEPARATOR}"
}

private fun Color.l(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun ColorScheme.toRqesColors() = ThemeColorsTemplate(
    primary = primary.l(), onPrimary = onPrimary.l(),
    primaryContainer = primaryContainer.l(), onPrimaryContainer = onPrimaryContainer.l(),
    inversePrimary = inversePrimary.l(),
    secondary = secondary.l(), onSecondary = onSecondary.l(),
    secondaryContainer = secondaryContainer.l(), onSecondaryContainer = onSecondaryContainer.l(),
    tertiary = tertiary.l(), onTertiary = onTertiary.l(),
    tertiaryContainer = tertiaryContainer.l(), onTertiaryContainer = onTertiaryContainer.l(),
    background = background.l(), onBackground = onBackground.l(),
    surface = surface.l(), onSurface = onSurface.l(),
    surfaceVariant = surfaceVariant.l(), onSurfaceVariant = onSurfaceVariant.l(),
    surfaceTint = surfaceTint.l(),
    inverseSurface = inverseSurface.l(), inverseOnSurface = inverseOnSurface.l(),
    error = error.l(), onError = onError.l(),
    errorContainer = errorContainer.l(), onErrorContainer = onErrorContainer.l(),
    outline = outline.l(), outlineVariant = outlineVariant.l(), scrim = scrim.l(),
    surfaceBright = surfaceBright.l(), surfaceDim = surfaceDim.l(),
    surfaceContainer = surfaceContainer.l(), surfaceContainerHigh = surfaceContainerHigh.l(),
    surfaceContainerHighest = surfaceContainerHighest.l(), surfaceContainerLow = surfaceContainerLow.l(),
    surfaceContainerLowest = surfaceContainerLowest.l(),
    primaryFixed = primaryFixed.l(), primaryFixedDim = primaryFixedDim.l(),
    onPrimaryFixed = onPrimaryFixed.l(), onPrimaryFixedVariant = onPrimaryFixedVariant.l(),
    secondaryFixed = secondaryFixed.l(), secondaryFixedDim = secondaryFixedDim.l(),
    onSecondaryFixed = onSecondaryFixed.l(), onSecondaryFixedVariant = onSecondaryFixedVariant.l(),
    tertiaryFixed = tertiaryFixed.l(), tertiaryFixedDim = tertiaryFixedDim.l(),
    onTertiaryFixed = onTertiaryFixed.l(), onTertiaryFixedVariant = onTertiaryFixedVariant.l(),
)

private val robotoRegular = ThemeFont(R.font.roboto_regular, ThemeFontWeight.W400, ThemeFontStyle.Normal)
private val robotoMedium = ThemeFont(R.font.roboto_medium, ThemeFontWeight.W500, ThemeFontStyle.Normal)

private fun estilo(size: Long, line: Long, spacing: Float, medium: Boolean = false) = ThemeTextStyle(
    fontFamily = listOf(if (medium) robotoMedium else robotoRegular),
    fontSize = size,
    lineHeight = line,
    letterSpacing = spacing,
    textAlign = ThemeTextAlign.Start,
)

// Escala tipografica estandar de Material 3 con las fuentes Roboto de la wallet.
private val rqesTypography = ThemeTypographyTemplate(
    displayLarge = estilo(57, 64, -0.25f),
    displayMedium = estilo(45, 52, 0f),
    displaySmall = estilo(36, 44, 0f),
    headlineLarge = estilo(32, 40, 0f),
    headlineMedium = estilo(28, 36, 0f),
    headlineSmall = estilo(24, 32, 0f),
    titleLarge = estilo(22, 28, 0f),
    titleMedium = estilo(16, 24, 0.15f, medium = true),
    titleSmall = estilo(14, 20, 0.1f, medium = true),
    bodyLarge = estilo(16, 24, 0.5f),
    bodyMedium = estilo(14, 20, 0.25f),
    bodySmall = estilo(12, 16, 0.4f),
    labelLarge = estilo(14, 20, 0.1f, medium = true),
    labelMedium = estilo(12, 16, 0.5f, medium = true),
    labelSmall = estilo(11, 16, 0.5f, medium = true),
)
