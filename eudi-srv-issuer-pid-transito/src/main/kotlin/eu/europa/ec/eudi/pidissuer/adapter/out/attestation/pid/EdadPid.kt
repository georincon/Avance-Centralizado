package eu.europa.ec.eudi.pidissuer.adapter.out.attestation.pid

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate as KLocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Verificación de edad del PID.
 *
 * Umbrales de edad que se certifican en el PID: `age_equal_or_over` ("18", "21")
 * en SD-JWT VC y `age_over_18` / `age_over_21` en mdoc (PID rulebook del ARF).
 */
internal val EdadesVerificadas: List<Int> = listOf(18, 21)

/**
 * Si el titular tiene al menos [edad] años HOY (UTC), el día en que se emite el PID.
 *
 * No se usa `PidMetaData.issuanceDate`: en la Tránsito es la fecha de expedición de
 * la cédula física (`licencia.fechaExpedicion`), no la del PID, y daría una edad desfasada.
 * Quien nació un 29 de febrero cumple el 28 de febrero en los años no bisiestos.
 */
internal fun PidAttributes.tieneAlMenos(
    edad: Int,
    hoy: LocalDate = LocalDate.now(ZoneOffset.UTC),
): Boolean = cumpleEdad(pid.birthDate, edad, hoy.toKotlinLocalDate())

internal fun cumpleEdad(
    nacimiento: KLocalDate,
    edad: Int,
    hoy: KLocalDate,
): Boolean = nacimiento.plus(edad, DateTimeUnit.YEAR) <= hoy
