package eu.europa.ec.onboardingfeature.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private const val FORMAT = "dd-MM-yyyy"

private fun formatter() = SimpleDateFormat(FORMAT, Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
    isLenient = false
}

/** "DD-MM-AAAA" -> milisegundos UTC (medianoche), o null si no es una fecha válida. */
private fun parse(value: String): Long? = runCatching { formatter().parse(value)?.time }.getOrNull()

/**
 * Campo de fecha de nacimiento con calendario. El valor se conserva como "DD-MM-AAAA",
 * el formato que ya consume el cliente del backend. Solo permite fechas pasadas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthDateField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Fecha de nacimiento",
) {
    var open by remember { mutableStateOf(false) }

    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Toca para elegir (DD-MM-AAAA)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // Capa transparente: un TextField de solo lectura no recibe el toque por sí solo.
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 8.dp)
                .clickable { open = true }
        )
    }

    if (open) {
        val todayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val initial = parse(value)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            // Sin fecha previa, el calendario abre en 1990 para no tener que retroceder años.
            initialDisplayedMonthMillis = initial ?: parse("01-01-1990"),
            yearRange = 1900..currentYear,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
                override fun isSelectableYear(year: Int) = year <= currentYear
            }
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { onValueChange(formatter().format(it)) }
                        open = false
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = state)
        }
    }
}
