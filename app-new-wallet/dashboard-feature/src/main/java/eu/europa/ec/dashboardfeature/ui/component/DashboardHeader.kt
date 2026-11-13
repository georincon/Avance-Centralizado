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

package eu.europa.ec.dashboardfeature.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import eu.europa.ec.commonfeature.util.DocumentJsonKeys
import eu.europa.ec.commonfeature.util.extractValueFromDocumentOrEmpty
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.onboardingfeature.data.PersonalInfoStore
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapCard
import eu.europa.ec.uilogic.component.wrap.shadowsAtElevation2

/** Quien es el titular de la wallet, tomado de los datos de su cedula (PID principal). */
data class UserIdentity(
    val name: String,
    val initials: String,
)

/** Sin PID emitido todavia no hay datos: se muestra un marcador neutro, nunca el nombre de nadie. */
private val NO_IDENTITY = UserIdentity(name = "", initials = "?")

/** Inicial del primer nombre + inicial del primer apellido (p. ej. "GEOVANI GREGORIO" / "RINCON NAVAS" -> "GR"). */
fun userInitials(givenNames: String, familyNames: String): String {
    fun initial(s: String) = s.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString().orEmpty()
    return (initial(givenNames) + initial(familyNames)).ifBlank { NO_IDENTITY.initials }
}

/**
 * Identidad del titular. La fuente principal son los datos de «Info Personal» (los de su cedula, validados en el onboarding),
 * que no dependen de las credenciales: borrar el PID no cambia el nombre. Solo si no hay Info Personal se mira el PID.
 * Cada usuario ve la suya; no hay nada fijo en la app.
 */
@Composable
fun rememberUserIdentity(): UserIdentity {
    val personalInfo = org.koin.compose.koinInject<PersonalInfoStore>()
    val documents = org.koin.compose.koinInject<WalletCoreDocumentsController>()
    val identity by produceState(initialValue = NO_IDENTITY, personalInfo, documents) {
        value = runCatching {
            personalInfo.load()?.takeIf { it.firstNames.isNotBlank() || it.lastNames.isNotBlank() }?.let {
                UserIdentity(it.firstNames.trim(), userInitials(it.firstNames, it.lastNames))
            }
        }.getOrNull() ?: runCatching {
            documents.getMainPidDocument()?.let { pid ->
                val given = extractValueFromDocumentOrEmpty(pid, DocumentJsonKeys.FIRST_NAME)
                val family = extractValueFromDocumentOrEmpty(pid, DocumentJsonKeys.LAST_NAME)
                if (given.isBlank() && family.isBlank()) null else UserIdentity(given.trim(), userInitials(given, family))
            }
        }.getOrNull() ?: NO_IDENTITY
    }
    return identity
}

/**
 * White, full-width app-bar-style container used to group the top-of-screen controls (menu
 * icon, profile badge, search bar, view toggle, etc.), shared by Home, Credenciales and
 * Conexiones. Spans edge-to-edge (square top corners, rounded bottom corners only) with a
 * pronounced shadow along its bottom edge, separating it from the scrollable content below.
 */
private val HeaderCardBottomCornerRadius = 20.dp

@Composable
fun HeaderCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    WrapCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            bottomStart = HeaderCardBottomCornerRadius,
            bottomEnd = HeaderCardBottomCornerRadius,
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        shadows = shadowsAtElevation2,
    ) {
        content()
    }
}

/**
 * Circle avatar showing the user's initials (real PID given name initial, or the fallback
 * initials when no PID has been issued yet).
 */
@Composable
fun UserAvatar(
    initials: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Avatar + nombre del titular, para la cabecera de Home. El nombre llega del estado de la pantalla (PID principal) y
 * las iniciales se calculan de los mismos datos de la cedula; sin PID no se muestra ningun nombre.
 */
@Composable
fun UserBadge(
    userFirstName: String,
    modifier: Modifier = Modifier,
) {
    val identity = rememberUserIdentity()
    // Primero Info Personal; el nombre del PID (estado de Home) solo si no hay datos personales.
    val displayName = identity.name.ifBlank { userFirstName }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(initials = identity.initials)
        if (displayName.isNotBlank()) {
            Text(
                text = displayName,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
