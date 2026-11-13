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

package eu.europa.ec.onboardingfeature.ui.otherdata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.europa.ec.onboardingfeature.data.OtherData
import eu.europa.ec.onboardingfeature.data.OtherDataStore
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ContentTitle
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import org.koin.compose.koinInject

/**
 * "Otros datos": celular y correo que la persona dio en el onboarding (solo lectura).
 * Si el onboarding es anterior a que se guardaran, salen vacíos hasta que se repita.
 */
@Composable
fun OtherDataScreen(onBack: () -> Unit) {
    val store = koinInject<OtherDataStore>()
    val loaded by produceState(initialValue = Loaded(false, null)) {
        value = Loaded(true, store.load())
    }
    ContentScreen(
        navigatableAction = ScreenNavigateAction.BACKABLE,
        isLoading = !loaded.done,
        onBack = onBack,
    ) { padding -> Body(loaded.data, padding) }
}

private data class Loaded(val done: Boolean, val data: OtherData?)

@Composable
private fun Body(data: OtherData?, padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        ContentTitle(
            modifier = Modifier.fillMaxWidth(),
            title = "Otros datos",
            subtitle = "Datos de contacto que diste al validar tu identidad. Son solo tuyos: no se comparten al presentar un documento.",
        )
        Column(modifier = Modifier.padding(top = 16.dp)) {
            InfoRow("Celular", data?.phone?.phoneDisplay())
            InfoRow("Correo electrónico", data?.email)
        }
        if (data?.phone == null && data?.email == null) {
            Text(
                "Aparecerán aquí cuando repitas la validación de tu identidad.",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
    HorizontalDivider()
}

/** 3001234567 -> 300 123 4567 (si no son 10 dígitos se deja como está). */
private fun String.phoneDisplay(): String =
    if (length == 10 && all { it.isDigit() }) "${substring(0, 3)} ${substring(3, 6)} ${substring(6)}" else this
